package com.huarenzaimeng.api.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.time.Clock;
import javax.sql.DataSource;

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
            @Value("${hz.environment.database-name}") String expectedDatabase,
            @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
            @Value("${spring.flyway.connect-retries:0}") int connectRetries,
            @Value("${spring.flyway.validate-on-migrate:true}") boolean validateOnMigrate,
            @Value("${spring.flyway.baseline-on-migrate:false}") boolean baselineOnMigrate) {
        if (connectRetries != 0) {
            throw new IllegalStateException("FLYWAY_CONNECT_RETRIES_MUST_BE_ZERO");
        }
        return Flyway.configure()
                .dataSource(url, user, password)
                .defaultSchema(expectedDatabase)
                .locations(locations.split(","))
                .connectRetries(connectRetries)
                .validateOnMigrate(validateOnMigrate)
                .baselineOnMigrate(baselineOnMigrate)
                .load();
    }

    @Bean
    @Profile("!prod-mysql")
    ReleaseMigrationReadyVerifier releaseMigrationReadyVerifier(Flyway releaseFlyway,
            DataSource dataSource, ReleaseMigrationState state) {
        var store = ReleaseMigrationAuthorizationStore.fixed(Clock.systemUTC());
        var identities = new ReleaseMigrationRuntimeIdentityProvider(dataSource, releaseFlyway);
        return new ReleaseMigrationReadyVerifier(store, identities,
                ReleaseFlywayMigrationRunner.flywayStages(releaseFlyway), state);
    }

}
