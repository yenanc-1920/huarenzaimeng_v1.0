package com.huarenzaimeng.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
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

        FunctionState initial = runtime.inspect();
        if (initial == FunctionState.POST_V12) {
            runtime.requireExactInventory(initial);
            return;
        }
        if (initial != FunctionState.PRE_V10) fail("FLYWAY_FUNCTION_START_STATE_INVALID");

        runtime.requireExactInventory(initial);
        runtime.migrateOnce();
        FunctionState terminal = runtime.inspect();
        if (terminal != FunctionState.POST_V12) fail("FLYWAY_FUNCTION_POST_STATE_INVALID");
        runtime.requireExactInventory(terminal);
    }

    enum FunctionState { PRE_V10, POST_V12, INVALID }

    record StructureSnapshot(
            int v11ColumnCount,
            int v11ExactColumnCount,
            int factTableCount,
            int factExactTableCount,
            int factColumnCount,
            int factExactColumnCount,
            int factPrimaryKeyColumnCount,
            int factExactPrimaryKeyColumnCount,
            int factUniqueKeyColumnCount,
            int factExactUniqueKeyColumnCount,
            int factForeignKeyColumnCount,
            int factExactForeignKeyColumnCount) {}

    static FunctionState classify(StructureSnapshot snapshot) {
        if (snapshot.v11ColumnCount() == 0
                && snapshot.factTableCount() == 0
                && snapshot.factColumnCount() == 0
                && snapshot.factPrimaryKeyColumnCount() == 0
                && snapshot.factUniqueKeyColumnCount() == 0
                && snapshot.factForeignKeyColumnCount() == 0) {
            return FunctionState.PRE_V10;
        }
        if (snapshot.v11ColumnCount() == 3 && snapshot.v11ExactColumnCount() == 3
                && snapshot.factTableCount() == 1 && snapshot.factExactTableCount() == 1
                && snapshot.factColumnCount() == 10 && snapshot.factExactColumnCount() == 10
                && snapshot.factPrimaryKeyColumnCount() == 1 && snapshot.factExactPrimaryKeyColumnCount() == 1
                && snapshot.factUniqueKeyColumnCount() == 2 && snapshot.factExactUniqueKeyColumnCount() == 2
                && snapshot.factForeignKeyColumnCount() == 1 && snapshot.factExactForeignKeyColumnCount() == 1) {
            return FunctionState.POST_V12;
        }
        return FunctionState.INVALID;
    }

    interface Runtime {
        FunctionState inspect() throws Exception;
        void requireExactInventory(FunctionState state);
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

        @Override public FunctionState inspect() throws Exception {
            DataSource source = flyway.getConfiguration().getDataSource();
            try (Connection connection = source.getConnection()) {
                requireExpectedDatabase(connection);
                return classify(collectStructure(connection));
            }
        }

        @Override public void requireExactInventory(FunctionState state) {
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
                MigrationState expected = state == FunctionState.POST_V12 || number <= 10
                        ? MigrationState.SUCCESS : MigrationState.PENDING;
                if (info.getState() != expected) fail("FLYWAY_FUNCTION_INVENTORY_INVALID");
            }
        }

        @Override public void migrateOnce() {
            if (!migrated.compareAndSet(false, true)) fail("FLYWAY_FUNCTION_MULTIPLE_MIGRATE_FORBIDDEN");
            flyway.migrate();
        }
    }

    static StructureSnapshot collectStructure(Connection connection) throws Exception {
        return new StructureSnapshot(
                count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'hz_order' AND column_name IN ('environment','evidence_level','authority_state')"),
                count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'hz_order' AND ((column_name='environment' AND ordinal_position=14 AND column_type='varchar(32)' AND is_nullable='NO' AND column_default='LOCAL_SYNTHETIC' AND character_set_name='ascii' AND collation_name='ascii_bin') OR (column_name='evidence_level' AND ordinal_position=15 AND column_type='varchar(8)' AND is_nullable='NO' AND column_default='L1' AND character_set_name='ascii' AND collation_name='ascii_bin') OR (column_name='authority_state' AND ordinal_position=16 AND column_type='varchar(24)' AND is_nullable='NO' AND column_default='NON_PRODUCTION' AND character_set_name='ascii' AND collation_name='ascii_bin'))"),
                count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'hz_state_advance_authority_fact'"),
                count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'hz_state_advance_authority_fact' AND table_type='BASE TABLE' AND engine='InnoDB' AND table_collation='utf8mb4_unicode_ci'"),
                count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'hz_state_advance_authority_fact'"),
                count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'hz_state_advance_authority_fact' AND ((column_name='authority_fact_id' AND ordinal_position=1 AND column_type='bigint(20) unsigned' AND is_nullable='NO' AND column_default IS NULL AND character_set_name IS NULL AND collation_name IS NULL AND extra='auto_increment') OR (column_name='aggregate_ref' AND ordinal_position=2 AND column_type='varchar(64)' AND is_nullable='NO' AND column_default IS NULL AND character_set_name='ascii' AND collation_name='ascii_bin' AND extra='') OR (column_name='command_id' AND ordinal_position=3 AND column_type='varchar(96)' AND is_nullable='NO' AND column_default IS NULL AND character_set_name='ascii' AND collation_name='ascii_bin' AND extra='') OR (column_name='environment' AND ordinal_position=4 AND column_type='varchar(32)' AND is_nullable='NO' AND column_default IS NULL AND character_set_name='ascii' AND collation_name='ascii_bin' AND extra='') OR (column_name='evidence_level' AND ordinal_position=5 AND column_type='varchar(8)' AND is_nullable='NO' AND column_default IS NULL AND character_set_name='ascii' AND collation_name='ascii_bin' AND extra='') OR (column_name='authorization_ref' AND ordinal_position=6 AND column_type='varchar(96)' AND is_nullable='NO' AND column_default IS NULL AND character_set_name='ascii' AND collation_name='ascii_bin' AND extra='') OR (column_name='target_transition' AND ordinal_position=7 AND column_type='varchar(64)' AND is_nullable='NO' AND column_default IS NULL AND character_set_name='ascii' AND collation_name='ascii_bin' AND extra='') OR (column_name='evidence_ref' AND ordinal_position=8 AND column_type='varchar(128)' AND is_nullable='NO' AND column_default IS NULL AND character_set_name='ascii' AND collation_name='ascii_bin' AND extra='') OR (column_name='authority_state' AND ordinal_position=9 AND column_type='varchar(24)' AND is_nullable='NO' AND column_default IS NULL AND character_set_name='ascii' AND collation_name='ascii_bin' AND extra='') OR (column_name='created_at' AND ordinal_position=10 AND column_type='datetime(3)' AND is_nullable='NO' AND column_default IS NULL AND character_set_name IS NULL AND collation_name IS NULL AND extra=''))"),
                count(connection, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = ? AND table_name='hz_state_advance_authority_fact' AND constraint_name='PRIMARY'"),
                count(connection, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = ? AND table_name='hz_state_advance_authority_fact' AND constraint_name='PRIMARY' AND column_name='authority_fact_id' AND ordinal_position=1"),
                count(connection, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = ? AND table_name='hz_state_advance_authority_fact' AND constraint_name='uq_state_advance_authority'"),
                count(connection, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = ? AND table_name='hz_state_advance_authority_fact' AND constraint_name='uq_state_advance_authority' AND ((column_name='aggregate_ref' AND ordinal_position=1) OR (column_name='command_id' AND ordinal_position=2))"),
                count(connection, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = ? AND table_name='hz_state_advance_authority_fact' AND constraint_name='fk_state_advance_authority_order'"),
                count(connection, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = ? AND table_name='hz_state_advance_authority_fact' AND constraint_name='fk_state_advance_authority_order' AND column_name='aggregate_ref' AND ordinal_position=1 AND referenced_table_schema=table_schema AND referenced_table_name='hz_order' AND referenced_column_name='order_ref'"));
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, EXPECTED_DATABASE);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) fail("FLYWAY_FUNCTION_STRUCTURE_READ_INVALID");
                int value = result.getInt(1);
                if (result.wasNull() || value < 0 || result.next()) fail("FLYWAY_FUNCTION_STRUCTURE_READ_INVALID");
                return value;
            }
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
