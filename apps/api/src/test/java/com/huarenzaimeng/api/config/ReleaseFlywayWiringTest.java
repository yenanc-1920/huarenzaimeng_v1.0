package com.huarenzaimeng.api.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReleaseFlywayWiringTest {
    @Test void releaseProfileKeepsMigrationRunnerDisabledByDefaultWithoutExpectedIdentity() {
        try (AnnotationConfigApplicationContext context = releaseContext(Map.of(
                "spring.flyway.url", "jdbc:mysql://127.0.0.1:1/not_connected",
                "spring.flyway.user", "synthetic_migrator",
                "spring.flyway.password", "synthetic-not-a-real-secret",
                "spring.flyway.connect-retries", "0"
        ))) {
            assertThat(context.getBean(Flyway.class)).isNotNull();
            assertThat(context.getBeansOfType(ReleaseFlywayMigrationRunner.class)).isEmpty();
            assertThat(context.getBean(ReleaseMigrationState.class).phase())
                    .isEqualTo(ReleaseMigrationState.Phase.MIGRATING);
        }
    }

    @Test void migrationEnabledAloneStillCannotCreateOrRunTheControlledCommand() {
        try (AnnotationConfigApplicationContext context = releaseContext(Map.of(
                    "spring.flyway.url", "jdbc:mysql://127.0.0.1:1/not_connected",
                    "spring.flyway.user", "synthetic_migrator",
                    "spring.flyway.password", "synthetic-not-a-real-secret",
                    "spring.flyway.connect-retries", "0",
                    "spring.flyway.validate-on-migrate", "true",
                    "spring.flyway.baseline-on-migrate", "false",
                    "hz.data-integration.migration-enabled", "true",
                    "hz.data-integration.expected-database-name", "huarenzaimeng_it_vnext",
                    "hz.data-integration.expected-server-uuid", "00000000-0000-0000-0000-000000000001"
            ))) {

            assertThat(context.getBean(Flyway.class)).isNotNull();
            assertThat(context.getBeansOfType(ReleaseFlywayMigrationRunner.class)).isEmpty();
            assertThat(context.getBean(ReleaseMigrationState.class).phase())
                    .isEqualTo(ReleaseMigrationState.Phase.MIGRATING);
        }
    }

    private static AnnotationConfigApplicationContext releaseContext(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("release-mysql");
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("testReleaseFlyway", properties));
        context.getBeanFactory().registerSingleton("dataSource", mock(DataSource.class));
        context.register(ReleaseFlywayConfiguration.class);
        context.refresh();
        return context;
    }
}
