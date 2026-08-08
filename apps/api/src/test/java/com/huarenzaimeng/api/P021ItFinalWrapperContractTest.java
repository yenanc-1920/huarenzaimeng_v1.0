package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class P021ItFinalWrapperContractTest {
    private static final Path WRAPPER = Path.of("Invoke-P021TestReadonlyIntegrationFinalRun.ps1");
    private static final Path COLLECTOR = Path.of("../miniapp/scripts/collect-p021-it-page-actual.ps1");
    private static final Path DIAGNOSTIC = Path.of("src/main/java/com/huarenzaimeng/api/P021TestReadonlyDiagnosticController.java");

    @TempDir Path temporaryDirectory;

    @Test void wrapperFreezesTargetDatabaseSevenRefsAndSingleUsePolicy() throws Exception {
        String text = Files.readString(WRAPPER);
        assertThat(text).contains("https://huaren-api-it-284852-10-1456291159.sh.run.tcloudbase.com")
                .contains("huarenzaimeng_it_vnext").contains("AutomaticRetryAllowed -ne $false")
                .contains("$script:OrderRefs.Count -ne 7").doesNotContain("huaren-api'");
        assertThat(text).doesNotContain("SkipHttpErrorCheck").contains("DatabaseInstanceIdentity")
                .contains("DiagnosticControllerSha256").doesNotContain("MysqlExecutableSha256")
                .doesNotContain("--login-path").doesNotContain("Invoke-Mysql")
                .contains("ManifestSha256").contains("AuthorizationRecordSha256")
                .contains("AuthorizationConsumptionSha256").contains("Consumable=$false");
    }

    @Test void tokensAreInteractiveSecureAndNeverSerialized() throws Exception {
        String text = Files.readString(WRAPPER);
        assertThat(text).contains("Read-Host '请输入 BUYER 测试会话 token' -AsSecureString")
                .contains("Read-Host '请输入 CS 测试会话 token' -AsSecureString")
                .contains("Read-Host '请输入 FIN 测试会话 token' -AsSecureString")
                .contains("SecretsPersisted=$false").doesNotContain("$env:HZ_IT_BUYER_SESSION_TOKEN");
    }

    @Test void scenariosAreSequentialRollbackAndFailClosed() throws Exception {
        String text = Files.readString(WRAPPER);
        for (int i=1;i<=7;i++) { assertThat(text).contains("'P021-IT-0"+i+"'"); }
        assertThat(text).contains("/internal/test-readonly/p021/conflict/").contains("IT03_ROLLBACK_NOT_PROVEN")
                .contains("finally").contains("BLOCKED.json").contains("READY.json")
                .contains("FileMode]::CreateNew").contains("process.stdout.txt").contains("process.stderr.txt")
                .contains("[IO.Directory]::Move($stagingRoot,$runRoot)")
                .contains("Write-JsonAtomic (Join-Path $runRoot 'READY.json')");
        assertThat(text.indexOf("Consumable=$false")).isLessThan(text.lastIndexOf("READY.json"));
    }

    @Test void actualPageCollectorIsBoundAndTokensUseStdin() throws Exception {
        String wrapper = Files.readString(WRAPPER);
        String collector = Files.readString(COLLECTOR);
        assertThat(wrapper).contains("PageCollectorSha256").contains("RedirectStandardInput=$true")
                .contains("LATE_LOWER_VERSION_AFTER_READY").contains("CONTENT_DENIED")
                .contains("ForbiddenFieldFindings").contains("ForbiddenIdentityHeaderFindings")
                .contains("BrowserNetwork").contains("ProxyNetwork");
        assertThat(collector).contains("collect-p021-it-page-actual.mjs").doesNotContain("Authorization");
    }

    @Test void runtimeChallengeAndThreeRollbackConflictsAreTestOnly() throws Exception {
        String text=Files.readString(DIAGNOSTIC);
        assertThat(text).contains("/challenge").contains("K_REVISION").contains("TCB_CLOUD_RUN_VERSION")
                .contains("/snapshot").contains("/projection").contains("FIXED_ORDERS")
                .contains("databaseIdentity").contains("huarenzaimeng_it_vnext|")
                .contains("PROJECTION_LOW_VERSION").contains("ORDER_VERSION_CONFLICT").contains("QUOTE_DIGEST_CONFLICT")
                .contains("status.setRollbackOnly()").contains("hz.p021.mode").contains("test-readonly")
                .contains("hz.persistence.mode").contains("mysql");
        assertThat(text).doesNotContain("@RequestBody").doesNotContain("String sql").doesNotContain("nativeQuery");
    }

    @Test void runtimeChallengeFallsBackToVerifiableArtifactIdentity() throws Exception {
        Path artifact = temporaryDirectory.resolve("app.jar");
        Files.writeString(artifact, "fixed-artifact");
        assertThat(P021TestReadonlyDiagnosticController.artifactIdentity(artifact))
                .matches("ARTIFACT_SHA256:[A-F0-9]{64}")
                .isEqualTo(P021TestReadonlyDiagnosticController.artifactIdentity(artifact));
    }
}
