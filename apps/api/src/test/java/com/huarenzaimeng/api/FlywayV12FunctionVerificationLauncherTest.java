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
        FakeRuntime runtime = runtime(FlywayV12FunctionVerificationLauncher.FunctionState.PRE_V10,
                FlywayV12FunctionVerificationLauncher.FunctionState.POST_V12);
        FlywayV12FunctionVerificationLauncher.execute(new String[]{"release-mysql"}, "true", runtime);
        assertThat(runtime.migrations).hasValue(1);
        assertThat(runtime.inventories).containsExactly(FlywayV12FunctionVerificationLauncher.FunctionState.PRE_V10,
                FlywayV12FunctionVerificationLauncher.FunctionState.POST_V12);
    }

    @Test void exactPostReturnsWithoutMigration() throws Exception {
        FakeRuntime runtime = runtime(FlywayV12FunctionVerificationLauncher.FunctionState.POST_V12);
        FlywayV12FunctionVerificationLauncher.execute(new String[]{"release-mysql"}, "true", runtime);
        assertThat(runtime.migrations).hasValue(0);
        assertThat(runtime.inventories).containsExactly(FlywayV12FunctionVerificationLauncher.FunctionState.POST_V12);
    }

    @Test void midFailsBeforeMigration() {
        assertRejected(FlywayV12FunctionVerificationLauncher.FunctionState.INVALID,
                "FLYWAY_FUNCTION_START_STATE_INVALID");
    }

    @Test void partialFailsBeforeMigration() {
        assertRejected(FlywayV12FunctionVerificationLauncher.FunctionState.INVALID,
                "FLYWAY_FUNCTION_START_STATE_INVALID");
    }

    @Test void disabledFlagFailsBeforeInspect() {
        FakeRuntime runtime = runtime(FlywayV12FunctionVerificationLauncher.FunctionState.PRE_V10);
        assertThatThrownBy(() -> FlywayV12FunctionVerificationLauncher.execute(
                new String[]{"release-mysql"}, "false", runtime)).hasMessage("FLYWAY_FUNCTION_DISABLED");
        assertThat(runtime.inspections).hasValue(0);
    }

    @Test void extraProfileFailsBeforeInspect() {
        FakeRuntime runtime = runtime(FlywayV12FunctionVerificationLauncher.FunctionState.PRE_V10);
        assertThatThrownBy(() -> FlywayV12FunctionVerificationLauncher.execute(
                new String[]{"release-mysql", "mock"}, "true", runtime))
                .hasMessage("FLYWAY_FUNCTION_PROFILE_INVALID");
        assertThat(runtime.inspections).hasValue(0);
    }

    @Test void failedMigrationIsNotRetried() {
        FakeRuntime runtime = runtime(FlywayV12FunctionVerificationLauncher.FunctionState.PRE_V10);
        runtime.failMigration = true;
        assertThatThrownBy(() -> FlywayV12FunctionVerificationLauncher.execute(
                new String[]{"release-mysql"}, "true", runtime)).hasMessage("SYNTHETIC_MIGRATION_FAILURE");
        assertThat(runtime.migrations).hasValue(1);
    }

    @Test void nonPostResultAfterMigrationFailsClosed() {
        FakeRuntime runtime = runtime(FlywayV12FunctionVerificationLauncher.FunctionState.PRE_V10,
                FlywayV12FunctionVerificationLauncher.FunctionState.INVALID);
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

    @Test void minimalPreRequiresAllV11AndV12ObjectsAbsent() {
        var snapshot = new FlywayV12FunctionVerificationLauncher.StructureSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(FlywayV12FunctionVerificationLauncher.classify(snapshot))
                .isEqualTo(FlywayV12FunctionVerificationLauncher.FunctionState.PRE_V10);
    }

    @Test void minimalPostRequiresExactAffectedObjectsAndKeys() {
        var snapshot = new FlywayV12FunctionVerificationLauncher.StructureSnapshot(
                3, 3, 1, 1, 10, 10, 1, 1, 2, 2, 1, 1);
        assertThat(FlywayV12FunctionVerificationLauncher.classify(snapshot))
                .isEqualTo(FlywayV12FunctionVerificationLauncher.FunctionState.POST_V12);
    }

    @Test void partialV11AndPrematureV12AreRejected() {
        var partialV11 = new FlywayV12FunctionVerificationLauncher.StructureSnapshot(
                1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var prematureV12 = new FlywayV12FunctionVerificationLauncher.StructureSnapshot(
                0, 0, 1, 1, 10, 10, 1, 1, 2, 2, 1, 1);
        assertThat(FlywayV12FunctionVerificationLauncher.classify(partialV11))
                .isEqualTo(FlywayV12FunctionVerificationLauncher.FunctionState.INVALID);
        assertThat(FlywayV12FunctionVerificationLauncher.classify(prematureV12))
                .isEqualTo(FlywayV12FunctionVerificationLauncher.FunctionState.INVALID);
    }

    @Test void postDefinitionOrKeyDriftIsRejected() {
        var wrongColumn = new FlywayV12FunctionVerificationLauncher.StructureSnapshot(
                3, 2, 1, 1, 10, 10, 1, 1, 2, 2, 1, 1);
        var missingForeignKey = new FlywayV12FunctionVerificationLauncher.StructureSnapshot(
                3, 3, 1, 1, 10, 10, 1, 1, 2, 2, 0, 0);
        assertThat(FlywayV12FunctionVerificationLauncher.classify(wrongColumn))
                .isEqualTo(FlywayV12FunctionVerificationLauncher.FunctionState.INVALID);
        assertThat(FlywayV12FunctionVerificationLauncher.classify(missingForeignKey))
                .isEqualTo(FlywayV12FunctionVerificationLauncher.FunctionState.INVALID);
    }

    private static void assertRejected(FlywayV12FunctionVerificationLauncher.FunctionState state, String code) {
        FakeRuntime runtime = runtime(state);
        assertThatThrownBy(() -> FlywayV12FunctionVerificationLauncher.execute(
                new String[]{"release-mysql"}, "true", runtime)).hasMessage(code);
        assertThat(runtime.migrations).hasValue(0);
    }

    private static FakeRuntime runtime(FlywayV12FunctionVerificationLauncher.FunctionState... states) {
        return new FakeRuntime(new ArrayDeque<>(java.util.List.of(states)));
    }

    private static final class FakeRuntime implements FlywayV12FunctionVerificationLauncher.Runtime {
        private final Deque<FlywayV12FunctionVerificationLauncher.FunctionState> states;
        private final AtomicInteger inspections = new AtomicInteger();
        private final AtomicInteger migrations = new AtomicInteger();
        private final java.util.List<FlywayV12FunctionVerificationLauncher.FunctionState> inventories = new java.util.ArrayList<>();
        private boolean failMigration;

        private FakeRuntime(Deque<FlywayV12FunctionVerificationLauncher.FunctionState> states) { this.states = states; }

        @Override public FlywayV12FunctionVerificationLauncher.FunctionState inspect() {
            inspections.incrementAndGet();
            return states.removeFirst();
        }

        @Override public void requireExactInventory(FlywayV12FunctionVerificationLauncher.FunctionState state) {
            inventories.add(state);
        }

        @Override public void migrateOnce() {
            migrations.incrementAndGet();
            if (failMigration) throw new IllegalStateException("SYNTHETIC_MIGRATION_FAILURE");
        }
    }
}
