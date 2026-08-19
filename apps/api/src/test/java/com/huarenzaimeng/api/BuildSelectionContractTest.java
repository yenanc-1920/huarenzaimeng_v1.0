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
    void containerBuildPackagesWithoutRepeatingCiTestsAndFailsClosedWithoutReleaseProfile() throws IOException {
        String dockerfile = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
        String devDockerfile = Files.readString(PROJECT_ROOT.resolve("Dockerfile.dev"));
        String testDockerfile = Files.readString(PROJECT_ROOT.resolve("Dockerfile.test"));
        String stageDockerfile = Files.readString(PROJECT_ROOT.resolve("Dockerfile.stage"));
        String prodDockerfile = Files.readString(PROJECT_ROOT.resolve("Dockerfile.prod"));
        String entrypoint = Files.readString(PROJECT_ROOT.resolve("tools/container-entrypoint.sh"));
        String gate = Files.readString(PROJECT_ROOT.resolve(".github/workflows/dev-predeploy-gate.yml"));
        assertThat(dockerfile).contains("package -DskipTests")
                .doesNotContain("clean test")
                .doesNotContain("ENV SPRING_PROFILES_ACTIVE=")
                .contains("FROM node:24-alpine AS admin-web-build")
                .contains("VITE_ADMIN_DATA_MODE=PROJECT_API_PROXY")
                .contains("npm run build")
                .contains("COPY --from=admin-web-build /workspace/apps/admin-web/dist apps/api/src/main/resources/static")
                .contains("USER 10001:10001")
                .contains("--chown=10001:10001")
                .contains("ENV USE_SYSTEM_CA_CERTS=1")
                .contains("ENTRYPOINT [\"/__cacert_entrypoint.sh\", \"/app/container-entrypoint.sh\"]")
                .doesNotContain("-Dloader.main=com.huarenzaimeng.api.FlywayV12FunctionVerificationLauncher");
        assertThat(devDockerfile).contains("package -DskipTests -Plocal-devdata")
                .contains("ENV SPRING_PROFILES_ACTIVE=release-mysql,local-mysql")
                .contains("ENV USE_SYSTEM_CA_CERTS=1")
                .contains("ENTRYPOINT [\"/__cacert_entrypoint.sh\", \"/app/container-entrypoint.sh\"]")
                .doesNotContain("clean test");
        assertThat(testDockerfile).contains("ENV SPRING_PROFILES_ACTIVE=release-mysql,test-mysql")
                .contains("ENV USE_SYSTEM_CA_CERTS=1")
                .contains("ENTRYPOINT [\"/__cacert_entrypoint.sh\", \"/app/container-entrypoint.sh\"]")
                .doesNotContain("clean test");
        assertThat(stageDockerfile).contains("ENV SPRING_PROFILES_ACTIVE=release-mysql,stage-mysql")
                .contains("ENV USE_SYSTEM_CA_CERTS=1")
                .contains("ENTRYPOINT [\"/__cacert_entrypoint.sh\", \"/app/container-entrypoint.sh\"]")
                .doesNotContain("clean test");
        assertThat(prodDockerfile).contains("ENV SPRING_PROFILES_ACTIVE=release-mysql,prod-mysql")
                .contains("ENV USE_SYSTEM_CA_CERTS=1")
                .contains("ENTRYPOINT [\"/__cacert_entrypoint.sh\", \"/app/container-entrypoint.sh\"]")
                .doesNotContain("clean test");
        assertThat(entrypoint).contains("release-mysql,local-mysql")
                .contains("release-mysql,test-mysql")
                .contains("release-mysql,stage-mysql")
                .contains("release-mysql,prod-mysql")
                .contains("exit 64")
                .doesNotContain("mock");
        assertThat(gate).contains("run-dev-fast-gate.mjs")
                .contains("run-dev-deployment-gate.mjs");
        assertThat(Files.readString(PROJECT_ROOT.resolve("apps/api/src/main/java/com/huarenzaimeng/api/MockFlowController.java")))
                .contains("@Profile({\"mock\", \"test\"})");
    }
}
