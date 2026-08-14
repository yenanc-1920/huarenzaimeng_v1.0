package com.huarenzaimeng.api;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlywayV12FunctionVerificationLauncherTest {
    @Test void exactPreMigratesOnceAndRequiresPost() throws Exception {
        FakeRuntime runtime = runtime(DataMigrationOracleVerifier.State.PRE_V10,
                DataMigrationOracleVerifier.State.POST_V12);
        FlywayV12FunctionVerificationLauncher.execute(new String[]{"release-mysql"}, "true", runtime);
        assertThat(runtime.migrations).hasValue(1);
        assertThat(runtime.inventories).containsExactly(DataMigrationOracleVerifier.State.PRE_V10,
                DataMigrationOracleVerifier.State.POST_V12);
    }

    @Test void exactPostReturnsWithoutMigration() throws Exception {
        FakeRuntime runtime = runtime(DataMigrationOracleVerifier.State.POST_V12);
        FlywayV12FunctionVerificationLauncher.execute(new String[]{"release-mysql"}, "true", runtime);
        assertThat(runtime.migrations).hasValue(0);
        assertThat(runtime.inventories).containsExactly(DataMigrationOracleVerifier.State.POST_V12);
    }

    @Test void midFailsBeforeMigration() {
        assertRejected(DataMigrationOracleVerifier.State.MID_V11, "FLYWAY_FUNCTION_START_STATE_INVALID");
    }

    @Test void partialFailsBeforeMigration() {
        assertRejected(DataMigrationOracleVerifier.State.NO_GO_PARTIAL_OR_DRIFT,
                "FLYWAY_FUNCTION_START_STATE_INVALID");
    }

    @Test void unavailableFailsBeforeMigration() {
        assertRejected(DataMigrationOracleVerifier.State.NO_GO_ORACLE_UNAVAILABLE,
                "FLYWAY_FUNCTION_START_STATE_INVALID");
    }

    @Test void disabledFlagFailsBeforeInspect() {
        FakeRuntime runtime = runtime(DataMigrationOracleVerifier.State.PRE_V10);
        assertThatThrownBy(() -> FlywayV12FunctionVerificationLauncher.execute(
                new String[]{"release-mysql"}, "false", runtime)).hasMessage("FLYWAY_FUNCTION_DISABLED");
        assertThat(runtime.inspections).hasValue(0);
    }

    @Test void extraProfileFailsBeforeInspect() {
        FakeRuntime runtime = runtime(DataMigrationOracleVerifier.State.PRE_V10);
        assertThatThrownBy(() -> FlywayV12FunctionVerificationLauncher.execute(
                new String[]{"release-mysql", "mock"}, "true", runtime))
                .hasMessage("FLYWAY_FUNCTION_PROFILE_INVALID");
        assertThat(runtime.inspections).hasValue(0);
    }

    @Test void failedMigrationIsNotRetried() {
        FakeRuntime runtime = runtime(DataMigrationOracleVerifier.State.PRE_V10);
        runtime.failMigration = true;
        assertThatThrownBy(() -> FlywayV12FunctionVerificationLauncher.execute(
                new String[]{"release-mysql"}, "true", runtime)).hasMessage("SYNTHETIC_MIGRATION_FAILURE");
        assertThat(runtime.migrations).hasValue(1);
    }

    @Test void nonPostResultAfterMigrationFailsClosed() {
        FakeRuntime runtime = runtime(DataMigrationOracleVerifier.State.PRE_V10,
                DataMigrationOracleVerifier.State.MID_V11);
        assertThatThrownBy(() -> FlywayV12FunctionVerificationLauncher.execute(
                new String[]{"release-mysql"}, "true", runtime))
                .hasMessage("FLYWAY_FUNCTION_POST_STATE_INVALID");
        assertThat(runtime.migrations).hasValue(1);
    }

    @Test void databaseIdentityMustBeExactAndSingleRow() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT DATABASE()")).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString(1)).thenReturn("another_database");
        assertThatThrownBy(() -> FlywayV12FunctionVerificationLauncher.requireExpectedDatabase(connection))
                .hasMessage("FLYWAY_FUNCTION_DATABASE_INVALID");
    }

    @Test void exactDatabaseIdentityPasses() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT DATABASE()")).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString(1)).thenReturn(FlywayV12FunctionVerificationLauncher.EXPECTED_DATABASE);
        FlywayV12FunctionVerificationLauncher.requireExpectedDatabase(connection);
    }

    private static void assertRejected(DataMigrationOracleVerifier.State state, String code) {
        FakeRuntime runtime = runtime(state);
        assertThatThrownBy(() -> FlywayV12FunctionVerificationLauncher.execute(
                new String[]{"release-mysql"}, "true", runtime)).hasMessage(code);
        assertThat(runtime.migrations).hasValue(0);
    }

    private static FakeRuntime runtime(DataMigrationOracleVerifier.State... states) {
        return new FakeRuntime(new ArrayDeque<>(java.util.List.of(states)));
    }

    private static final class FakeRuntime implements FlywayV12FunctionVerificationLauncher.Runtime {
        private final Deque<DataMigrationOracleVerifier.State> states;
        private final AtomicInteger inspections = new AtomicInteger();
        private final AtomicInteger migrations = new AtomicInteger();
        private final java.util.List<DataMigrationOracleVerifier.State> inventories = new java.util.ArrayList<>();
        private boolean failMigration;

        private FakeRuntime(Deque<DataMigrationOracleVerifier.State> states) { this.states = states; }

        @Override public DataMigrationOracleVerifier.State inspect() {
            inspections.incrementAndGet();
            return states.removeFirst();
        }

        @Override public void requireExactInventory(DataMigrationOracleVerifier.State state) {
            inventories.add(state);
        }

        @Override public void migrateOnce() {
            migrations.incrementAndGet();
            if (failMigration) throw new IllegalStateException("SYNTHETIC_MIGRATION_FAILURE");
        }
    }
}
