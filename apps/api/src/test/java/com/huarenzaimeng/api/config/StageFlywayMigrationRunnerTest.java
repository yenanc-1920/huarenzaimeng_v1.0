package com.huarenzaimeng.api.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
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

class StageFlywayMigrationRunnerTest {
    @Test void migratesExactlyOnceWhenStageDatabaseIdentityMatches() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        Flyway flyway = mock(Flyway.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT DATABASE()")) .thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString(1)).thenReturn("huarenzaimeng_stage");

        new StageFlywayMigrationRunner(source, flyway, "huarenzaimeng_stage").run(null);

        verify(flyway).migrate();
    }

    @Test void rejectsWrongDatabaseBeforeMigration() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        Flyway flyway = mock(Flyway.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT DATABASE()")) .thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn("huarenzaimeng_test");

        assertThrows(IllegalStateException.class,
                () -> new StageFlywayMigrationRunner(source, flyway, "huarenzaimeng_stage").run(null));
        verify(flyway, never()).migrate();
    }
}
