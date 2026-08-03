package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class P021EvidenceRunnerPreparationContractTest {
    private static final Path WRAPPER = Path.of("Invoke-P021OrderDetailEvidenceFinalRun.ps1");
    private static final List<String> WRITE_KEYS = List.of("Command", "CommandAlias", "TopupBusinessKey",
            "TopupSemanticAction", "TopupIntent", "DispatchSemanticAction", "DispatchIntent", "OrderVersion",
            "ProjectionVersion", "SyntheticObservation", "PaymentAttempt", "SendAttempt", "RemoteAcceptance",
            "WechatPrepay", "RequestPayment", "Notification", "ExternalFact", "W", "U", "D", "L",
            "LedgerEntry", "ExternalCall");

    @Test void wrapperFreezesExactTwentyThreeKeysAndRejectsCounterMutations() throws Exception {
        String source = Files.readString(WRAPPER);
        Matcher matcher = Pattern.compile("\\$writeKeys=@\\(([^\\r\\n]+)\\)").matcher(source);
        assertThat(matcher.find()).isTrue();
        List<String> actual = Pattern.compile("'([^']+)'").matcher(matcher.group(1)).results()
                .map(result -> result.group(1)).toList();
        assertThat(actual).containsExactlyElementsOf(WRITE_KEYS);
        assertThat(source).contains("BLOCKED_COUNTER_KEYS", "BLOCKED_COUNTER_ARITHMETIC",
                "BLOCKED_WRITE_DELTA", "BLOCKED_QUERY_OR_AUX_DELTA");
        assertThat(WRITE_KEYS.subList(1, WRITE_KEYS.size())).doesNotContain(WRITE_KEYS.get(0));
        java.util.ArrayList<String> additional = new java.util.ArrayList<>(WRITE_KEYS);
        additional.add("UnexpectedCounter");
        assertThat(additional).hasSize(WRITE_KEYS.size() + 1).contains("UnexpectedCounter");
        assertThat(additional).isNotEqualTo(WRITE_KEYS);
    }

    @Test void authorizationIsValidatedThenAtomicallyConsumedBeforeProcess() throws Exception {
        String source = Files.readString(WRAPPER);
        int schema = source.indexOf("BLOCKED_AUTHORIZATION_SCHEMA");
        int scope = source.indexOf("BLOCKED_AUTHORIZATION_SCOPE_OR_EXPIRY");
        int consume = source.indexOf("ConsumeAuthorization $authorizationMarker");
        int process = source.indexOf("Start-Process");
        assertThat(schema).isPositive(); assertThat(scope).isGreaterThan(schema);
        assertThat(consume).isGreaterThan(scope); assertThat(process).isGreaterThan(consume);
        assertThat(source).contains("[IO.FileMode]::CreateNew", "SingleUse", "ValidUntil",
                "ImplementationAggregateSha", "MatrixIdentitySha", "RunnerAggregateSha");
    }

    @Test void cleanupPrecedesReadyAndAtomicPublicationAndCatchRevokesReady() throws Exception {
        String source = Files.readString(WRAPPER);
        int cleanupStaging = source.indexOf("Remove-Item -LiteralPath $staging -Recurse -Force");
        int cleanupProcess = source.indexOf("Remove-Item -LiteralPath $processTemp -Recurse -Force");
        int ready = source.indexOf("'READY'|Set-Content");
        int publish = source.indexOf("Move-Item -LiteralPath $publishing -Destination $finalDir");
        assertThat(cleanupStaging).isPositive(); assertThat(cleanupProcess).isGreaterThan(cleanupStaging);
        assertThat(ready).isGreaterThan(cleanupProcess); assertThat(publish).isGreaterThan(ready);
        assertThat(source).contains("RevokeJsonTree $candidate $blocked",
                "ExecutionStatus='BLOCKED'", "Consumable=$false");
        int sanitize = source.indexOf("$json.Consumable=$false", source.indexOf("function RevokeJsonTree"));
        int diagnosticCopy = source.indexOf("Where-Object{$_.Extension-ne'.json'}|Copy-Item", sanitize);
        assertThat(sanitize).isPositive(); assertThat(diagnosticCopy).isGreaterThan(sanitize);
        assertThat(source).contains("BLOCKED_READY_REVOCATION_FAILED");
    }

    @Test void runnerFreezesFullResponseAndPerCaseTraceability() throws Exception {
        String wrapper = Files.readString(WRAPPER);
        String runner = Files.readString(Path.of("src/test/java/com/huarenzaimeng/api/P021OrderDetailEvidenceFinalRunTest.java"));
        assertThat(runner).contains("assertCompleteResponseShape", "containsExactlyInAnyOrder(\"requestRef\"",
                "containsExactlyInAnyOrder(\"orderRef\"", "priceSnapshotSummary", "timeline", "allowedActions",
                "FixtureDigest", "CompleteRequest", "FixedInputs", "ImplementationFiles", "ProcessEvidenceRef");
        assertThat(wrapper).contains("BLOCKED_CASE_VERSION_KEYS", "BLOCKED_CASE_VERSION_VALUE",
                "BLOCKED_CASE_IMPLEMENTATION_SHA", "BLOCKED_PROCESS_EVIDENCE_REF");
    }

    @Test void blockedRevocationRecursivelyVerifiesJsonAndQuarantinesRewriteFailure() throws Exception {
        String source = Files.readString(WRAPPER);
        assertThat(source).contains("function AssertBlockedJsonTree([string]$root)",
                "Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.json'",
                "$json.Consumable-ne $false-or$json.ExecutionStatus-ne'BLOCKED'",
                "function RevokeJsonTree([string]$root,[string]$isolationRoot)",
                "Set-Content -LiteralPath $file.FullName -Encoding utf8 -ErrorAction Stop",
                "NONCONSUMABLE-REVOCATION-FAILED-", "BLOCKED_JSON_REVOCATION_FAILED_ISOLATED",
                "foreach($candidate in @($publishing,$finalDir)){RevokeJsonTree $candidate $blocked}",
                "AssertRevokedNamespace $publishing;AssertRevokedNamespace $finalDir");
        assertThat(source).doesNotContain("catch{}}",
                "Set-Content -LiteralPath $file.FullName -Encoding utf8}catch",
                "ConvertFrom-Json;if($j.PSObject.Properties.Name-contains'Consumable'");
    }

    @Test void readyDeletionFailureIsInsideIsolationTransactionAndCannotLeaveConsumableNamespace() throws Exception {
        String source = Files.readString(WRAPPER);
        int revokeTry = source.indexOf("try {", source.indexOf("function RevokeJsonTree"));
        int firstReadyDelete = source.indexOf("Get-ChildItem -LiteralPath $root -Recurse -File -Filter 'READY'|Remove-Item -Force -ErrorAction Stop", revokeTry);
        int revokeCatch = source.indexOf("} catch {", firstReadyDelete);
        int isolate = source.indexOf("Move-Item -LiteralPath $root -Destination $quarantine -ErrorAction Stop", revokeCatch);
        int originalAbsent = source.indexOf("if(Test-Path -LiteralPath $root){throw ('BLOCKED_NAMESPACE_ISOLATION_FAILED|'", isolate);
        int isolatedReadyDelete = source.indexOf("Get-ChildItem -LiteralPath $quarantine -Recurse -File -Filter 'READY'|Remove-Item -Force -ErrorAction Stop", originalAbsent);
        assertThat(revokeTry).isPositive();
        assertThat(firstReadyDelete).isGreaterThan(revokeTry).isLessThan(revokeCatch);
        assertThat(isolate).isGreaterThan(revokeCatch);
        assertThat(originalAbsent).isGreaterThan(isolate);
        assertThat(isolatedReadyDelete).isGreaterThan(originalAbsent);
        assertThat(source).contains("BLOCKED_REVOCATION_FAILED_NONCONSUMABLE_PATH_GATE",
                "AssertRevokedNamespace $publishing;AssertRevokedNamespace $finalDir");
    }
}
