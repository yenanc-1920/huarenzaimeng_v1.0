package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseMigrationStartupContractTest {
    @Test void releaseProfileDefersFlywayUntilAfterTheWebServerStarts() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application-release-mysql.yml"));
        String configuration = Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/config/ReleaseFlywayConfiguration.java"));
        String runner = Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/config/ReleaseFlywayMigrationRunner.java"));
        String gate = Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/config/ReleaseMigrationGateFilter.java"));

        assertThat(yaml).contains("flyway:\n    #", "enabled: false")
                .contains("user: ${SPRING_FLYWAY_USER}", "password: ${SPRING_FLYWAY_PASSWORD}")
                .doesNotContain("SPRING_DATASOURCE_PASSWORD: ${SPRING_FLYWAY_PASSWORD}");
        assertThat(configuration).contains("Flyway.configure()", ".dataSource(url, user, password)")
                .doesNotContain("System.out", "logger.", "printStackTrace");
        assertThat(runner).contains("implements ApplicationRunner", "flyway.validate();", "flyway.migrate();")
                .contains("state.failed();", "throw failure;");
        assertThat(gate).contains("@Order(Ordered.HIGHEST_PRECEDENCE)", "SC_SERVICE_UNAVAILABLE")
                .doesNotContain("shouldNotFilter");
    }
}
