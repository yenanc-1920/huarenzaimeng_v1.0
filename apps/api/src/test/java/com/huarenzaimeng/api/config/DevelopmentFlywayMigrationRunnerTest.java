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
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;

class DevelopmentFlywayMigrationRunnerTest {
    @Test
    void explicitlySelectsReleaseFlywayWhenOtherFlywayBeansExist() {
        Constructor<?> constructor = DevelopmentFlywayMigrationRunner.class.getDeclaredConstructors()[0];
        Annotation[] annotations = constructor.getParameterAnnotations()[1];
        Qualifier qualifier = null;
        for (Annotation annotation : annotations) {
            if (annotation instanceof Qualifier candidate) {
                qualifier = candidate;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(qualifier);
        org.junit.jupiter.api.Assertions.assertEquals("releaseFlyway", qualifier.value());
    }

    @Test
    void migratesExactlyOnceWhenDatabaseIdentityMatches() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        Flyway flyway = mock(Flyway.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT DATABASE()" )).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString(1)).thenReturn("huarenzaimeng_dev");

        new DevelopmentFlywayMigrationRunner(source, flyway, "huarenzaimeng_dev").run(null);

        verify(flyway).migrate();
    }

    @Test
    void rejectsWrongDatabaseBeforeMigration() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        Flyway flyway = mock(Flyway.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT DATABASE()" )).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn("huarenzaimeng_test");

        assertThrows(IllegalStateException.class,
                () -> new DevelopmentFlywayMigrationRunner(source, flyway, "huarenzaimeng_dev").run(null));
        verify(flyway, never()).migrate();
    }
}
