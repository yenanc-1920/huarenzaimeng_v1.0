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
                .doesNotContain("-Dloader.main=com.huarenzaimeng.api.FlywayV12FunctionVerificationLauncher");
        assertFixedJvmTruststore(dockerfile);
        assertThat(devDockerfile).contains("package -DskipTests -Plocal-devdata")
                .contains("ENV SPRING_PROFILES_ACTIVE=release-mysql,local-mysql")
                .doesNotContain("clean test");
        assertFixedJvmTruststore(devDockerfile);
        assertThat(testDockerfile).contains("ENV SPRING_PROFILES_ACTIVE=release-mysql,test-mysql")
                .doesNotContain("clean test");
        assertFixedJvmTruststore(testDockerfile);
        assertThat(stageDockerfile).contains("ENV SPRING_PROFILES_ACTIVE=release-mysql,stage-mysql")
                .doesNotContain("clean test");
        assertFixedJvmTruststore(stageDockerfile);
        assertThat(prodDockerfile).contains("ENV SPRING_PROFILES_ACTIVE=release-mysql,prod-mysql")
                .doesNotContain("clean test");
        assertFixedJvmTruststore(prodDockerfile);
        assertThat(entrypoint).contains("release-mysql,local-mysql")
                .contains("release-mysql,test-mysql")
                .contains("release-mysql,stage-mysql")
                .contains("release-mysql,prod-mysql")
                .contains("Configured JVM truststore is missing or unreadable")
                .contains("Configured JVM truststore failed validation")
                .contains("HZ_JAVA_RUNTIME_TRUSTSTORE_PATH")
                .contains("HZ_RUNTIME_CA_DIRECTORY")
                .contains("HZ_SYSTEM_CA_DIRECTORY")
                .contains("keytool -importcert")
                .contains("JVM_RUNTIME_TRUSTSTORE_READY")
                .contains("export JAVA_TOOL_OPTIONS")
                .contains("keytool -list")
                .contains("exit 78")
                .contains("exit 64")
                .doesNotContain("mock");
        assertThat(gate).contains("run-dev-fast-gate.mjs")
                .contains("run-dev-deployment-gate.mjs");
        assertThat(Files.readString(PROJECT_ROOT.resolve("apps/api/src/main/java/com/huarenzaimeng/api/MockFlowController.java")))
                .contains("@Profile({\"mock\", \"test\"})");
    }

    private static void assertFixedJvmTruststore(String dockerfile) {
        assertThat(dockerfile)
                .contains("USE_SYSTEM_CA_CERTS=1 /__cacert_entrypoint.sh /bin/true")
                .contains("cp \"$JAVA_HOME/lib/security/cacerts\" /app/truststore/cacerts")
                .contains("chmod 0444 /app/truststore/cacerts")
                .contains("keytool -list -keystore /app/truststore/cacerts -storepass changeit")
                .contains("ENV HZ_JAVA_TRUSTSTORE_PATH=/app/truststore/cacerts")
                .contains("-Djavax.net.ssl.trustStore=/app/truststore/cacerts")
                .contains("ENTRYPOINT [\"/app/container-entrypoint.sh\"]")
                .doesNotContain("ENV USE_SYSTEM_CA_CERTS=1")
                .doesNotContain("ENTRYPOINT [\"/__cacert_entrypoint.sh\"");
    }
}
