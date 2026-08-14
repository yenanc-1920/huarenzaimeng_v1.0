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
        String adminAuth = Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/adminauth/AdminAuthController.java"));

        assertThat(yaml).contains("flyway:\n    #", "enabled: false")
                .contains("user: ${SPRING_FLYWAY_USER}", "password: ${SPRING_FLYWAY_PASSWORD}")
                .doesNotContain("SPRING_DATASOURCE_PASSWORD: ${SPRING_FLYWAY_PASSWORD}");
        assertThat(configuration).contains("Flyway.configure()", ".dataSource(url, user, password)")
                .doesNotContain("System.out", "logger.", "printStackTrace");
        assertThat(runner).doesNotContain("implements ApplicationRunner", "flyway.migrate();")
                .contains("stages.migrateTo(\"11\")", "DataMigrationOracleVerifier.State.MID_V11",
                        "stages.migrateTo(\"12\")", "DataMigrationOracleVerifier.State.POST_V12",
                        "authorizations.consume(authorization, identity)", "state.failed();", "throw failure;");
        assertThat(gate).contains("@Order(Ordered.HIGHEST_PRECEDENCE)", "SC_SERVICE_UNAVAILABLE")
                .contains("\"/actuator/health\"",
                        "\"/actuator/health/liveness\"",
                        "\"/actuator/health/readiness\"",
                        "\"/admin-read/v1/data-integration/readiness\"")
                .contains("shouldNotFilterAsyncDispatch()", "shouldNotFilterErrorDispatch()",
                        "DispatcherType.REQUEST", "CLOSED_LOGIN_PATH", "application/json",
                        "Cache-Control", "no-store")
                .doesNotContain("path.startsWith(\"/admin\")");
        assertThat(adminAuth).contains("@PostMapping(\"/login\")", "response.addCookie(cookie)",
                "HZ_ADMIN_SESSION");
    }
}
