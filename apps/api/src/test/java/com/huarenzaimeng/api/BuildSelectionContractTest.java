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
    void containerBuildRunsTestsBeforePackagingAndDefaultsToMock() throws IOException {
        String dockerfile = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
        int test = dockerfile.indexOf("clean test");
        int packaging = dockerfile.indexOf("package -DskipTests");
        assertThat(test).isGreaterThanOrEqualTo(0);
        assertThat(packaging).isGreaterThan(test);
        assertThat(dockerfile).contains("SPRING_PROFILES_ACTIVE=mock")
                .contains("FROM node:24-alpine AS admin-web-build")
                .contains("VITE_ADMIN_DATA_MODE=PROJECT_API_PROXY")
                .contains("npm run test:contracts && npm run build")
                .contains("COPY --from=admin-web-build /workspace/apps/admin-web/dist apps/api/src/main/resources/static")
                .contains("USER 10001:10001")
                .contains("--chown=10001:10001");
        assertThat(Files.readString(PROJECT_ROOT.resolve("apps/api/src/main/java/com/huarenzaimeng/api/MockFlowController.java")))
                .contains("@Profile({\"mock\", \"test\"})");
    }
}
