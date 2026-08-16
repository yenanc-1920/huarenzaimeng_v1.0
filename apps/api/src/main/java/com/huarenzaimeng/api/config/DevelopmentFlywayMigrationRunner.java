package com.huarenzaimeng.api.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Development-only migration entry for the isolated CloudRun DEV database. */
@Component
@Profile("release-mysql & local-mysql")
@Order(Ordered.HIGHEST_PRECEDENCE)
final class DevelopmentFlywayMigrationRunner implements ApplicationRunner {
    private final DataSource dataSource;
    private final Flyway flyway;
    private final String expectedDatabase;

    DevelopmentFlywayMigrationRunner(DataSource dataSource, Flyway flyway,
            @Value("${hz.dev-database-name}") String expectedDatabase) {
        this.dataSource = dataSource;
        this.flyway = flyway;
        this.expectedDatabase = expectedDatabase;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (expectedDatabase == null || expectedDatabase.isBlank()) {
            throw new IllegalStateException("DEV_DATABASE_NAME_REQUIRED");
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next() || !expectedDatabase.equals(result.getString(1)) || result.next()) {
                throw new IllegalStateException("DEV_DATABASE_IDENTITY_MISMATCH");
            }
        }
        flyway.migrate();
    }
}
