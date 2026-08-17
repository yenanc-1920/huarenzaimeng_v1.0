package com.huarenzaimeng.api.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ProdFlywayBootstrapRunnerTest {
    @Test void initializesOnlyAnEmptyExactProdDatabaseAndMarksReady() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_prod", 0L, true, 0L);
        ReleaseMigrationState state = new ReleaseMigrationState();

        new ProdFlywayBootstrapRunner(fixture.source, fixture.flyway, state,
                "huarenzaimeng_prod", true).run(null);

        verify(fixture.flyway).migrate();
        org.assertj.core.api.Assertions.assertThat(state.isReady()).isTrue();
    }

    @Test void restartVerifiesTerminalStateWithoutMigrating() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_prod", 37L, true, 0L);
        ReleaseMigrationState state = new ReleaseMigrationState();

        new ProdFlywayBootstrapRunner(fixture.source, fixture.flyway, state,
                "huarenzaimeng_prod", false).run(null);

        verify(fixture.flyway, never()).migrate();
        org.assertj.core.api.Assertions.assertThat(state.isReady()).isTrue();
    }

    @Test void rejectsWrongDatabaseBeforeMigration() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_stage", 0L, true, 0L);
        ReleaseMigrationState state = new ReleaseMigrationState();

        assertThrows(IllegalStateException.class, () ->
                new ProdFlywayBootstrapRunner(fixture.source, fixture.flyway, state,
                        "huarenzaimeng_prod", true).run(null));

        verify(fixture.flyway, never()).migrate();
        org.assertj.core.api.Assertions.assertThat(state.phase())
                .isEqualTo(ReleaseMigrationState.Phase.FAILED);
    }

    @Test void resumesOnlyAnExactValidatedPreV10Initialization() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_prod", 20L, true, 0L);

        new ProdFlywayBootstrapRunner(fixture.source, fixture.flyway,
                new ReleaseMigrationState(), "huarenzaimeng_prod", true).run(null);

        verify(fixture.flyway).validate();
        verify(fixture.flyway).migrate();
    }

    @Test void rejectsInitializationWhenDatabaseIsUnknownNonEmptyState() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_prod", 20L, false, 0L);

        assertThrows(IllegalStateException.class, () ->
                new ProdFlywayBootstrapRunner(fixture.source, fixture.flyway,
                        new ReleaseMigrationState(), "huarenzaimeng_prod", true).run(null));

        verify(fixture.flyway, never()).migrate();
    }

    @Test void rejectsDevelopmentSeedRows() throws Exception {
        Fixture fixture = fixture("huarenzaimeng_prod", 37L, true, 1L);

        assertThrows(IllegalStateException.class, () ->
                new ProdFlywayBootstrapRunner(fixture.source, fixture.flyway,
                        new ReleaseMigrationState(), "huarenzaimeng_prod", false).run(null));
    }

    private static Fixture fixture(String database, long existingTables,
            boolean validPreV10, long seedRows) throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        Flyway flyway = mock(Flyway.class);
        ResultSet identity = single(database);
        ResultSet tables = single(existingTables);
        ResultSet preHistory = mock(ResultSet.class);
        ResultSet history = mock(ResultSet.class);
        ResultSet seeds = single(seedRows);
        when(history.next()).thenReturn(true, false);
        when(history.getLong(1)).thenReturn(14L);
        when(history.getLong(2)).thenReturn(14L);
        when(history.getLong(3)).thenReturn(14L);
        when(history.getLong(4)).thenReturn(14L);
        when(history.getLong(5)).thenReturn(0L);
        when(preHistory.next()).thenReturn(true, false);
        when(preHistory.getLong(1)).thenReturn(validPreV10 ? 10L : 9L);
        when(preHistory.getLong(2)).thenReturn(10L);
        when(preHistory.getLong(3)).thenReturn(1L);
        when(preHistory.getLong(4)).thenReturn(10L);
        when(preHistory.getLong(5)).thenReturn(10L);
        when(preHistory.getLong(6)).thenReturn(0L);
        when(preHistory.getLong(7)).thenReturn(10L);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT DATABASE()")) .thenReturn(identity);
        when(statement.executeQuery(startsWith("SELECT COUNT(*) FROM information_schema.tables"))).thenReturn(tables);
        when(statement.executeQuery(startsWith("SELECT COUNT(*), COUNT(DISTINCT version)"))).thenReturn(history);
        when(statement.executeQuery(startsWith("SELECT COUNT(*), COUNT(DISTINCT version), MIN"))).thenReturn(preHistory);
        when(statement.executeQuery("SELECT COUNT(*) FROM hz_v1_dev_seed_registry")).thenReturn(seeds);
        return new Fixture(source, flyway);
    }

    private static ResultSet single(Object value) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        if (value instanceof String string) when(result.getString(1)).thenReturn(string);
        if (value instanceof Long number) when(result.getLong(1)).thenReturn(number);
        return result;
    }

    private record Fixture(DataSource source, Flyway flyway) {}
}
