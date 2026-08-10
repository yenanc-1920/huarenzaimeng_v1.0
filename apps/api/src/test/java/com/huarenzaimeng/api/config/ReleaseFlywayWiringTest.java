package com.huarenzaimeng.api.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseFlywayWiringTest {
    @Test void releaseProfileWiresManualFlywayRunnerAndClosedGateWithoutConnecting() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("release-mysql");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testReleaseFlyway", Map.of(
                    "spring.flyway.url", "jdbc:mysql://127.0.0.1:1/not_connected",
                    "spring.flyway.user", "synthetic_migrator",
                    "spring.flyway.password", "synthetic-not-a-real-secret",
                    "spring.flyway.connect-retries", "0",
                    "spring.flyway.validate-on-migrate", "true",
                    "spring.flyway.baseline-on-migrate", "false"
            )));
            context.register(ReleaseFlywayConfiguration.class);
            context.refresh();

            assertThat(context.getBean(Flyway.class)).isNotNull();
            assertThat(context.getBean(ReleaseFlywayMigrationRunner.class)).isNotNull();
            assertThat(context.getBean(ReleaseMigrationState.class).phase())
                    .isEqualTo(ReleaseMigrationState.Phase.MIGRATING);
        }
    }
}
