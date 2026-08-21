package com.huarenzaimeng.api.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ProdFlywayBootstrapRunnerTest {
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("V(\\d+)__.+\\.sql");

    @Test void terminalExpectationTracksTheCompleteMigrationManifest() throws Exception {
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            var versions = files
                    .map(path -> path.getFileName().toString())
                    .map(VERSIONED_MIGRATION::matcher)
                    .filter(matcher -> matcher.matches())
                    .mapToLong(matcher -> Long.parseLong(matcher.group(1)))
                    .sorted()
                    .toArray();

            org.assertj.core.api.Assertions.assertThat(versions).hasSize(25);
            org.assertj.core.api.Assertions.assertThat(versions)
                    .containsExactly(java.util.stream.LongStream.rangeClosed(1L, 25L).toArray());
            org.assertj.core.api.Assertions.assertThat(ProdFlywayBootstrapRunner.EXPECTED_VERSIONED_MIGRATION_COUNT)
                    .isEqualTo(versions.length);
            org.assertj.core.api.Assertions.assertThat(ProdFlywayBootstrapRunner.EXPECTED_TERMINAL_MIGRATION_VERSION)
                    .isEqualTo(versions[versions.length - 1]);
        }
    }

    @Test void initializesOnlyAnEmptyExactProdDatabaseAndMarksReady() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_prod", false);
        ReleaseMigrationState state = new ReleaseMigrationState();

        runner(fixture, state, true, false).onApplicationReady();

        verify(fixture.flyway).migrate();
        org.assertj.core.api.Assertions.assertThat(state.isReady()).isTrue();
    }

    @Test void restartVerifiesTerminalStateWithoutMigrating() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_prod", false);
        ReleaseMigrationState state = new ReleaseMigrationState();

        runner(fixture, state, false, false).onApplicationReady();

        verify(fixture.flyway, never()).migrate();
        org.assertj.core.api.Assertions.assertThat(state.isReady()).isTrue();
    }

    @Test void rejectsWrongDatabaseBeforeMigration() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_stage", false);
        ReleaseMigrationState state = new ReleaseMigrationState();

        assertThrows(IllegalStateException.class, () ->
                new ProdFlywayBootstrapRunner(fixture.source, fixture.flyway, state,
                        "huarenzaimeng_prod", true, false, Runnable::run).migrateAndVerify());

        verify(fixture.flyway, never()).migrate();
        org.assertj.core.api.Assertions.assertThat(state.phase())
                .isEqualTo(ReleaseMigrationState.Phase.FAILED);
    }

    @Test void rejectsDevelopmentRegistryOutsideDevelopment() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_prod", true);

        assertThrows(IllegalStateException.class, () ->
                runner(fixture, new ReleaseMigrationState(), false, false).migrateAndVerify());
    }

    @Test void acceptsDevelopmentRegistryOnlyForDevelopment() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_dev", true);
        ReleaseMigrationState state = new ReleaseMigrationState();

        runner(fixture, state, true, true).migrateAndVerify();

        verify(fixture.flyway).migrate();
        org.assertj.core.api.Assertions.assertThat(state.isReady()).isTrue();
    }

    @Test void acceptsEmptyDevelopmentRegistryOutsideDevelopment() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_prod", true, 0L);
        ReleaseMigrationState state = new ReleaseMigrationState();

        runner(fixture, state, false, false).migrateAndVerify();

        org.assertj.core.api.Assertions.assertThat(state.isReady()).isTrue();
    }

    @Test void duplicateReadyEventsExecuteBootstrapExactlyOnce() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_prod", false);
        ProdFlywayBootstrapRunner runner = runner(fixture, new ReleaseMigrationState(), true, false);

        runner.onApplicationReady();
        runner.onApplicationReady();

        verify(fixture.flyway).migrate();
    }

    private static ProdFlywayBootstrapRunner runner(Fixture fixture,
            ReleaseMigrationState state, boolean migrationEnabled, boolean developmentDataExpected) {
        return new ProdFlywayBootstrapRunner(fixture.source, fixture.flyway, state,
                fixture.database, migrationEnabled, developmentDataExpected, Runnable::run);
    }

    private static Fixture fixture(String database, boolean registryPresent) throws Exception {
        return fixture(database, registryPresent, registryPresent ? 1L : 0L);
    }

    private static Fixture fixture(String database, boolean registryPresent, long seedCount) throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        Flyway flyway = mock(Flyway.class);
        ResultSet identity = single(database);
        ResultSet history = mock(ResultSet.class);
        ResultSet registry = single(registryPresent ? 1L : 0L);
        ResultSet seeds = single(seedCount);
        when(history.next()).thenReturn(true, false);
        when(history.getLong(1)).thenReturn(25L);
        when(history.getLong(2)).thenReturn(25L);
        when(history.getLong(3)).thenReturn(25L);
        when(history.getLong(4)).thenReturn(25L);
        when(history.getLong(5)).thenReturn(0L);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT DATABASE()")) .thenReturn(identity);
        when(statement.executeQuery(startsWith("SELECT COUNT(*), COUNT(DISTINCT version)"))).thenReturn(history);
        when(statement.executeQuery(startsWith("SELECT COUNT(*) FROM information_schema.tables"))).thenReturn(registry);
        when(statement.executeQuery("SELECT COUNT(*) FROM hz_v1_dev_seed_registry")).thenReturn(seeds);
        return new Fixture(source, flyway, database);
    }

    private static ResultSet single(Object value) throws Exception {
        ResultSet result = mock(ResultSet.class);
        AtomicInteger cursor = new AtomicInteger(-1);
        when(result.next()).thenAnswer(ignored -> cursor.incrementAndGet() == 0);
        if (value instanceof String string) {
            when(result.getString(1)).thenAnswer(ignored -> {
                requireCurrentRow(cursor);
                return string;
            });
        }
        if (value instanceof Long number) {
            when(result.getLong(1)).thenAnswer(ignored -> {
                requireCurrentRow(cursor);
                return number;
            });
        }
        return result;
    }

    private static void requireCurrentRow(AtomicInteger cursor) throws SQLException {
        if (cursor.get() != 0) throw new SQLException("After end of result set");
    }

    private record Fixture(DataSource source, Flyway flyway, String database) {}
}
