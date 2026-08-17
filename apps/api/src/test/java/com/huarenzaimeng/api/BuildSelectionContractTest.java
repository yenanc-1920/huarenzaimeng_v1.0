package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BuildSelectionContractTest {
    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir")).resolve("../..").normalize();

    @Test
    void backendUsesApprovedMyBatisLineAndNoJdbcClientStore() throws IOException {
        String pom = Files.readString(PROJECT_ROOT.resolve("apps/api/pom.xml"));
        assertThat(pom).contains("mybatis-spring-boot-starter")
                .contains("<version>3.0.5</version>")
                .doesNotContain("spring-boot-starter-jdbc");
        assertThat(Files.exists(PROJECT_ROOT.resolve(
                "apps/api/src/main/java/com/huarenzaimeng/api/JdbcFlowStore.java"))).isFalse();
    }

    @Test
    void containerBuildPackagesWithoutRepeatingCiTestsAndDefaultsToMock() throws IOException {
        String dockerfile = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
        String devDockerfile = Files.readString(PROJECT_ROOT.resolve("Dockerfile.dev"));
        String gate = Files.readString(PROJECT_ROOT.resolve(".github/workflows/dev-predeploy-gate.yml"));
        assertThat(dockerfile).contains("package -DskipTests")
                .doesNotContain("clean test")
                .contains("SPRING_PROFILES_ACTIVE=mock")
                .contains("FROM node:24-alpine AS admin-web-build")
                .contains("VITE_ADMIN_DATA_MODE=PROJECT_API_PROXY")
                .contains("npm run build")
                .contains("COPY --from=admin-web-build /workspace/apps/admin-web/dist apps/api/src/main/resources/static")
                .contains("USER 10001:10001")
                .contains("--chown=10001:10001")
                .contains("ENTRYPOINT [\"java\", \"-jar\", \"/app/app.jar\"]")
                .doesNotContain("-Dloader.main=com.huarenzaimeng.api.FlywayV12FunctionVerificationLauncher");
        assertThat(devDockerfile).contains("package -DskipTests -Plocal-devdata")
                .doesNotContain("clean test");
        assertThat(gate).contains("run-dev-fast-gate.mjs")
                .contains("run-dev-deployment-gate.mjs");
        assertThat(Files.readString(PROJECT_ROOT.resolve("apps/api/src/main/java/com/huarenzaimeng/api/MockFlowController.java")))
                .contains("@Profile({\"mock\", \"test\"})");
    }
}
