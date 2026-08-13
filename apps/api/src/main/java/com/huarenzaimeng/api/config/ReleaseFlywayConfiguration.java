package com.huarenzaimeng.api.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("release-mysql")
public class ReleaseFlywayConfiguration {
    @Bean
    ReleaseMigrationState releaseMigrationState() {
        return new ReleaseMigrationState();
    }

    @Bean
    Flyway releaseFlyway(
            @Value("${spring.flyway.url}") String url,
            @Value("${spring.flyway.user}") String user,
            @Value("${spring.flyway.password}") String password,
            @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
            @Value("${spring.flyway.connect-retries:0}") int connectRetries,
            @Value("${spring.flyway.validate-on-migrate:true}") boolean validateOnMigrate,
            @Value("${spring.flyway.baseline-on-migrate:false}") boolean baselineOnMigrate) {
        if (connectRetries != 0) {
            throw new IllegalStateException("FLYWAY_CONNECT_RETRIES_MUST_BE_ZERO");
        }
        return Flyway.configure()
                .dataSource(url, user, password)
                .locations(locations.split(","))
                .connectRetries(connectRetries)
                .validateOnMigrate(validateOnMigrate)
                .baselineOnMigrate(baselineOnMigrate)
                .load();
    }

    @Bean
    ReleaseFlywayMigrationRunner releaseFlywayMigrationRunner(
            Flyway releaseFlyway,
            ReleaseMigrationState state,
            @Value("${hz.data-integration.expected-database-name}") String expectedDatabaseName,
            @Value("${hz.data-integration.expected-server-uuid}") String expectedServerUuid) {
        return new ReleaseFlywayMigrationRunner(releaseFlyway, state, expectedDatabaseName, expectedServerUuid);
    }
}
