package com.huarenzaimeng.api.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import com.huarenzaimeng.api.DataMigrationOracleVerifier;

public final class ReleaseFlywayMigrationRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ReleaseFlywayMigrationRunner.class);
    private final Flyway flyway;
    private final ReleaseMigrationState state;
    private final String expectedDatabaseName;
    private final String expectedServerUuid;

    ReleaseFlywayMigrationRunner(Flyway flyway, ReleaseMigrationState state,
                                  String expectedDatabaseName, String expectedServerUuid) {
        this.flyway = flyway;
        this.state = state;
        if (!DataMigrationOracleVerifier.DEPLOYMENT_DATABASE.equals(expectedDatabaseName)) {
            throw new IllegalArgumentException("EXPECTED_DATABASE_NAME_INVALID");
        }
        this.expectedDatabaseName = expectedDatabaseName;
        if (expectedServerUuid == null || expectedServerUuid.isBlank()) {
            throw new IllegalArgumentException("EXPECTED_SERVER_UUID_REQUIRED");
        }
        this.expectedServerUuid = expectedServerUuid;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            LOG.info("RELEASE_MIGRATE_STARTED");
            try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
                if (DataMigrationOracleVerifier.verify(connection, expectedDatabaseName, expectedServerUuid) != DataMigrationOracleVerifier.State.PRE_V10) {
                    throw new IllegalStateException("MIGRATION_PRE_ORACLE_MISMATCH");
                }
            }
            flyway.migrate();
            try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
                if (DataMigrationOracleVerifier.verify(connection, expectedDatabaseName, expectedServerUuid) != DataMigrationOracleVerifier.State.POST_V12) {
                    throw new IllegalStateException("MIGRATION_POST_ORACLE_MISMATCH");
                }
            }
            state.ready();
            LOG.info("RELEASE_MIGRATE_READY");
        } catch (Exception failure) {
            state.failed();
            LOG.error("RELEASE_MIGRATE_FAILED type={}", failure.getClass().getSimpleName());
            throw failure;
        }
    }
}
