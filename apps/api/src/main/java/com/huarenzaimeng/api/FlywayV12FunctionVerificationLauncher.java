package com.huarenzaimeng.api;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Temporary, non-web entry used only to verify V1-V12 against one isolated database. */
public final class FlywayV12FunctionVerificationLauncher {
    static final String ENABLED_PROPERTY = "hz.data-integration.flyway-v12-function-verification-enabled";
    static final String EXPECTED_DATABASE = "hz_flyway_v12_20260814";
    private static final String[] SCRIPTS = {
            "V1__create_core_transaction_tables.sql",
            "V2__add_subject_scoped_commands_versions_and_worker_tables.sql",
            "V3__add_self_operated_directory_content.sql",
            "V4__add_versioned_operator_and_preset_catalog.sql",
            "V5__tighten_order_command_idempotency_scope.sql",
            "V6__add_local_synthetic_payment_intent.sql",
            "V7__add_order_detail_read_projection.sql",
            "V8__add_admin_authentication.sql",
            "V9__add_buyer_authentication.sql",
            "V10__add_code2session_attempt_and_idle_expiry.sql",
            "V11__add_state_advance_authority_columns.sql",
            "V12__create_state_advance_authority_fact.sql"
    };

    private FlywayV12FunctionVerificationLauncher() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) fail("FLYWAY_FUNCTION_ARGUMENTS_FORBIDDEN");
        try (var context = new SpringApplicationBuilder(IsolatedFlywayConfiguration.class)
                .profiles("release-mysql")
                .web(WebApplicationType.NONE)
                .run()) {
            String enabled = context.getEnvironment().getProperty(ENABLED_PROPERTY, "false");
            execute(context.getEnvironment().getActiveProfiles(), enabled,
                    new FlywayRuntime(context.getBean(Flyway.class)));
        }
    }

    static void execute(String[] activeProfiles, String enabled, Runtime runtime) throws Exception {
        if (!Set.of(activeProfiles).equals(Set.of("release-mysql"))) fail("FLYWAY_FUNCTION_PROFILE_INVALID");
        if (!"true".equals(enabled)) fail("FLYWAY_FUNCTION_DISABLED");

        DataMigrationOracleVerifier.State initial = runtime.inspect();
        if (initial == DataMigrationOracleVerifier.State.POST_V12) {
            runtime.requireExactInventory(initial);
            return;
        }
        if (initial != DataMigrationOracleVerifier.State.PRE_V10) fail("FLYWAY_FUNCTION_START_STATE_INVALID");

        runtime.requireExactInventory(initial);
        runtime.migrateOnce();
        DataMigrationOracleVerifier.State terminal = runtime.inspect();
        if (terminal != DataMigrationOracleVerifier.State.POST_V12) fail("FLYWAY_FUNCTION_POST_STATE_INVALID");
        runtime.requireExactInventory(terminal);
    }

    interface Runtime {
        DataMigrationOracleVerifier.State inspect() throws Exception;
        void requireExactInventory(DataMigrationOracleVerifier.State state);
        void migrateOnce();
    }

    static final class FlywayRuntime implements Runtime {
        private final Flyway flyway;
        private final AtomicBoolean migrated = new AtomicBoolean();

        FlywayRuntime(Flyway flyway) {
            this.flyway = flyway;
            if (flyway.getConfiguration().getConnectRetries() != 0)
                fail("FLYWAY_FUNCTION_CONNECT_RETRIES_INVALID");
        }

        @Override public DataMigrationOracleVerifier.State inspect() throws Exception {
            DataSource source = flyway.getConfiguration().getDataSource();
            try (Connection connection = source.getConnection()) {
                requireExpectedDatabase(connection);
                return DataMigrationOracleVerifier.classify(DataMigrationOracleVerifier.collect(connection));
            }
        }

        @Override public void requireExactInventory(DataMigrationOracleVerifier.State state) {
            MigrationInfo[] all = flyway.info().all();
            if (all.length != SCRIPTS.length) fail("FLYWAY_FUNCTION_INVENTORY_INVALID");
            Set<String> versions = new HashSet<>();
            for (MigrationInfo info : all) {
                if (info.getVersion() == null) fail("FLYWAY_FUNCTION_INVENTORY_INVALID");
                String version = info.getVersion().getVersion();
                int number;
                try { number = Integer.parseInt(version); }
                catch (NumberFormatException invalid) { fail("FLYWAY_FUNCTION_INVENTORY_INVALID"); return; }
                if (number < 1 || number > SCRIPTS.length || !versions.add(version)
                        || !SCRIPTS[number - 1].equals(info.getScript()))
                    fail("FLYWAY_FUNCTION_INVENTORY_INVALID");
                MigrationState expected = state == DataMigrationOracleVerifier.State.POST_V12 || number <= 10
                        ? MigrationState.SUCCESS : MigrationState.PENDING;
                if (info.getState() != expected) fail("FLYWAY_FUNCTION_INVENTORY_INVALID");
            }
        }

        @Override public void migrateOnce() {
            if (!migrated.compareAndSet(false, true)) fail("FLYWAY_FUNCTION_MULTIPLE_MIGRATE_FORBIDDEN");
            flyway.migrate();
        }
    }

    static void requireExpectedDatabase(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next() || !EXPECTED_DATABASE.equals(result.getString(1)) || result.next())
                fail("FLYWAY_FUNCTION_DATABASE_INVALID");
        }
    }

    private static void fail(String code) { throw new IllegalStateException(code); }

    @Configuration(proxyBeanMethods = false)
    @Profile("release-mysql")
    static class IsolatedFlywayConfiguration {
        @Bean
        Flyway isolatedFlyway(
                @Value("${spring.flyway.url}") String url,
                @Value("${spring.flyway.user}") String user,
                @Value("${spring.flyway.password}") String password,
                @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
                @Value("${spring.flyway.connect-retries:0}") int connectRetries,
                @Value("${spring.flyway.validate-on-migrate:true}") boolean validateOnMigrate,
                @Value("${spring.flyway.baseline-on-migrate:false}") boolean baselineOnMigrate) {
            if (connectRetries != 0) fail("FLYWAY_FUNCTION_CONNECT_RETRIES_INVALID");
            return Flyway.configure()
                    .dataSource(url, user, password)
                    .locations(Arrays.stream(locations.split(",")).map(String::trim).toArray(String[]::new))
                    .connectRetries(0)
                    .validateOnMigrate(validateOnMigrate)
                    .baselineOnMigrate(baselineOnMigrate)
                    .load();
        }
    }
}
