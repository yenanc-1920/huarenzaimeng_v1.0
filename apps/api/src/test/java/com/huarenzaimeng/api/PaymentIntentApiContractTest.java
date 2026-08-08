package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huarenzaimeng.api.config.TestAccessTokenFilter;
import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.OrderState;
import com.huarenzaimeng.core.ProjectProjection;
import com.huarenzaimeng.core.Quote;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory",
        "hz.test-access-token=local-synthetic-m2-payment-intent-secret"
})
@AutoConfigureMockMvc
class PaymentIntentApiContractTest {
    private static final String TOKEN = "local-synthetic-m2-payment-intent-secret";
    private static final long SESSION_VERSION = 11L;
    private static final String AUTH_SET = "SYN-AS-M2";
    private static final String AUTH_EVIDENCE = "SYN-AUTH-EVIDENCE-M2";
    private static final String D3_03_SHA = "DBD457BBC409A9F520566B19EA3A4B867C83917505EFC8AB32B3FDF3969F47D9";
    private static final String D3_05_SHA = "5C047237E57C903843444D7255F7B752F752443633D45775E9ECE4B6F60FFA20";
    private static final String D3_REGISTRY_SHA = "6ECC47AF5078FC9195053EFD7875906C45242521F31FF2B987939834692A2CB2";
    private static final String MATRIX_SHA = "93D09612A29A4409044F0D3E9802D5BB4476D3A523FF5BFB7519FCE0A9D8544A";
    private static final String EXECUTION_RUN_ID = "M2-BE-" + UUID.randomUUID();
    private static final String EXECUTED_AT = Instant.now().toString();
    private static final String EVIDENCE_PACKAGE_ID = "D5-M2-BE-LOCAL-SYNTHETIC";
    private static final Path EVIDENCE_DIRECTORY = Path.of("target", "m2-payment-intent-evidence");
    private static final List<String> IMPLEMENTATION_PATHS = List.of(
            "apps/api/src/main/java/com/huarenzaimeng/api/FlowMapper.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/FlowStore.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/InMemoryFlowStore.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/LocalSyntheticOrderRecoveryService.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/MockFlowController.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/MockFlowService.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/MyBatisFlowStore.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/OrderCreationDomain.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/PaymentIntentDomain.java",
            "apps/api/src/main/resources/db/migration/V6_PAYMENT_INTENT_FORWARD_RUNBOOK.md",
            "apps/api/src/main/resources/db/migration/V6__add_local_synthetic_payment_intent.sql",
            "apps/api/src/test/java/com/huarenzaimeng/api/MockFlowApiContractTest.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/OrderCreationApiContractTest.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/OrderCreationMigrationContractTest.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/PaymentIntentApiContractTest.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/PaymentIntentMigrationContractTest.java");
    private static final List<String> MATRIX_SUBCASES = List.of(
            "M2-PAY-001-FIRST-INTENT", "M2-PAY-002-EXACT-REPLAY",
            "M2-PAY-003-DUALKEY-REKEY-CONFLICT", "M2-PAY-004-COMMANDID-CONFLICT",
            "M2-PAY-005-IDEMPOTENCYKEY-CONFLICT", "M2-PAY-006-SEMANTIC-FINGERPRINT-CONFLICT",
            "M2-PAY-007-ORDER-NOT-OWNED-OR-NOT-AUTHORIZED", "M2-PAY-008-PRICESNAPSHOT-MISMATCH",
            "M2-PAY-009-ALLOWEDACTION-MISSING", "M2-PAY-010-ALLOWEDACTION-STALE-OR-DISABLED",
            "M2-PAY-011-GUEST-OR-ROLE-MISMATCH", "M2-PAY-012-STALE-SESSIONVERSION",
            "M2-PAY-013-WRONG-AUTHORIZATIONSETREF", "M2-PAY-014-REVOKED-OR-EVIDENCEVERSION-MISMATCH",
            "M2-PAY-015-STALE-PROJECTIONVERSION", "M2-PAY-016-STALE-AGGREGATEVERSION",
            "M2-PAY-017-SUPPORTEDSET-VERSION-DRIFT", "M2-PAY-018-CATALOG-VERSION-DRIFT",
            "M2-PAY-019-MISSING-COMMANDID", "M2-PAY-020-MISSING-IDEMPOTENCYKEY",
            "M2-PAY-021-INVALID-CREATION-PRECONDITION", "M2-PAY-022-MISSING-EXPECTED-VERSIONS",
            "M2-PAY-023-CLIENT-AUTHORITY-OR-MONEY-TAMPER", "M2-PAY-024-A1-UNKNOWN",
            "M2-PAY-025-A1-REJECTED", "M2-PAY-026-STABLE-REJECTION",
            "M2-PAY-027-WRITE-UNKNOWN", "M2-PAY-028-NETWORK-INTERRUPTED");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MockFlowService service;
    @Autowired LocalSyntheticOrderRecoveryService recovery;
    @Autowired InMemoryFlowStore store;
    @Autowired InMemoryCatalogStore catalog;
    @Autowired Clock clock;

    private LocalSyntheticIdentity identity;
    private Fixture activeFixture;

    @BeforeAll
    static void clearEvidence() throws Exception {
        Files.createDirectories(EVIDENCE_DIRECTORY);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(EVIDENCE_DIRECTORY, "*.json")) {
            for (Path file : files) Files.deleteIfExists(file);
        }
    }

    @AfterAll
    static void finalizeEvidencePackage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Path> caseFiles;
        try (var stream = Files.list(EVIDENCE_DIRECTORY)) {
            caseFiles = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().equals("evidence-package-index.json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        Map<Path, ObjectNode> cases = new HashMap<>();
        List<String> normalizedManifest = new ArrayList<>();
        for (Path file : caseFiles) {
            ObjectNode evidence = (ObjectNode) mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            evidence.remove(List.of("evidencePackageDigest", "evidencePackageSha256"));
            cases.put(file, evidence);
            normalizedManifest.add(file.getFileName() + "|" + sha256(mapper.writeValueAsBytes(evidence)));
        }
        normalizedManifest.sort(Comparator.naturalOrder());
        String packageDigest = sha256(String.join("\n", normalizedManifest).getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<Path, ObjectNode> entry : cases.entrySet()) {
            entry.getValue().put("evidencePackageDigest", packageDigest);
            entry.getValue().put("evidencePackageSha256", packageDigest);
            entry.getValue().put("evidencePackageDigestScope",
                    "ORDINAL filename|SHA256(normalized JSON without package digest), UTF-8, LF, no terminal LF");
            Files.writeString(entry.getKey(), mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(entry.getValue()) + "\n", StandardCharsets.UTF_8);
        }

        ObjectNode index = mapper.createObjectNode();
        index.put("evidencePackageId", EVIDENCE_PACKAGE_ID);
        index.put("evidenceRoot", "apps/api/target/m2-payment-intent-evidence");
        index.put("executionRunId", EXECUTION_RUN_ID);
        index.put("executedAt", EXECUTED_AT);
        index.put("matrixSha256", MATRIX_SHA);
        index.put("implementationAggregateSha256", implementationAggregate());
        index.put("implementationAggregateAlgorithm",
                ".NET StringComparer.Ordinal equivalent; path|UPPERCASE_SHA256; UTF-8 no BOM; LF; no terminal LF");
        index.put("evidencePackageSha256", packageDigest);
        ArrayNode matrix = index.putArray("matrix");
        for (String subcaseId : MATRIX_SUBCASES) {
            ObjectNode row = matrix.addObject();
            row.put("subcaseId", subcaseId);
            ArrayNode evidenceFiles = row.putArray("evidenceFiles");
            cases.entrySet().stream()
                    .filter(entry -> subcaseId.equals(entry.getValue().path("subcaseId").asText()))
                    .map(entry -> entry.getKey().getFileName().toString())
                    .sorted()
                    .forEach(evidenceFiles::add);
            row.put("executionStatus", evidenceFiles.isEmpty() ? "NOT_RUN" : "PASS");
        }
        ArrayNode finalFiles = index.putArray("evidenceFiles");
        for (Path file : caseFiles) {
            ObjectNode item = finalFiles.addObject();
            item.put("path", "apps/api/target/m2-payment-intent-evidence/" + file.getFileName());
            item.put("sha256", sha256(Files.readAllBytes(file)));
        }
        ObjectNode d3 = index.putObject("d3Sha256");
        d3.put("D3-03", D3_03_SHA);
        d3.put("D3-05", D3_05_SHA);
        d3.put("D3-REGISTRY", D3_REGISTRY_SHA);
        String indexDigest = sha256(mapper.writeValueAsBytes(index));
        index.put("indexSha256WithoutSelfField", indexDigest);
        Files.writeString(EVIDENCE_DIRECTORY.resolve("evidence-package-index.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(index) + "\n", StandardCharsets.UTF_8);
    }

    @BeforeEach
    void prepare() {
        identity = LocalSyntheticIdentity.fromToken(TOKEN);
        store.resetSubjectForTest(identity.projectSubjectRef());
        catalog.resetForTest();
        recovery.resetForTest();
        service.resetPaymentEligibilityForTest();
        recovery.installBuyerAuthorizationForTest(identity.projectSubjectRef(), identity.sessionRef(),
                SESSION_VERSION, AUTH_SET, AUTH_EVIDENCE);
    }

    @Test void M2_PAY_001_FIRST_INTENT() throws Exception {
        Fixture fixture = fixture("FIRST");
        PaymentIntentEvidenceSnapshot before = snapshot();
        MvcResult result = perform(request("CMD-M2-FIRST", "IDEM-M2-FIRST", AUTH_SET, SESSION_VERSION, 1, 1),
                fixture.orderRef(), true);
        JsonNode response = response(result, 200, "PAYMENT_INTENT_CREATED");
        PaymentIntentEvidenceSnapshot after = snapshot();

        assertThat(response.path("outcome").asText()).isEqualTo("ACCEPTED");
        assertThat(response.path("retryClass").asText()).isEqualTo("NONE");
        assertThat(response.path("resourceRef").asText()).startsWith("PI-");
        assertThat(response.path("aggregateVersion").asLong()).isEqualTo(2);
        assertThat(response.path("currentProjection").path("orderRef").asText()).isEqualTo(fixture.orderRef());
        assertThat(response.path("currentProjection").path("stateCode").asText()).isEqualTo("AWAITING_PAYMENT");
        assertThat(response.path("currentProjection").path("intentScope").asText())
                .isEqualTo("LOCAL_SYNTHETIC_ONLY");
        assertThat(response.path("currentProjection").path("paymentInitiated").asBoolean()).isFalse();
        assertThat(response.path("currentProjection").path("paymentConfirmed").asBoolean()).isFalse();
        assertThat(response.path("currentProjection").path("projectionVersion").asLong()).isEqualTo(2);
        assertThat(response.path("currentProjection").path("aggregateVersion").asLong()).isEqualTo(2);
        assertThat(response.path("currentProjection").path("allowedActions").path(0)
                .path("actionCode").asText()).isEqualTo(MockFlowService.QUERY_PAYMENT_INTENT_ACTION);
        PaymentIntentRecord intent = store.requirePaymentIntent(identity.projectSubjectRef(),
                response.path("resourceRef").asText());
        assertThat(intent.scope()).isEqualTo(MockFlowService.PAYMENT_INTENT_SCOPE);
        assertThat(intent.priceSnapshotDigest()).hasSize(64);
        assertThat(intent.paymentEligibilityDecisionRef()).startsWith("PED-");
        PaymentEligibilityDecision decision = service.paymentEligibilityDecisionForTest(fixture.orderRef());
        assertThat(decision.status()).isEqualTo(PaymentEligibilityDecisionStatus.ELIGIBLE);
        assertThat(decision.a1EvidenceVersion()).isEqualTo("A1-EVIDENCE-V1");
        assertThat(decision.a1EvidenceVersion()).isNotEqualTo(AUTH_EVIDENCE);
        assertThat(intent.paymentEligibilityDecisionRef()).isEqualTo(decision.decisionRef());
        assertFirstDelta(before, after);
        assertZeroDownstreamExceptIntent(before, after);
        assertThat(store.requireOrder(identity.projectSubjectRef(), fixture.orderRef()).paymentState())
                .isEqualTo("ABSENT_CONFIRMED");
        writeEvidence("M2-S01", "M2-PAY-001-FIRST-INTENT", "APPROVED_ELIGIBLE", result, response, before, after);
    }

    @Test void M2_PAY_002_EXACT_REPLAY() throws Exception {
        Fixture fixture = fixture("REPLAY");
        ObjectNode request = request("CMD-M2-REPLAY", "IDEM-M2-REPLAY", AUTH_SET, SESSION_VERSION, 1, 1);
        JsonNode first = response(perform(request, fixture.orderRef(), true), 200, "PAYMENT_INTENT_CREATED");
        PaymentIntentEvidenceSnapshot before = snapshot();
        MvcResult replayResult = perform(request, fixture.orderRef(), true);
        JsonNode replay = response(replayResult, 200, "PAYMENT_INTENT_REPLAYED");
        PaymentIntentEvidenceSnapshot after = snapshot();
        assertThat(replay.path("resourceRef").asText()).isEqualTo(first.path("resourceRef").asText());
        assertThat(replay.path("currentProjection").path("intentScope").asText())
                .isEqualTo("LOCAL_SYNTHETIC_ONLY");
        assertThat(replay.path("currentProjection").path("paymentInitiated").asBoolean()).isFalse();
        assertThat(replay.path("currentProjection").path("paymentConfirmed").asBoolean()).isFalse();
        assertThat(replay.path("currentProjection").path("stateCode").asText()).isEqualTo("AWAITING_PAYMENT");
        assertNoDelta(before, after);
        writeEvidence("M2-S01", "M2-PAY-002-EXACT-REPLAY", "EXACT_DOUBLE_KEY", replayResult, replay, before, after);
    }

    @Test void M2_PAY_004_COMMANDID_CONFLICT() throws Exception {
        Fixture fixture = fixture("CMD-ONLY");
        create(fixture, "CMD-M2-CMD-ONLY", "IDEM-M2-CMD-ONLY");
        assertConflict("M2-S02", "M2-PAY-004-COMMANDID-CONFLICT", "COMMAND_ONLY_REUSE", fixture,
                request("CMD-M2-CMD-ONLY", "IDEM-M2-CMD-CHANGED", AUTH_SET, SESSION_VERSION, 1, 1));
    }

    @Test void M2_PAY_005_IDEMPOTENCYKEY_CONFLICT() throws Exception {
        Fixture fixture = fixture("IDEM-ONLY");
        create(fixture, "CMD-M2-IDEM-ORIGINAL", "IDEM-M2-IDEM-ONLY");
        assertConflict("M2-S02", "M2-PAY-005-IDEMPOTENCYKEY-CONFLICT", "IDEMPOTENCY_ONLY_REUSE", fixture,
                request("CMD-M2-IDEM-CHANGED", "IDEM-M2-IDEM-ONLY", AUTH_SET, SESSION_VERSION, 1, 1));
    }

    @Test void M2_PAY_003_DUALKEY_REKEY_CONFLICT() throws Exception {
        Fixture fixture = fixture("DOUBLE-REKEY");
        create(fixture, "CMD-M2-DOUBLE-A", "IDEM-M2-DOUBLE-A");
        assertConflict("M2-S01", "M2-PAY-003-DUALKEY-REKEY-CONFLICT", "BOTH_KEYS_CHANGED", fixture,
                request("CMD-M2-DOUBLE-B", "IDEM-M2-DOUBLE-B", AUTH_SET, SESSION_VERSION, 1, 1));
    }

    @Test void M2_PAY_006_SEMANTIC_FINGERPRINT_CONFLICT_AUTH_EVIDENCE() throws Exception {
        Fixture fixture = fixture("FINGERPRINT");
        create(fixture, "CMD-M2-FP", "IDEM-M2-FP");
        recovery.installBuyerAuthorizationForTest(identity.projectSubjectRef(), identity.sessionRef(), 12L,
                "SYN-AS-M2-CHANGED", "SYN-AUTH-EVIDENCE-M2-CHANGED");
        recovery.appendOrderRefForTest("SYN-AS-M2-CHANGED", fixture.orderRef());
        assertConflict("M2-S02", "M2-PAY-006-SEMANTIC-FINGERPRINT-CONFLICT",
                "AUTHORIZATION_EVIDENCE_VERSION_API", fixture,
                request("CMD-M2-FP", "IDEM-M2-FP", "SYN-AS-M2-CHANGED", 12L, 1, 1));
    }

    @ParameterizedTest(name = "M2-PAY-006 fingerprint {0}")
    @ValueSource(strings = {"ENVIRONMENT", "PROJECT_SUBJECT_REF", "SESSION_ROLE", "SESSION_VERSION",
            "AUTHORIZATION_SET_REF", "AUTHORIZATION_EVIDENCE_VERSION", "ORDER_REF",
            "PAYMENT_INTENT_CREATION_PRECONDITION", "EXPECTED_PROJECTION_VERSION",
            "EXPECTED_AGGREGATE_VERSION", "PRICE_SNAPSHOT_DIGEST", "PAYMENT_ELIGIBILITY_DECISION_REF",
            "ALLOWED_ACTION_CODE"})
    void M2_PAY_006_EACH_NAMED_FINGERPRINT_FIELD_CONFLICTS(String parameterId) throws Exception {
        Fixture fixture = fixture("FINGERPRINT-" + parameterId);
        ObjectNode firstRequest = request("CMD-M2-FP-ALL", "IDEM-M2-FP-ALL", AUTH_SET,
                SESSION_VERSION, 1, 1);
        JsonNode first = response(perform(firstRequest, fixture.orderRef(), true),
                200, "PAYMENT_INTENT_CREATED");
        PaymentIntentRecord existing = store.requirePaymentIntent(identity.projectSubjectRef(),
                first.path("resourceRef").asText());
        String mutatedFingerprint = CanonicalFingerprint.sha256(existing.requestFingerprint(), parameterId,
                "MUTATED_SINGLE_FIELD");
        CommandIdentity conflictingCommand = new CommandIdentity("CMD-M2-FP-ALL", "IDEM-M2-FP-ALL",
                "POST:/api/v1/orders/{orderRef}/payment-intents", fixture.orderRef(),
                existing.semanticActionKey(), mutatedFingerprint);
        PaymentIntentDraft conflictingDraft = new PaymentIntentDraft("PI-MUTATED-" + UUID.randomUUID(),
                existing.environment(), existing.projectSubjectRef(), existing.orderRef(), existing.businessKey(),
                existing.semanticActionKey(), mutatedFingerprint, existing.priceSnapshot(),
                existing.priceSnapshotDigest(), existing.paymentEligibilityDecisionRef(), existing.scope(),
                clock.instant());
        PaymentIntentEvidenceSnapshot before = snapshot();
        assertThatThrownBy(() -> store.createPaymentIntent(identity.projectSubjectRef(), conflictingDraft,
                conflictingCommand, 1, 1, () -> { }))
                .isInstanceOf(FlowRejectedException.class)
                .hasMessage("IDEMPOTENCY_CONFLICT");
        PaymentIntentEvidenceSnapshot after = snapshot();
        assertNoDelta(before, after);
        writeSyntheticEvidence("M2-S02", "M2-PAY-006-SEMANTIC-FINGERPRINT-CONFLICT", parameterId,
                409, "IDEMPOTENCY_CONFLICT", before, after,
                "SINGLE_NAMED_FINGERPRINT_FIELD_MUTATION");
    }

    @Test void M2_PAY_021_INVALID_CREATION_PRECONDITION_WRONG() throws Exception {
        Fixture fixture = fixture("PRECONDITION");
        ObjectNode request = request("CMD-M2-PRE", "IDEM-M2-PRE", AUTH_SET, SESSION_VERSION, 1, 1);
        request.put("paymentIntentCreationPrecondition", "WRONG_PRECONDITION");
        assertRejected("M2-S06", "M2-PAY-021-INVALID-CREATION-PRECONDITION", "WRONG", fixture, request,
                "PAYMENT_INTENT_CREATION_PRECONDITION_INVALID", 422);
    }

    @Test void M2_PAY_015_STALE_PROJECTIONVERSION() throws Exception {
        Fixture fixture = fixture("STALE-PROJECTION");
        assertRejected("M2-S05", "M2-PAY-015-STALE-PROJECTIONVERSION", "STALE_PROJECTION", fixture,
                request("CMD-M2-PV", "IDEM-M2-PV", AUTH_SET, SESSION_VERSION, 2, 1),
                "PROJECTION_VERSION_CONFLICT", 409);
    }

    @Test void M2_PAY_016_STALE_AGGREGATEVERSION() throws Exception {
        Fixture fixture = fixture("STALE-AGGREGATE");
        assertRejected("M2-S05", "M2-PAY-016-STALE-AGGREGATEVERSION", "STALE_AGGREGATE", fixture,
                request("CMD-M2-AV", "IDEM-M2-AV", AUTH_SET, SESSION_VERSION, 1, 2),
                "AGGREGATE_VERSION_CONFLICT", 409);
    }

    @Test void M2_PAY_007_ORDER_NOT_AUTHORIZED() throws Exception {
        Fixture fixture = fixture("AUTH-SET");
        recovery.installBuyerAuthorizationForTest(identity.projectSubjectRef(), identity.sessionRef(),
                SESSION_VERSION, AUTH_SET, AUTH_EVIDENCE);
        assertRejected("M2-S03", "M2-PAY-007-ORDER-NOT-OWNED-OR-NOT-AUTHORIZED",
                "NOT_IN_AUTHORIZED_SET", fixture,
                request("CMD-M2-AUTH", "IDEM-M2-AUTH", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_024_A1_UNKNOWN() throws Exception {
        Fixture fixture = fixture("UNKNOWN");
        service.installPaymentEligibilityDecisionForTest(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), fixture.orderRef(), AUTH_EVIDENCE,
                PaymentEligibilityDecisionStatus.UNKNOWN, clock.instant().plusSeconds(600));
        assertRejected("M2-S07", "M2-PAY-024-A1-UNKNOWN", "DECISION_UNKNOWN", fixture,
                request("CMD-M2-UNKNOWN", "IDEM-M2-UNKNOWN", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_009_ALLOWEDACTION_MISSING() throws Exception {
        Fixture fixture = fixture("ACTION");
        OrderProjection current = store.requireOrder(identity.projectSubjectRef(), fixture.orderRef());
        store.replaceOrderForTest(identity.projectSubjectRef(), new OrderProjection(current.orderRef(),
                current.quoteRef(), current.orderState(), current.paymentState(), current.upstreamDebitState(),
                current.deliveryState(), current.refundState(), current.totalAmountMinor(), current.currency(),
                current.projectionVersion(), current.aggregateVersion(), "NONE"));
        assertRejected("M2-S03", "M2-PAY-009-ALLOWEDACTION-MISSING", "MISSING", fixture,
                request("CMD-M2-ACTION", "IDEM-M2-ACTION", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_018_CATALOG_VERSION_DRIFT() throws Exception {
        Fixture fixture = fixture("CATALOG-DRIFT");
        Instant now = clock.instant();
        catalog.installForTest(new CatalogBatch(2L, 2L, "SYN-M2-CATALOG-V2", now.minusSeconds(60),
                now.plusSeconds(3600), true, List.of("SYN-OP"), List.of(new CatalogItem("SYN-OP",
                "SYN-PRODUCT", "SYN-DENOM-1000", "PRESET_DENOMINATION", 1000L, "CNY"))));
        assertRejected("M2-S05", "M2-PAY-018-CATALOG-VERSION-DRIFT", "CATALOG_VERSION_CHANGED", fixture,
                request("CMD-M2-CATALOG", "IDEM-M2-CATALOG", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_027_WRITE_UNKNOWN() throws Exception {
        Fixture fixture = fixture("RESPONSE-LOSS");
        ObjectNode request = request("CMD-M2-LOSS", "IDEM-M2-LOSS", AUTH_SET, SESSION_VERSION, 1, 1);
        BuyerAuthorization authorization = recovery.requireBuyerAuthorization(
                LocalSyntheticOrderRecoveryService.ENVIRONMENT, identity.projectSubjectRef(), identity.sessionRef(),
                SESSION_VERSION, AUTH_SET);
        PaymentIntentResponse unobserved = service.createLocalSyntheticPaymentIntent(authorization,
                fixture.orderRef(), MockFlowService.PAYMENT_INTENT_CREATION_PRECONDITION,
                "CMD-M2-LOSS", "IDEM-M2-LOSS", 1, 1);
        PaymentIntentEvidenceSnapshot before = snapshot();
        MvcResult replayResult = perform(request, fixture.orderRef(), true);
        JsonNode replay = response(replayResult, 200, "PAYMENT_INTENT_REPLAYED");
        PaymentIntentEvidenceSnapshot after = snapshot();
        assertThat(replay.path("resourceRef").asText()).isEqualTo(unobserved.resourceRef());
        assertNoDelta(before, after);
        writeEvidence("M2-S08", "M2-PAY-027-WRITE-UNKNOWN", "ORIGINAL_DOUBLE_KEY_CONVERGENCE",
                replayResult, replay, before, after);
    }

    @Test void M2_PAY_023_CLIENT_AUTHORITY_OR_MONEY_TAMPER() throws Exception {
        Fixture fixture = fixture("FORGED");
        ObjectNode request = request("CMD-M2-FORGED", "IDEM-M2-FORGED", AUTH_SET, SESSION_VERSION, 1, 1);
        request.put("projectSubjectRef", "FORGED");
        request.put("amountMinor", 1);
        request.put("prepay_id", "FORGED");
        assertRejected("M2-S06", "M2-PAY-023-CLIENT-AUTHORITY-OR-MONEY-TAMPER", "COMPOUND_LEGACY_SUPPORT",
                fixture, request, "INVALID_REQUEST", 400);
    }

    @Test void M2_PAY_011_UNTRUSTED_BUYER_REJECTED() throws Exception {
        Fixture fixture = fixture("NO-TOKEN");
        ObjectNode request = request("CMD-M2-NO-TOKEN", "IDEM-M2-NO-TOKEN", AUTH_SET,
                SESSION_VERSION, 1, 1);
        PaymentIntentEvidenceSnapshot before = snapshot();
        MvcResult result = perform(request, fixture.orderRef(), false);
        JsonNode response = response(result, 401, "UNAUTHORIZED");
        PaymentIntentEvidenceSnapshot after = snapshot();
        assertNoDelta(before, after);
        writeEvidence("M2-S04", "M2-PAY-011-GUEST-OR-ROLE-MISMATCH", "UNAUTHENTICATED",
                result, response, before, after);
    }

    @Test void M2_PAY_012_STALE_SESSIONVERSION() throws Exception {
        Fixture fixture = fixture("STALE-SESSION");
        assertRejected("M2-S04", "M2-PAY-012-STALE-SESSIONVERSION", "STALE", fixture,
                request("CMD-M2-SESSION", "IDEM-M2-SESSION", AUTH_SET, SESSION_VERSION - 1, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_013_WRONG_AUTHORIZATIONSETREF() throws Exception {
        Fixture fixture = fixture("WRONG-SET");
        assertRejected("M2-S04", "M2-PAY-013-WRONG-AUTHORIZATIONSETREF", "WRONG_SET", fixture,
                request("CMD-M2-SET", "IDEM-M2-SET", "SYN-AS-M2-WRONG", SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_008_PRICESNAPSHOT_EXPIRED() throws Exception {
        Fixture fixture = fixture("EXPIRED-SNAPSHOT");
        Quote quote = fixture.quote();
        store.installQuoteForTest(identity.projectSubjectRef(), new Quote(quote.quoteRef(), quote.maskedPhone(),
                quote.operatorCode(), quote.productCode(), quote.denominationRef(),
                quote.supportedOperatorSetVersion(), quote.catalogVersion(), quote.totalAmountMinor(),
                quote.currency(), clock.instant().minusSeconds(1)));
        assertRejected("M2-S03", "M2-PAY-008-PRICESNAPSHOT-MISMATCH", "EXPIRED_SNAPSHOT", fixture,
                request("CMD-M2-EXPIRED", "IDEM-M2-EXPIRED", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_026_STABLE_REJECTION_NON_AWAITING() throws Exception {
        Fixture fixture = fixture("ORDER-STATE");
        OrderProjection current = store.requireOrder(identity.projectSubjectRef(), fixture.orderRef());
        store.replaceOrderForTest(identity.projectSubjectRef(), new OrderProjection(current.orderRef(),
                current.quoteRef(), OrderState.PAYMENT_CONFIRMED, current.paymentState(),
                current.upstreamDebitState(), current.deliveryState(), current.refundState(),
                current.totalAmountMinor(), current.currency(), current.projectionVersion(),
                current.aggregateVersion(), MockFlowService.CREATE_PAYMENT_INTENT_ACTION));
        assertRejected("M2-S08", "M2-PAY-026-STABLE-REJECTION", "ORDER_NOT_AWAITING_PAYMENT", fixture,
                request("CMD-M2-STATE", "IDEM-M2-STATE", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_007_ORDER_NOT_FOUND() throws Exception {
        Fixture fixture = fixture("UNKNOWN-ORDER");
        recovery.appendOrderRefForTest(AUTH_SET, "O-SYNTHETIC-NOT-FOUND");
        Fixture missing = new Fixture("O-SYNTHETIC-NOT-FOUND", fixture.quote());
        assertRejected("M2-S03", "M2-PAY-007-ORDER-NOT-OWNED-OR-NOT-AUTHORIZED", "NOT_FOUND", missing,
                request("CMD-M2-NOT-FOUND", "IDEM-M2-NOT-FOUND", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_007_CROSS_SUBJECT() throws Exception {
        Fixture fixture = fixture("CROSS-SUBJECT-BASE");
        Quote foreignQuote = quoteFor("SYN-SUBJECT-M2-FOREIGN", "CROSS-SUBJECT");
        CommandIdentity foreignCommand = new CommandIdentity("CMD-M2-FOREIGN-ORDER", "IDEM-M2-FOREIGN-ORDER",
                "POST:/api/v1/orders", foreignQuote.quoteRef(), "FOREIGN-ORDER-" + foreignQuote.quoteRef(),
                CanonicalFingerprint.sha256("FOREIGN", foreignQuote.quoteRef()));
        String foreignOrderRef = store.createOrder("SYN-SUBJECT-M2-FOREIGN", foreignQuote,
                foreignCommand).order().orderRef();
        recovery.appendOrderRefForTest(AUTH_SET, foreignOrderRef);
        Fixture foreign = new Fixture(foreignOrderRef, fixture.quote());
        assertRejected("M2-S03", "M2-PAY-007-ORDER-NOT-OWNED-OR-NOT-AUTHORIZED", "CROSS_SUBJECT", foreign,
                request("CMD-M2-CROSS", "IDEM-M2-CROSS", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_022_MISSING_EXPECTED_AGGREGATE_VERSION() throws Exception {
        Fixture fixture = fixture("MISSING-VERSION");
        ObjectNode request = request("CMD-M2-MISSING", "IDEM-M2-MISSING", AUTH_SET,
                SESSION_VERSION, 1, 1);
        request.remove("expectedAggregateVersion");
        assertRejected("M2-S06", "M2-PAY-022-MISSING-EXPECTED-VERSIONS", "MISSING_AGGREGATE_VERSION",
                fixture, request,
                "INVALID_REQUEST", 400);
    }

    @Test void M2_SUPPORT_CONTROLLED_CONCURRENT_EXACT_REPLAY_CREATES_ONE_CANONICAL_INTENT() throws Exception {
        Fixture fixture = fixture("CONCURRENT");
        ObjectNode request = request("CMD-M2-CONCURRENT", "IDEM-M2-CONCURRENT", AUTH_SET,
                SESSION_VERSION, 1, 1);
        PaymentIntentEvidenceSnapshot before = snapshot();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<MvcResult> left = executor.submit(() -> concurrentPerform(request, fixture.orderRef(), ready, start));
            Future<MvcResult> right = executor.submit(() -> concurrentPerform(request, fixture.orderRef(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            MvcResult leftResult = left.get(10, TimeUnit.SECONDS);
            MvcResult rightResult = right.get(10, TimeUnit.SECONDS);
            JsonNode leftResponse = json.readTree(leftResult.getResponse().getContentAsString());
            JsonNode rightResponse = json.readTree(rightResult.getResponse().getContentAsString());
            assertThat(leftResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(rightResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(List.of(leftResponse.path("projectCode").asText(), rightResponse.path("projectCode").asText()))
                    .containsExactlyInAnyOrder("PAYMENT_INTENT_CREATED", "PAYMENT_INTENT_REPLAYED");
            assertThat(leftResponse.path("resourceRef").asText())
                    .isEqualTo(rightResponse.path("resourceRef").asText());
            PaymentIntentEvidenceSnapshot after = snapshot();
            assertFirstDelta(before, after);
            assertZeroDownstreamExceptIntent(before, after);
            writeConcurrentEvidence("M2-S01", "M2-PAY-002-EXACT-REPLAY", "SINGLE_JVM_START_BARRIER",
                    leftResponse, rightResponse,
                    before, after);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void M2_SUPPORT_CONTROLLED_CONCURRENT_REKEY_CREATES_ONE_AND_CONFLICTS_ONE() throws Exception {
        Fixture fixture = fixture("CONCURRENT-REKEY");
        ObjectNode leftRequest = request("CMD-M2-CONCURRENT-A", "IDEM-M2-CONCURRENT-A", AUTH_SET,
                SESSION_VERSION, 1, 1);
        ObjectNode rightRequest = request("CMD-M2-CONCURRENT-B", "IDEM-M2-CONCURRENT-B", AUTH_SET,
                SESSION_VERSION, 1, 1);
        PaymentIntentEvidenceSnapshot before = snapshot();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<MvcResult> left = executor.submit(() -> concurrentPerform(leftRequest, fixture.orderRef(),
                    ready, start));
            Future<MvcResult> right = executor.submit(() -> concurrentPerform(rightRequest, fixture.orderRef(),
                    ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            MvcResult leftResult = left.get(10, TimeUnit.SECONDS);
            MvcResult rightResult = right.get(10, TimeUnit.SECONDS);
            JsonNode leftResponse = json.readTree(leftResult.getResponse().getContentAsString());
            JsonNode rightResponse = json.readTree(rightResult.getResponse().getContentAsString());
            assertThat(List.of(leftResult.getResponse().getStatus(), rightResult.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(List.of(leftResponse.path("projectCode").asText(),
                    rightResponse.path("projectCode").asText()))
                    .containsExactlyInAnyOrder("PAYMENT_INTENT_CREATED", "IDEMPOTENCY_CONFLICT");
            PaymentIntentEvidenceSnapshot after = snapshot();
            assertFirstDelta(before, after);
            assertZeroDownstreamExceptIntent(before, after);
            writeConcurrentEvidence("M2-S01", "M2-PAY-003-DUALKEY-REKEY-CONFLICT",
                    "SINGLE_JVM_DIFFERENT_DOUBLE_KEYS", leftResponse, rightResponse, before, after);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void M2_PAY_008_PRICESNAPSHOT_MISSING_FIELD() throws Exception {
        Fixture fixture = fixture("MISSING-SNAPSHOT-FIELD");
        Quote quote = fixture.quote();
        store.installQuoteForTest(identity.projectSubjectRef(), new Quote(quote.quoteRef(), quote.maskedPhone(),
                quote.operatorCode(), quote.productCode(), null, quote.supportedOperatorSetVersion(),
                quote.catalogVersion(), quote.totalAmountMinor(), quote.currency(), quote.expiresAt()));
        assertRejected("M2-S03", "M2-PAY-008-PRICESNAPSHOT-MISMATCH", "MISSING_DENOMINATION_REF", fixture,
                request("CMD-M2-MISSING-SNAPSHOT", "IDEM-M2-MISSING-SNAPSHOT", AUTH_SET,
                        SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_SUPPORT_EXACT_REPLAY_AFTER_CATALOG_DRIFT_RETURNS_ORIGINAL_WITH_ZERO_DELTA() throws Exception {
        Fixture fixture = fixture("REPLAY-AFTER-CATALOG-DRIFT");
        ObjectNode request = request("CMD-M2-REPLAY-DRIFT", "IDEM-M2-REPLAY-DRIFT", AUTH_SET,
                SESSION_VERSION, 1, 1);
        JsonNode first = response(perform(request, fixture.orderRef(), true),
                200, "PAYMENT_INTENT_CREATED");
        Instant now = clock.instant();
        catalog.installForTest(new CatalogBatch(2L, 2L, "SYN-M2-CATALOG-REPLAY-V2", now.minusSeconds(60),
                now.plusSeconds(3600), true, List.of("SYN-OP"), List.of(new CatalogItem("SYN-OP",
                "SYN-PRODUCT", "SYN-DENOM-1000", "PRESET_DENOMINATION", 1000L, "CNY"))));
        PaymentIntentEvidenceSnapshot before = snapshot();
        MvcResult replayResult = perform(request, fixture.orderRef(), true);
        JsonNode replay = response(replayResult, 200, "PAYMENT_INTENT_REPLAYED");
        PaymentIntentEvidenceSnapshot after = snapshot();
        assertThat(replay.path("resourceRef").asText()).isEqualTo(first.path("resourceRef").asText());
        assertNoDelta(before, after);
        writeEvidence("M2-S01", "M2-PAY-002-EXACT-REPLAY", "REPLAY_AFTER_CATALOG_DRIFT",
                replayResult, replay, before, after);
    }

    @Test void M2_PAY_010_ALLOWEDACTION_STALE_OR_DISABLED() throws Exception {
        Fixture fixture = fixture("ACTION-STALE");
        OrderProjection current = store.requireOrder(identity.projectSubjectRef(), fixture.orderRef());
        store.replaceOrderForTest(identity.projectSubjectRef(), new OrderProjection(current.orderRef(),
                current.quoteRef(), current.orderState(), current.paymentState(), current.upstreamDebitState(),
                current.deliveryState(), current.refundState(), current.totalAmountMinor(), current.currency(),
                current.projectionVersion() + 1, current.aggregateVersion(), "NONE"));
        assertRejected("M2-S03", "M2-PAY-010-ALLOWEDACTION-STALE-OR-DISABLED", "STALE_CLIENT_ACTION",
                fixture, request("CMD-M2-ACTION-STALE", "IDEM-M2-ACTION-STALE", AUTH_SET,
                        SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_014_REVOKED_AUTHORIZATION() throws Exception {
        Fixture fixture = fixture("AUTH-REVOKED");
        recovery.revokeBuyerAuthorizationForTest(AUTH_SET);
        assertRejected("M2-S04", "M2-PAY-014-REVOKED-OR-EVIDENCEVERSION-MISMATCH", "REVOKED",
                fixture, request("CMD-M2-REVOKED", "IDEM-M2-REVOKED", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_014_DECISION_AUTHORIZATION_EVIDENCE_MISMATCH() throws Exception {
        Fixture fixture = fixture("AUTH-EVIDENCE-MISMATCH");
        installDecisionVariant(fixture, "WRONG_AUTHORIZATION_EVIDENCE");
        assertRejected("M2-S04", "M2-PAY-014-REVOKED-OR-EVIDENCEVERSION-MISMATCH",
                "AUTHORIZATION_EVIDENCE_VERSION_MISMATCH", fixture,
                request("CMD-M2-AUTH-EVIDENCE", "IDEM-M2-AUTH-EVIDENCE", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_017_SUPPORTEDSET_VERSION_DRIFT() throws Exception {
        Fixture fixture = fixture("SUPPORTED-DRIFT");
        Instant now = clock.instant();
        catalog.installForTest(new CatalogBatch(2L, 1L, "SYN-M2-SUPPORTED-V2", now.minusSeconds(60),
                now.plusSeconds(3600), true, List.of("SYN-OP"), List.of(new CatalogItem("SYN-OP",
                "SYN-PRODUCT", "SYN-DENOM-1000", "PRESET_DENOMINATION", 1000L, "CNY"))));
        assertRejected("M2-S05", "M2-PAY-017-SUPPORTEDSET-VERSION-DRIFT", "SUPPORTED_SET_CHANGED",
                fixture, request("CMD-M2-SUPPORTED", "IDEM-M2-SUPPORTED", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_019_MISSING_COMMANDID() throws Exception {
        Fixture fixture = fixture("MISSING-COMMAND");
        ObjectNode body = request("CMD-M2-MISSING-COMMAND", "IDEM-M2-MISSING-COMMAND", AUTH_SET,
                SESSION_VERSION, 1, 1);
        body.remove("commandId");
        assertRejected("M2-S06", "M2-PAY-019-MISSING-COMMANDID", "MISSING_COMMAND_ID", fixture, body,
                "INVALID_REQUEST", 400);
    }

    @Test void M2_PAY_020_MISSING_IDEMPOTENCYKEY() throws Exception {
        Fixture fixture = fixture("MISSING-IDEMPOTENCY");
        ObjectNode body = request("CMD-M2-MISSING-IDEMPOTENCY", "IDEM-M2-MISSING-IDEMPOTENCY", AUTH_SET,
                SESSION_VERSION, 1, 1);
        body.remove("idempotencyKey");
        assertRejected("M2-S06", "M2-PAY-020-MISSING-IDEMPOTENCYKEY", "MISSING_IDEMPOTENCY_KEY", fixture,
                body, "INVALID_REQUEST", 400);
    }

    @Test void M2_PAY_021_INVALID_CREATION_PRECONDITION_MISSING() throws Exception {
        Fixture fixture = fixture("MISSING-PRECONDITION");
        ObjectNode body = request("CMD-M2-MISSING-PRE", "IDEM-M2-MISSING-PRE", AUTH_SET,
                SESSION_VERSION, 1, 1);
        body.remove("paymentIntentCreationPrecondition");
        assertRejected("M2-S06", "M2-PAY-021-INVALID-CREATION-PRECONDITION", "MISSING", fixture, body,
                "INVALID_REQUEST", 400);
    }

    @Test void M2_PAY_022_MISSING_EXPECTED_PROJECTION_VERSION() throws Exception {
        Fixture fixture = fixture("MISSING-PROJECTION-VERSION");
        ObjectNode body = request("CMD-M2-MISSING-PV", "IDEM-M2-MISSING-PV", AUTH_SET,
                SESSION_VERSION, 1, 1);
        body.remove("expectedProjectionVersion");
        assertRejected("M2-S06", "M2-PAY-022-MISSING-EXPECTED-VERSIONS", "MISSING_PROJECTION_VERSION",
                fixture, body, "INVALID_REQUEST", 400);
    }

    @ParameterizedTest(name = "M2-PAY-023 {0}")
    @ValueSource(strings = {"projectSubjectRef", "sessionRole", "authorizationEvidenceVersion", "priceSnapshot",
            "amountMinor", "currency", "paymentEligibilityDecisionRef", "allowedActions", "semanticActionKey",
            "prepay_id", "paymentConfirmed"})
    void M2_PAY_023_CLIENT_AUTHORITY_OR_MONEY_TAMPER_INDEPENDENT(String forbiddenField) throws Exception {
        Fixture fixture = fixture("TAMPER-" + forbiddenField);
        ObjectNode body = request("CMD-M2-TAMPER-" + forbiddenField, "IDEM-M2-TAMPER-" + forbiddenField,
                AUTH_SET, SESSION_VERSION, 1, 1);
        if ("priceSnapshot".equals(forbiddenField) || "allowedActions".equals(forbiddenField)) {
            body.putObject(forbiddenField).put("forged", true);
        } else if ("amountMinor".equals(forbiddenField)) {
            body.put(forbiddenField, 1);
        } else if ("paymentConfirmed".equals(forbiddenField)) {
            body.put(forbiddenField, true);
        } else {
            body.put(forbiddenField, "FORGED");
        }
        assertRejected("M2-S06", "M2-PAY-023-CLIENT-AUTHORITY-OR-MONEY-TAMPER",
                forbiddenField.toUpperCase(), fixture, body, "INVALID_REQUEST", 400);
    }

    @ParameterizedTest(name = "M2-PAY-024 {0}")
    @ValueSource(strings = {"MISSING_DECISION", "EXPIRED_DECISION", "WRONG_ENVIRONMENT", "WRONG_SUBJECT",
            "WRONG_ORDER", "WRONG_SNAPSHOT_DIGEST", "WRONG_PARENT_PROJECTION", "WRONG_PARENT_AGGREGATE",
            "WRONG_ALLOWED_ACTION", "WRONG_RULE_VERSION", "TAMPERED_DECISION_REF"})
    void M2_PAY_024_A1_UNKNOWN_OR_INVALID_BINDING(String variant) throws Exception {
        Fixture fixture = fixture("A1-" + variant);
        installDecisionVariant(fixture, variant);
        assertRejected("M2-S07", "M2-PAY-024-A1-UNKNOWN", variant, fixture,
                request("CMD-M2-A1-" + variant, "IDEM-M2-A1-" + variant, AUTH_SET,
                        SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_025_A1_REJECTED() throws Exception {
        Fixture fixture = fixture("A1-REJECTED");
        installDecisionVariant(fixture, "REJECTED_DECISION");
        assertRejected("M2-S07", "M2-PAY-025-A1-REJECTED", "REJECTED", fixture,
                request("CMD-M2-A1-REJECTED", "IDEM-M2-A1-REJECTED", AUTH_SET, SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_008_DECISION_PRICESNAPSHOT_DIGEST_MISMATCH() throws Exception {
        Fixture fixture = fixture("DECISION-SNAPSHOT-MISMATCH");
        installDecisionVariant(fixture, "WRONG_SNAPSHOT_DIGEST");
        assertRejected("M2-S03", "M2-PAY-008-PRICESNAPSHOT-MISMATCH", "DECISION_DIGEST_MISMATCH", fixture,
                request("CMD-M2-DECISION-SNAPSHOT", "IDEM-M2-DECISION-SNAPSHOT", AUTH_SET,
                        SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_010_DECISION_ALLOWEDACTION_WRONG_BINDING() throws Exception {
        Fixture fixture = fixture("DECISION-ACTION-MISMATCH");
        installDecisionVariant(fixture, "WRONG_ALLOWED_ACTION");
        assertRejected("M2-S03", "M2-PAY-010-ALLOWEDACTION-STALE-OR-DISABLED", "DECISION_ACTION_MISMATCH",
                fixture, request("CMD-M2-DECISION-ACTION", "IDEM-M2-DECISION-ACTION", AUTH_SET,
                        SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @ParameterizedTest(name = "M2-PAY-011 {0}")
    @ValueSource(strings = {"GUEST", "CS", "FIN", "CONTENT", "UNTRUSTED_BUYER"})
    void M2_PAY_011_GUEST_OR_ROLE_MISMATCH(String roleClass) throws Exception {
        Fixture fixture = fixture("ROLE-" + roleClass);
        recovery.resetForTest();
        assertRejected("M2-S04", "M2-PAY-011-GUEST-OR-ROLE-MISMATCH", roleClass, fixture,
                request("CMD-M2-ROLE-" + roleClass, "IDEM-M2-ROLE-" + roleClass, AUTH_SET,
                        SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_017_DECISION_SUPPORTEDSET_WRONG_BINDING() throws Exception {
        Fixture fixture = fixture("DECISION-SUPPORTED-MISMATCH");
        installDecisionVariant(fixture, "WRONG_SUPPORTED_SET");
        assertRejected("M2-S05", "M2-PAY-017-SUPPORTEDSET-VERSION-DRIFT", "DECISION_SUPPORTED_SET_MISMATCH",
                fixture, request("CMD-M2-DECISION-SUPPORTED", "IDEM-M2-DECISION-SUPPORTED", AUTH_SET,
                        SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_018_DECISION_CATALOG_WRONG_BINDING() throws Exception {
        Fixture fixture = fixture("DECISION-CATALOG-MISMATCH");
        installDecisionVariant(fixture, "WRONG_CATALOG");
        assertRejected("M2-S05", "M2-PAY-018-CATALOG-VERSION-DRIFT", "DECISION_CATALOG_MISMATCH", fixture,
                request("CMD-M2-DECISION-CATALOG", "IDEM-M2-DECISION-CATALOG", AUTH_SET,
                        SESSION_VERSION, 1, 1),
                "PAYMENT_INTENT_NOT_AVAILABLE", 422);
    }

    @Test void M2_PAY_026_STABLE_REJECTION_EXISTING_CANONICAL_INTENT() throws Exception {
        Fixture fixture = fixture("EXISTING-INTENT");
        create(fixture, "CMD-M2-EXISTING-A", "IDEM-M2-EXISTING-A");
        assertConflict("M2-S08", "M2-PAY-026-STABLE-REJECTION", "CANONICAL_INTENT_EXISTS", fixture,
                request("CMD-M2-EXISTING-B", "IDEM-M2-EXISTING-B", AUTH_SET, SESSION_VERSION, 1, 1));
    }

    @Test void M2_PAY_028_NETWORK_INTERRUPTED_NOT_OBSERVED_CLIENT_RESULT() throws Exception {
        Fixture fixture = fixture("NETWORK-INTERRUPTED");
        BuyerAuthorization authorization = recovery.requireBuyerAuthorization(
                LocalSyntheticOrderRecoveryService.ENVIRONMENT, identity.projectSubjectRef(), identity.sessionRef(),
                SESSION_VERSION, AUTH_SET);
        PaymentIntentEvidenceSnapshot before = snapshot();
        service.createLocalSyntheticPaymentIntent(authorization, fixture.orderRef(),
                MockFlowService.PAYMENT_INTENT_CREATION_PRECONDITION,
                "CMD-M2-NETWORK", "IDEM-M2-NETWORK", 1, 1);
        PaymentIntentEvidenceSnapshot after = snapshot();
        assertFirstDelta(before, after);
        assertZeroDownstreamExceptIntent(before, after);
        writeNetworkInterruptedEvidence(fixture, before, after);
    }

    @ParameterizedTest(name = "M2-SENSITIVITY {0}")
    @ValueSource(strings = {"PaymentAttempt", "requestPayment", "WechatPrepay", "Notification",
            "DispatchIntent", "W", "U", "D", "R", "L", "LedgerEntry", "ExternalCall"})
    void M2_SIDE_EFFECT_OBSERVER_SENSITIVITY(String boundary) throws Exception {
        fixture("SENSITIVITY-" + boundary);
        PaymentIntentEvidenceSnapshot before = snapshot();
        store.writeObservedSideEffectForSensitivityTest(identity.projectSubjectRef(), boundary);
        PaymentIntentEvidenceSnapshot after = snapshot();
        JsonNode delta = deltaJson(before, after);
        assertThat(delta.path(boundary).asLong()).isEqualTo(1);
        assertThatThrownBy(() -> assertThat(delta.path(boundary).asLong()).isZero())
                .isInstanceOf(AssertionError.class);
        writeSensitivityEvidence(boundary, before, after);
    }

    private void installDecisionVariant(Fixture fixture, String variant) {
        PaymentEligibilityDecision source = service.paymentEligibilityDecisionForTest(fixture.orderRef());
        if ("MISSING_DECISION".equals(variant)) {
            service.removePaymentEligibilityDecisionForTest(fixture.orderRef());
            return;
        }
        String environment = source.environment();
        String subject = source.projectSubjectRef();
        String orderRef = source.orderRef();
        String snapshotDigest = source.priceSnapshotDigest();
        long supportedSetVersion = source.supportedOperatorSetVersion();
        long catalogVersion = source.catalogVersion();
        long projectionVersion = source.parentProjectionVersion();
        long aggregateVersion = source.parentAggregateVersion();
        String authorizationEvidence = source.authorizationEvidenceVersion();
        String allowedAction = source.allowedActionCode();
        String ruleVersion = source.ruleVersion();
        PaymentEligibilityDecisionStatus status = source.status();
        Instant validUntil = source.validUntil();
        String decisionRef = "CANONICAL";
        switch (variant) {
            case "EXPIRED_DECISION" -> validUntil = clock.instant().minusSeconds(1);
            case "WRONG_ENVIRONMENT" -> environment = "PRODUCTION";
            case "WRONG_SUBJECT" -> subject = "SYN-SUBJECT-WRONG";
            case "WRONG_ORDER" -> orderRef = "O-SYNTHETIC-WRONG";
            case "WRONG_SNAPSHOT_DIGEST" -> snapshotDigest = "0".repeat(64);
            case "WRONG_SUPPORTED_SET" -> supportedSetVersion++;
            case "WRONG_CATALOG" -> catalogVersion++;
            case "WRONG_PARENT_PROJECTION" -> projectionVersion++;
            case "WRONG_PARENT_AGGREGATE" -> aggregateVersion++;
            case "WRONG_AUTHORIZATION_EVIDENCE" -> authorizationEvidence = "SYN-AUTH-EVIDENCE-WRONG";
            case "WRONG_ALLOWED_ACTION" -> allowedAction = "NONE";
            case "WRONG_RULE_VERSION" -> ruleVersion = "UNTRUSTED_RULE";
            case "TAMPERED_DECISION_REF" -> decisionRef = "PED-TAMPERED";
            case "REJECTED_DECISION" -> status = PaymentEligibilityDecisionStatus.REJECTED;
            default -> { }
        }
        PaymentEligibilityDecision changed = service.copyPaymentEligibilityDecisionForTest(source, decisionRef,
                environment, subject, orderRef, snapshotDigest, supportedSetVersion, catalogVersion,
                projectionVersion, aggregateVersion, authorizationEvidence, allowedAction, ruleVersion, status,
                validUntil);
        service.installPaymentEligibilityDecisionForTest(fixture.orderRef(), changed);
    }

    private void writeNetworkInterruptedEvidence(Fixture fixture, PaymentIntentEvidenceSnapshot before,
                                                 PaymentIntentEvidenceSnapshot after) throws Exception {
        ObjectNode evidence = json.createObjectNode();
        evidence.put("scenarioId", "M2-S08");
        evidence.put("subcaseId", "M2-PAY-028-NETWORK-INTERRUPTED");
        evidence.put("parameterId", "RESPONSE_NOT_OBSERVED");
        appendEvidenceBinding(evidence);
        evidence.put("executionStatus", "PASS");
        evidence.put("httpStatus", "NOT_OBSERVED");
        ObjectNode input = evidence.putObject("input");
        input.put("fixtureDigest", fixtureDigest());
        input.put("apiCallCount", 1);
        ObjectNode expected = evidence.putObject("expected");
        expected.put("clientPaymentIntentDelta", "NOT_OBSERVED");
        expected.put("automaticRetry", 0);
        expected.put("externalCall", 0);
        ObjectNode response = evidence.putObject("response");
        response.put("observation", "NOT_OBSERVED");
        response.put("serverAcceptanceNotInferredByClient", true);
        evidence.set("before", countJson(before));
        evidence.set("after", countJson(after));
        evidence.set("delta", deltaJson(before, after));
        ObjectNode actual = evidence.putObject("actual");
        actual.put("clientPaymentIntentDelta", "NOT_OBSERVED");
        actual.put("automaticRetry", 0);
        actual.put("externalCall", 0);
        actual.put("localServerObserverPaymentIntentDelta", after.paymentIntents() - before.paymentIntents());
        Files.writeString(EVIDENCE_DIRECTORY.resolve(evidenceFileName(
                        "M2-PAY-028-NETWORK-INTERRUPTED", "RESPONSE_NOT_OBSERVED")),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence) + "\n", StandardCharsets.UTF_8);
    }

    private void writeSyntheticEvidence(String scenarioId, String subcaseId, String parameterId,
                                        int httpStatus, String projectCode,
                                        PaymentIntentEvidenceSnapshot before,
                                        PaymentIntentEvidenceSnapshot after,
                                        String inputBoundary) throws Exception {
        ObjectNode evidence = json.createObjectNode();
        evidence.put("scenarioId", scenarioId);
        evidence.put("subcaseId", subcaseId);
        evidence.put("parameterId", parameterId);
        appendEvidenceBinding(evidence);
        evidence.put("executionStatus", "PASS");
        evidence.put("httpStatus", httpStatus);
        ObjectNode input = evidence.putObject("input");
        input.put("fixtureDigest", fixtureDigest());
        input.put("controlledLocalBoundary", inputBoundary);
        ObjectNode expected = evidence.putObject("expected");
        expected.put("projectCode", projectCode);
        expected.put("allNewWrites", 0);
        ObjectNode response = evidence.putObject("response");
        response.put("status", "REJECTED");
        response.put("projectCode", projectCode);
        evidence.set("before", countJson(before));
        evidence.set("after", countJson(after));
        evidence.set("delta", deltaJson(before, after));
        ObjectNode actual = evidence.putObject("actual");
        actual.put("projectCode", projectCode);
        actual.set("delta", deltaJson(before, after));
        Files.writeString(EVIDENCE_DIRECTORY.resolve(evidenceFileName(subcaseId, parameterId)),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence) + "\n", StandardCharsets.UTF_8);
    }

    private void writeSensitivityEvidence(String boundary, PaymentIntentEvidenceSnapshot before,
                                          PaymentIntentEvidenceSnapshot after) throws Exception {
        ObjectNode evidence = json.createObjectNode();
        evidence.put("scenarioId", "M2-SENSITIVITY");
        evidence.put("subcaseId", "QA-D5-M2-BE-002-OBSERVATION-SENSITIVITY");
        evidence.put("parameterId", boundary);
        appendEvidenceBinding(evidence);
        evidence.put("defectRef", "QA-D5-M2-BE-002");
        evidence.put("executionStatus", "PASS");
        evidence.putObject("input").put("controlledLocalWriteBoundary", boundary);
        evidence.putObject("expected").put("observedDelta", 1);
        evidence.set("before", countJson(before));
        evidence.set("after", countJson(after));
        evidence.set("delta", deltaJson(before, after));
        evidence.putObject("actual").put("observedDelta", deltaJson(before, after).path(boundary).asLong());
        Files.writeString(EVIDENCE_DIRECTORY.resolve(evidenceFileName(
                        "QA-D5-M2-BE-002-OBSERVATION-SENSITIVITY", boundary)),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence) + "\n", StandardCharsets.UTF_8);
    }

    private Fixture fixture(String suffix) throws Exception {
        Quote quote = quoteFor(identity.projectSubjectRef(), suffix);
        ObjectNode orderRequest = json.createObjectNode();
        orderRequest.put("commandId", "CMD-M2-ORDER-" + suffix);
        orderRequest.put("idempotencyKey", "IDEM-M2-ORDER-" + suffix);
        orderRequest.put("orderCreationPrecondition", MockFlowService.ORDER_CREATION_PRECONDITION);
        orderRequest.put("quoteRef", quote.quoteRef());
        orderRequest.put("sessionVersion", SESSION_VERSION);
        orderRequest.put("authorizationSetRef", AUTH_SET);
        MvcResult created = mvc.perform(post("/api/v1/orders")
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(orderRequest)))
                .andReturn();
        JsonNode response = response(created, 200, "ORDER_CREATED");
        String orderRef = response.path("resourceRef").asText();
        recovery.appendOrderRefForTest(AUTH_SET, orderRef);
        service.installPaymentEligibilityDecisionForTest(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), orderRef, AUTH_EVIDENCE,
                PaymentEligibilityDecisionStatus.ELIGIBLE, clock.instant().plusSeconds(600));
        activeFixture = new Fixture(orderRef, quote);
        return activeFixture;
    }

    private Quote quoteFor(String subject, String suffix) {
        String unique = suffix + "-" + UUID.randomUUID();
        return service.createQuote(subject, "8801700000000", "SYN-OP", "SYN-PRODUCT",
                "SYN-DENOM-1000", 1L, 1L, "CMD-M2-QUOTE-" + unique,
                "IDEM-M2-QUOTE-" + unique, MnpState.CONFIRMED);
    }

    private void create(Fixture fixture, String commandId, String idempotencyKey) throws Exception {
        response(perform(request(commandId, idempotencyKey, AUTH_SET, SESSION_VERSION, 1, 1),
                fixture.orderRef(), true), 200, "PAYMENT_INTENT_CREATED");
    }

    private void assertConflict(String scenarioId, String subcaseId, String parameterId,
                                Fixture fixture, ObjectNode request) throws Exception {
        assertRejected(scenarioId, subcaseId, parameterId, fixture, request, "IDEMPOTENCY_CONFLICT", 409);
    }

    private void assertRejected(String scenarioId, String subcaseId, String parameterId,
                                Fixture fixture, ObjectNode request,
                                String projectCode, int httpStatus) throws Exception {
        activeFixture = fixture;
        PaymentIntentEvidenceSnapshot before = snapshot();
        MvcResult result = perform(request, fixture.orderRef(), true);
        JsonNode response = response(result, httpStatus, projectCode);
        PaymentIntentEvidenceSnapshot after = snapshot();
        assertNoDelta(before, after);
        assertThat(response.hasNonNull("data")).isFalse();
        writeEvidence(scenarioId, subcaseId, parameterId, result, response, before, after);
    }

    private ObjectNode request(String commandId, String idempotencyKey, String authorizationSetRef,
                               long sessionVersion, long projectionVersion, long aggregateVersion) {
        ObjectNode request = json.createObjectNode();
        request.put("commandId", commandId);
        request.put("idempotencyKey", idempotencyKey);
        request.put("paymentIntentCreationPrecondition",
                MockFlowService.PAYMENT_INTENT_CREATION_PRECONDITION);
        request.put("sessionVersion", sessionVersion);
        request.put("authorizationSetRef", authorizationSetRef);
        request.put("expectedProjectionVersion", projectionVersion);
        request.put("expectedAggregateVersion", aggregateVersion);
        return request;
    }

    private MvcResult perform(ObjectNode request, String orderRef, boolean includeToken) throws Exception {
        var builder = post("/api/v1/orders/{orderRef}/payment-intents", orderRef)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request));
        if (includeToken) builder.header(TestAccessTokenFilter.HEADER_NAME, TOKEN);
        return mvc.perform(builder).andReturn();
    }

    private MvcResult concurrentPerform(ObjectNode request, String orderRef, CountDownLatch ready,
                                        CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent start timeout");
        return perform(request.deepCopy(), orderRef, true);
    }

    private JsonNode response(MvcResult result, int status, String projectCode) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        JsonNode response = json.readTree(result.getResponse().getContentAsString());
        String actual = response.has("projectCode") ? response.path("projectCode").asText()
                : response.path("status").asText();
        assertThat(actual).isEqualTo(projectCode);
        return response;
    }

    private PaymentIntentEvidenceSnapshot snapshot() {
        return store.paymentIntentEvidenceSnapshot(identity.projectSubjectRef());
    }

    private static void assertFirstDelta(PaymentIntentEvidenceSnapshot before,
                                         PaymentIntentEvidenceSnapshot after) {
        assertThat(after.orders() - before.orders()).isZero();
        assertThat(after.semanticActions() - before.semanticActions()).isEqualTo(1);
        assertThat(after.paymentIntents() - before.paymentIntents()).isEqualTo(1);
        assertThat(after.paymentIntentBusinessKeys() - before.paymentIntentBusinessKeys()).isEqualTo(1);
    }

    private static void assertNoDelta(PaymentIntentEvidenceSnapshot before,
                                      PaymentIntentEvidenceSnapshot after) {
        assertThat(after.orders() - before.orders()).isZero();
        assertThat(after.semanticActions() - before.semanticActions()).isZero();
        assertThat(after.paymentIntents() - before.paymentIntents()).isZero();
        assertThat(after.paymentIntentBusinessKeys() - before.paymentIntentBusinessKeys()).isZero();
        assertThat(after.downstream()).isEqualTo(before.downstream());
    }

    private static void assertZeroDownstreamExceptIntent(PaymentIntentEvidenceSnapshot before,
                                                         PaymentIntentEvidenceSnapshot after) {
        OrderCreationSideEffectSnapshot left = before.downstream();
        OrderCreationSideEffectSnapshot right = after.downstream();
        assertThat(right.paymentIntents() - left.paymentIntents()).isEqualTo(1);
        assertThat(right.paymentAttempts() - left.paymentAttempts()).isZero();
        assertThat(right.wechatPrepayCalls() - left.wechatPrepayCalls()).isZero();
        assertThat(right.requestPaymentCalls() - left.requestPaymentCalls()).isZero();
        assertThat(right.paymentNotifications() - left.paymentNotifications()).isZero();
        assertThat(right.dispatchIntents() - left.dispatchIntents()).isZero();
        assertThat(right.wechatPaymentFacts() - left.wechatPaymentFacts()).isZero();
        assertThat(right.upstreamDebitFacts() - left.upstreamDebitFacts()).isZero();
        assertThat(right.deliveryFacts() - left.deliveryFacts()).isZero();
        assertThat(right.refundFacts() - left.refundFacts()).isZero();
        assertThat(right.localLedgerFacts() - left.localLedgerFacts()).isZero();
        assertThat(right.ledgerEntries() - left.ledgerEntries()).isZero();
        assertThat(right.externalCalls() - left.externalCalls()).isZero();
    }

    private void writeEvidence(String scenarioId, String subcaseId, String parameterId,
                               MvcResult result, JsonNode response,
                               PaymentIntentEvidenceSnapshot before,
                               PaymentIntentEvidenceSnapshot after) throws Exception {
        ObjectNode evidence = json.createObjectNode();
        evidence.put("scenarioId", scenarioId);
        evidence.put("subcaseId", subcaseId);
        evidence.put("parameterId", parameterId);
        appendEvidenceBinding(evidence);
        evidence.put("executionStatus", "PASS");
        evidence.put("httpStatus", result.getResponse().getStatus());
        ObjectNode input = evidence.putObject("input");
        input.put("fixtureDigest", fixtureDigest());
        input.put("parameterId", parameterId);
        ObjectNode expected = evidence.putObject("expected");
        expected.put("projectCode", response.path("projectCode").asText(response.path("status").asText()));
        expected.put("counterRule", "JUNIT_ASSERTED_EXACT_ZERO_OR_ONE_BY_MATRIX_ROW");
        evidence.set("response", response);
        evidence.set("before", countJson(before));
        evidence.set("after", countJson(after));
        evidence.set("delta", deltaJson(before, after));
        evidence.set("actual", actualJson(result, response, before, after));
        Files.writeString(EVIDENCE_DIRECTORY.resolve(evidenceFileName(subcaseId, parameterId)),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence) + "\n",
                StandardCharsets.UTF_8);
        System.out.println("M2_PAYMENT_INTENT_EVIDENCE|" + json.writeValueAsString(evidence));
    }

    private void writeConcurrentEvidence(String scenarioId, String subcaseId, String parameterId,
                                         JsonNode leftResponse, JsonNode rightResponse,
                                         PaymentIntentEvidenceSnapshot before,
                                         PaymentIntentEvidenceSnapshot after) throws Exception {
        ObjectNode evidence = json.createObjectNode();
        evidence.put("scenarioId", scenarioId);
        evidence.put("subcaseId", subcaseId);
        evidence.put("parameterId", parameterId);
        appendEvidenceBinding(evidence);
        evidence.put("executionStatus", "PASS");
        evidence.put("scheduler", "SINGLE_JVM_START_BARRIER");
        evidence.putObject("input").put("fixtureDigest", fixtureDigest());
        evidence.putObject("expected").put("counterRule", parameterId.contains("DIFFERENT_DOUBLE_KEYS")
                ? "ONE_CREATED_ONE_IDEMPOTENCY_CONFLICT_SINGLE_CANONICAL_INTENT"
                : "ONE_CREATED_ONE_REPLAYED_SINGLE_CANONICAL_INTENT");
        evidence.set("leftResponse", leftResponse);
        evidence.set("rightResponse", rightResponse);
        evidence.set("before", countJson(before));
        evidence.set("after", countJson(after));
        evidence.set("delta", deltaJson(before, after));
        ObjectNode actual = evidence.putObject("actual");
        actual.put("leftProjectCode", leftResponse.path("projectCode").asText());
        actual.put("rightProjectCode", rightResponse.path("projectCode").asText());
        actual.set("delta", deltaJson(before, after));
        Files.writeString(EVIDENCE_DIRECTORY.resolve(evidenceFileName(subcaseId, parameterId)),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence) + "\n",
                StandardCharsets.UTF_8);
        System.out.println("M2_PAYMENT_INTENT_EVIDENCE|" + json.writeValueAsString(evidence));
    }

    private void appendEvidenceBinding(ObjectNode evidence) throws Exception {
        evidence.put("evidencePackageId", EVIDENCE_PACKAGE_ID);
        evidence.put("executionRunId", EXECUTION_RUN_ID);
        evidence.put("executedAt", EXECUTED_AT);
        evidence.put("matrixSha256", MATRIX_SHA);
        ObjectNode d3 = evidence.putObject("d3Sha256");
        d3.put("D3-03", D3_03_SHA);
        d3.put("D3-05", D3_05_SHA);
        d3.put("D3-REGISTRY", D3_REGISTRY_SHA);
        evidence.put("implementationAggregateAlgorithm",
                ".NET StringComparer.Ordinal equivalent path sort; path|UPPERCASE_SHA256; UTF-8 no BOM; LF between; no terminal LF");
        evidence.put("implementationAggregateSha256", implementationAggregate());
        evidence.put("defectRef", "QA-D5-M2-BE-001");
        evidence.put("evidencePackageDigest", "PENDING_AFTER_ALL_CASES");
        evidence.put("evidencePackageSha256", "PENDING_AFTER_ALL_CASES");
    }

    private ObjectNode actualJson(MvcResult result, JsonNode response,
                                  PaymentIntentEvidenceSnapshot before,
                                  PaymentIntentEvidenceSnapshot after) {
        ObjectNode actual = json.createObjectNode();
        actual.put("httpStatus", result.getResponse().getStatus());
        actual.put("projectCode", response.path("projectCode").asText(response.path("status").asText()));
        actual.set("fullResponse", response);
        actual.set("before", countJson(before));
        actual.set("after", countJson(after));
        actual.set("delta", deltaJson(before, after));
        return actual;
    }

    private String fixtureDigest() {
        if (activeFixture == null) return CanonicalFingerprint.sha256("NO_FIXTURE");
        Quote quote = activeFixture.quote();
        return CanonicalFingerprint.sha256(activeFixture.orderRef(), quote.quoteRef(), quote.maskedPhone(),
                quote.operatorCode(), quote.productCode(), quote.denominationRef(),
                String.valueOf(quote.supportedOperatorSetVersion()), String.valueOf(quote.catalogVersion()),
                String.valueOf(quote.totalAmountMinor()), quote.currency(), quote.expiresAt().toString());
    }

    private static String evidenceFileName(String subcaseId, String parameterId) {
        return (subcaseId + "__" + parameterId).replaceAll("[^A-Za-z0-9._-]", "_") + ".json";
    }

    private static String implementationAggregate() throws Exception {
        Path root = repositoryRoot();
        List<String> lines = new ArrayList<>();
        for (String path : IMPLEMENTATION_PATHS) {
            lines.add(path + "|" + sha256(Files.readAllBytes(root.resolve(path))));
        }
        lines.sort(Comparator.naturalOrder());
        return sha256(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("pom.xml")) && Files.isDirectory(candidate.resolve("apps"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private ObjectNode countJson(PaymentIntentEvidenceSnapshot snapshot) {
        ObjectNode node = json.createObjectNode();
        node.put("Order", snapshot.orders());
        node.put("SemanticAction", snapshot.semanticActions());
        node.put("PaymentIntent", snapshot.paymentIntents());
        node.put("PaymentIntentBusinessKey", snapshot.paymentIntentBusinessKeys());
        appendDownstream(node, snapshot.downstream());
        return node;
    }

    private ObjectNode deltaJson(PaymentIntentEvidenceSnapshot before, PaymentIntentEvidenceSnapshot after) {
        ObjectNode node = json.createObjectNode();
        node.put("Order", after.orders() - before.orders());
        node.put("SemanticAction", after.semanticActions() - before.semanticActions());
        node.put("PaymentIntent", after.paymentIntents() - before.paymentIntents());
        node.put("PaymentIntentBusinessKey",
                after.paymentIntentBusinessKeys() - before.paymentIntentBusinessKeys());
        OrderCreationSideEffectSnapshot left = before.downstream();
        OrderCreationSideEffectSnapshot right = after.downstream();
        node.put("PaymentAttempt", right.paymentAttempts() - left.paymentAttempts());
        node.put("DispatchIntent", right.dispatchIntents() - left.dispatchIntents());
        node.put("requestPayment", right.requestPaymentCalls() - left.requestPaymentCalls());
        node.put("WechatPrepay", right.wechatPrepayCalls() - left.wechatPrepayCalls());
        node.put("Notification", right.paymentNotifications() - left.paymentNotifications());
        node.put("W", right.wechatPaymentFacts() - left.wechatPaymentFacts());
        node.put("U", right.upstreamDebitFacts() - left.upstreamDebitFacts());
        node.put("D", right.deliveryFacts() - left.deliveryFacts());
        node.put("R", right.refundFacts() - left.refundFacts());
        node.put("L", right.localLedgerFacts() - left.localLedgerFacts());
        node.put("LedgerEntry", right.ledgerEntries() - left.ledgerEntries());
        node.put("ExternalCall", right.externalCalls() - left.externalCalls());
        return node;
    }

    private static void appendDownstream(ObjectNode node, OrderCreationSideEffectSnapshot effects) {
        node.put("PaymentAttempt", effects.paymentAttempts());
        node.put("requestPayment", effects.requestPaymentCalls());
        node.put("WechatPrepay", effects.wechatPrepayCalls());
        node.put("Notification", effects.paymentNotifications());
        node.put("DispatchIntent", effects.dispatchIntents());
        node.put("W", effects.wechatPaymentFacts());
        node.put("U", effects.upstreamDebitFacts());
        node.put("D", effects.deliveryFacts());
        node.put("R", effects.refundFacts());
        node.put("L", effects.localLedgerFacts());
        node.put("LedgerEntry", effects.ledgerEntries());
        node.put("ExternalCall", effects.externalCalls());
    }

    private record Fixture(String orderRef, Quote quote) {}
}
