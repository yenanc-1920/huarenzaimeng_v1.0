package com.huarenzaimeng.api.config;

import com.huarenzaimeng.api.DataMigrationOracleVerifier;
import java.sql.Connection;
import org.flywaydb.core.Flyway;

/** Explicit controlled command; deliberately not an ApplicationRunner or Spring bean. */
public final class ReleaseFlywayMigrationRunner {
    interface StageExecutor {
        Connection openConnection() throws Exception;
        void migrateTo(String target) throws Exception;
    }

    static StageExecutor flywayStages(Flyway base) {
        return new StageExecutor() {
            @Override public Connection openConnection() throws Exception {
                return base.getConfiguration().getDataSource().getConnection();
            }
            @Override public void migrateTo(String target) {
                Flyway.configure().configuration(base.getConfiguration()).target(target).load().migrate();
            }
        };
    }

    private final StageExecutor stages;
    private final ReleaseMigrationState state;
    private final ReleaseMigrationAuthorizationStore authorizations;
    private final ReleaseMigrationRuntimeIdentityProvider identities;

    ReleaseFlywayMigrationRunner(StageExecutor stages, ReleaseMigrationState state,
                                  ReleaseMigrationAuthorizationStore authorizations,
                                  ReleaseMigrationRuntimeIdentityProvider identities) {
        this.stages = stages;
        this.state = state;
        this.authorizations = authorizations;
        this.identities = identities;
    }

    public void execute() throws Exception {
        ReleaseMigrationAuthorizationStore.ConsumedAuthorization consumed = null;
        try {
            var authorization = authorizations.preview();
            var identity = identities.current();
            authorizations.validate(authorization, identity);
            var preflight = currentOracle(identity);
            var expectedStart = switch(authorization.startState()) {
                case PRE_V10 -> DataMigrationOracleVerifier.State.PRE_V10;
                case MID_V11 -> DataMigrationOracleVerifier.State.MID_V11;
                case POST_V12 -> DataMigrationOracleVerifier.State.POST_V12;
                case POST_V13 -> DataMigrationOracleVerifier.State.POST_V13;
            };
            if (preflight != expectedStart) throw new IllegalStateException("MIGRATION_START_ORACLE_MISMATCH");

            // Durable single-use consumption is the final action before the first DDL.
            consumed = authorizations.consume(authorization, identity);

            if (authorization.startState() == ReleaseMigrationAuthorization.StartState.PRE_V10) {
                stages.migrateTo("11");
                requireOracle(DataMigrationOracleVerifier.State.MID_V11, "MIGRATION_AFTER_V11_ORACLE_MISMATCH", identity);
            }

            if (authorization.startState() == ReleaseMigrationAuthorization.StartState.PRE_V10
                    || authorization.startState() == ReleaseMigrationAuthorization.StartState.MID_V11) {
                stages.migrateTo("12");
                requireOracle(DataMigrationOracleVerifier.State.POST_V12, "MIGRATION_AFTER_V12_ORACLE_MISMATCH", identity);
            }

            boolean v14Terminal=authorization.allowedTarget()==ReleaseMigrationAuthorization.AllowedTarget.V14_VIA_V13
                    || authorization.allowedTarget()==ReleaseMigrationAuthorization.AllowedTarget.V14_ONLY;
            if (authorization.allowedTarget()==ReleaseMigrationAuthorization.AllowedTarget.V14_VIA_V13) {
                stages.migrateTo("13");
                requireOracle(DataMigrationOracleVerifier.State.POST_V13,"MIGRATION_AFTER_V13_ORACLE_MISMATCH",identity);
            }
            if (v14Terminal) {
                stages.migrateTo("14");
                requireOracle(DataMigrationOracleVerifier.State.POST_V14,"MIGRATION_AFTER_V14_ORACLE_MISMATCH",identity);
            }

            var terminalIdentity = identities.current();
            authorizations.validate(authorization, terminalIdentity);
            consumed.seal("SUCCEEDED");
            if(v14Terminal){consumed.publishReady(terminalIdentity);state.ready();}
        } catch (Exception failure) {
            state.failed();
            if (consumed != null) consumed.seal("FAILED");
            throw failure;
        }
    }

    private void requireOracle(DataMigrationOracleVerifier.State expected, String code,
                               ReleaseMigrationAuthorization.ExecutionIdentity identity) throws Exception {
        if (currentOracle(identity) != expected) throw new IllegalStateException(code);
    }

    private DataMigrationOracleVerifier.State currentOracle(
            ReleaseMigrationAuthorization.ExecutionIdentity identity) throws Exception {
        try (Connection connection = stages.openConnection()) {
            return DataMigrationOracleVerifier.verify(connection, identity.databaseName(), identity.expectedServerUuid());
        }
    }
}
