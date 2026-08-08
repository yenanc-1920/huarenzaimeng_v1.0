package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huarenzaimeng.api.config.TestAccessTokenFilter;
import com.huarenzaimeng.core.MnpState;
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
import org.springframework.transaction.support.TransactionTemplate;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory",
        "hz.test-access-token=local-synthetic-m2-map003-secret"
})
@AutoConfigureMockMvc
class PaymentIntentResultApiContractTest {
    private static final String TOKEN = "local-synthetic-m2-map003-secret";
    private static final long SESSION_VERSION = 21L;
    private static final String AUTH_SET = "SYN-AS-MAP003";
    private static final String AUTH_EVIDENCE = "SYN-AUTH-EVIDENCE-MAP003";
    private static final String D3_03_SHA = "4B8D4615D5E9CC5452AB7652285F22BCA2CE0B66307A40ACB50414DD5F73AD68";
    private static final String D3_05_SHA = "E830EC602F5B7D469E00D62C98D4951B7712C6772CBD6A362E10A121A59077A2";
    private static final String D3_REGISTRY_SHA = "2B9C2ED5EC5C39257C33BE3608612452A7390022E119796CA4404D01B7E22F27";
    private static final String RUN_ID = "MAP003-BE-" + UUID.randomUUID();
    private static final String EXECUTED_AT = Instant.now().toString();
    private static final Path EVIDENCE_DIRECTORY = Path.of("target", "m2-map003-evidence");
    private static final Set<String> STRICT_FIELDS = Set.of("requestRef", "outcome", "projectCode", "resourceRef",
            "aggregateVersion", "currentProjection", "retryClass", "nextPollAt");
    private static final String MATRIX_SCENARIO = "M2-S08";
    private static final String MATRIX_WRITE_UNKNOWN = "M2-PAY-027-WRITE-UNKNOWN";
    private static final String MATRIX_NETWORK_INTERRUPTED = "M2-PAY-028-NETWORK-INTERRUPTED";
    private static final List<String> IMPLEMENTATION_PATHS = List.of(
            "apps/api/src/main/java/com/huarenzaimeng/api/FlowMapper.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/FlowStore.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/InMemoryFlowStore.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/LocalSyntheticOrderRecoveryService.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/MockFlowController.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/MockFlowService.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/MyBatisFlowStore.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/PaymentIntentDomain.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/config/TestAccessTokenFilter.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/PaymentIntentResultApiContractTest.java");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MockFlowService service;
    @Autowired LocalSyntheticOrderRecoveryService recovery;
    @Autowired InMemoryFlowStore store;
    @Autowired InMemoryCatalogStore catalog;
    @Autowired Clock clock;

    private LocalSyntheticIdentity identity;

    @BeforeAll
    static void clearEvidence() throws Exception {
        Files.createDirectories(EVIDENCE_DIRECTORY);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(EVIDENCE_DIRECTORY, "*.json")) {
            for (Path file : files) Files.deleteIfExists(file);
        }
    }

    @AfterAll
    static void finalizeEvidence() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Path> files;
        try (var stream = Files.list(EVIDENCE_DIRECTORY)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().equals("evidence-package-index.json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        Map<Path, ObjectNode> cases = new HashMap<>();
        List<String> normalized = new ArrayList<>();
        for (Path file : files) {
            ObjectNode node = (ObjectNode) mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            node.remove(List.of("evidencePackageSha256", "evidencePackageDigest"));
            cases.put(file, node);
            normalized.add(file.getFileName() + "|" + sha256(mapper.writeValueAsBytes(node)));
        }
        normalized.sort(Comparator.naturalOrder());
        String packageSha = sha256(String.join("\n", normalized).getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<Path, ObjectNode> entry : cases.entrySet()) {
            entry.getValue().put("evidencePackageSha256", packageSha);
            entry.getValue().put("evidencePackageDigest", packageSha);
            Files.writeString(entry.getKey(), mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(entry.getValue()) + "\n", StandardCharsets.UTF_8);
        }
        ObjectNode index = mapper.createObjectNode();
        index.put("evidencePackageId", "D5-M2-MAP003-BE-LOCAL-SYNTHETIC");
        index.put("evidenceRoot", "apps/api/target/m2-map003-evidence");
        index.put("executionRunId", RUN_ID);
        index.put("executedAt", EXECUTED_AT);
        index.put("implementationAggregateSha256", implementationAggregate());
        index.put("implementationAggregateAlgorithm",
                ".NET StringComparer.Ordinal equivalent path sort; path|UPPERCASE_SHA256; UTF-8 no BOM; LF between; no terminal LF");
        index.put("evidencePackageSha256", packageSha);
        ObjectNode d3 = index.putObject("d3Sha256");
        d3.put("D3-03", D3_03_SHA);
        d3.put("D3-05", D3_05_SHA);
        d3.put("D3-REGISTRY", D3_REGISTRY_SHA);
        ArrayNode evidenceFiles = index.putArray("evidenceFiles");
        for (Path file : files) {
            ObjectNode item = evidenceFiles.addObject();
            item.put("path", "apps/api/target/m2-map003-evidence/" + file.getFileName());
            item.put("sha256", sha256(Files.readAllBytes(file)));
        }
        index.put("caseCount", files.size());
        ArrayNode matrix = index.putArray("fixedMatrixCoverage");
        for (String fixedSubcase : List.of(MATRIX_WRITE_UNKNOWN, MATRIX_NETWORK_INTERRUPTED)) {
            ObjectNode row = matrix.addObject();
            row.put("scenarioId", MATRIX_SCENARIO);
            row.put("subcaseId", fixedSubcase);
            ArrayNode mappedFiles = row.putArray("evidenceFiles");
            files.stream().filter(file -> fixedSubcase.equals(cases.get(file).path("subcaseId").asText()))
                    .map(file -> file.getFileName().toString()).forEach(mappedFiles::add);
            row.put("executionStatus", mappedFiles.isEmpty() ? "NOT_RUN" : "PASS");
        }
        index.put("executionStatus", "PASS");
        index.put("realMySql", "NOT_RUN");
        index.put("crossProcess", "NOT_RUN");
        index.put("wechatAndFunds", "NOT_RUN");
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
        service.resetPaymentIntentResultQueryCallsForTest();
        recovery.installBuyerAuthorizationForTest(identity.projectSubjectRef(), identity.sessionRef(),
                SESSION_VERSION, AUTH_SET, AUTH_EVIDENCE);
    }

    @Test
    void MAP003_BE_001_UNKNOWN_STAYS_UNKNOWN_AND_REPEAT_IS_ZERO_WRITE() throws Exception {
        Fixture fixture = fixture("UNKNOWN-STABLE", true);
        store.maskPaymentIntentResultForTest(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), fixture.orderRef(), fixture.commandId(), fixture.idempotencyKey(),
                PaymentIntentResultState.UNKNOWN, clock.instant().plusSeconds(30));
        EvidenceSnapshot before = snapshot(identity.projectSubjectRef());
        JsonNode first = query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null);
        EvidenceSnapshot middle = snapshot(identity.projectSubjectRef());
        assertUnknown(first);
        assertReadOnly(before, middle, 1);
        writeEvidence("MAP003-BE-001", "UNKNOWN_FIRST", first, before, middle);

        JsonNode second = query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null);
        EvidenceSnapshot after = snapshot(identity.projectSubjectRef());
        assertUnknown(second);
        assertThat(second).isEqualTo(first);
        assertReadOnly(middle, after, 1);
        writeEvidence("MAP003-BE-001", "UNKNOWN_REPEAT", second, middle, after);
    }

    @Test
    void MAP003_BE_002_UNKNOWN_CONVERGES_TO_ORIGINAL_FOUND_MONOTONICALLY() throws Exception {
        Fixture fixture = fixture("LATE-FOUND", true);
        store.maskPaymentIntentResultForTest(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), fixture.orderRef(), fixture.commandId(), fixture.idempotencyKey(),
                PaymentIntentResultState.UNKNOWN, clock.instant().plusSeconds(30));
        assertUnknown(query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null));
        store.convergePaymentIntentResultForTest(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), fixture.orderRef(), fixture.commandId(), fixture.idempotencyKey(),
                PaymentIntentResultState.FOUND);
        EvidenceSnapshot before = snapshot(identity.projectSubjectRef());
        JsonNode found = query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null);
        EvidenceSnapshot after = snapshot(identity.projectSubjectRef());
        assertFound(found, fixture.paymentIntentRef());
        assertReadOnly(before, after, 1);
        EvidenceSnapshot conflictBefore = snapshot(identity.projectSubjectRef());
        assertThatThrownBy(() -> store.convergePaymentIntentResultForTest(
                LocalSyntheticOrderRecoveryService.ENVIRONMENT, identity.projectSubjectRef(), fixture.orderRef(),
                fixture.commandId(), fixture.idempotencyKey(), PaymentIntentResultState.UNKNOWN))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.convergePaymentIntentResultForTest(
                LocalSyntheticOrderRecoveryService.ENVIRONMENT, identity.projectSubjectRef(), fixture.orderRef(),
                fixture.commandId(), fixture.idempotencyKey(), PaymentIntentResultState.UNKNOWN))
                .isInstanceOf(IllegalStateException.class);
        EvidenceSnapshot conflictAfter = snapshot(identity.projectSubjectRef());
        assertBusinessReadOnlyWithReviewDelta(conflictBefore, conflictAfter, 0, 1);
        assertReviewSignal(store.paymentIntentReviewSignalsForTest(identity.projectSubjectRef()).get(0),
                PaymentIntentReviewReason.AUTHORITATIVE_RESULT_CONFLICT, fixture.commandId());
        writeEvidence("MAP003-BE-002", "LATE_FOUND", found, before, after);
        writeEvidence("MAP003-BE-008", "AUTHORITATIVE_RESULT_CONFLICT_REVIEW_DEDUPED", found,
                conflictBefore, conflictAfter, 1);
    }

    @Test
    void MAP003_BE_003_UNKNOWN_CONVERGES_TO_REJECTED_MONOTONICALLY() throws Exception {
        Fixture fixture = fixture("LATE-REJECTED", false);
        store.installRejectedPaymentIntentResultForTest(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), fixture.orderRef(), fixture.commandId(), fixture.idempotencyKey(),
                PaymentIntentResultState.UNKNOWN, clock.instant().plusSeconds(30));
        assertUnknown(query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null));
        store.convergePaymentIntentResultForTest(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), fixture.orderRef(), fixture.commandId(), fixture.idempotencyKey(),
                PaymentIntentResultState.REJECTED);
        EvidenceSnapshot before = snapshot(identity.projectSubjectRef());
        JsonNode rejected = query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null);
        EvidenceSnapshot after = snapshot(identity.projectSubjectRef());
        assertRejected(rejected, "PAYMENT_INTENT_RESULT_REJECTED");
        assertReadOnly(before, after, 1);
        writeEvidence("MAP003-BE-003", "LATE_REJECTED", rejected, before, after);
    }

    @Test
    void MAP003_BE_004_FOUND_REPEATS_ORIGINAL_REF_WITH_ZERO_WRITE() throws Exception {
        Fixture fixture = fixture("FOUND-REPEAT", true);
        EvidenceSnapshot before = snapshot(identity.projectSubjectRef());
        JsonNode first = query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null);
        JsonNode second = query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null);
        EvidenceSnapshot after = snapshot(identity.projectSubjectRef());
        assertFound(first, fixture.paymentIntentRef());
        assertThat(second).isEqualTo(first);
        assertReadOnly(before, after, 2);
        writeEvidence("MAP003-BE-004", "FOUND_REPEAT", second, before, after);
    }

    @ParameterizedTest(name = "MAP003 unavailable {0}")
    @ValueSource(strings = {"ABSENT", "WRONG_COMMAND", "WRONG_IDEMPOTENCY", "CROSS_SUBJECT", "REVOKED_SESSION"})
    void MAP003_BE_005_NOT_AVAILABLE_IS_EXISTENCE_UNIFORM(String variant) throws Exception {
        Fixture fixture = fixture("NOT-AVAILABLE-" + variant, false);
        String commandId = fixture.commandId();
        String idempotencyKey = fixture.idempotencyKey();
        if ("WRONG_COMMAND".equals(variant)) commandId = "CMD-WRONG";
        if ("WRONG_IDEMPOTENCY".equals(variant)) idempotencyKey = "IDEM-WRONG";
        if ("CROSS_SUBJECT".equals(variant)) {
            store.installRejectedPaymentIntentResultForTest(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                    "SYN-SUBJECT-OTHER", fixture.orderRef(), commandId, idempotencyKey,
                    PaymentIntentResultState.REJECTED, null);
        }
        if ("REVOKED_SESSION".equals(variant)) recovery.revokeBuyerAuthorizationForTest(AUTH_SET);
        EvidenceSnapshot before = snapshot(identity.projectSubjectRef());
        JsonNode response = query(fixture, commandId, idempotencyKey, TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null);
        EvidenceSnapshot after = snapshot(identity.projectSubjectRef());
        assertRejected(response, "PAYMENT_INTENT_QUERY_NOT_AVAILABLE");
        assertReadOnly(before, after, 1);
        writeEvidence("MAP003-BE-005", variant, response, before, after);
    }

    @ParameterizedTest(name = "MAP003 invalid shape {0}")
    @ValueSource(strings = {"MISSING_COMMAND", "MISSING_IDEMPOTENCY", "MISSING_SESSION", "MISSING_SET",
            "INVALID_SESSION_TYPE", "EXTRA_FIELD", "DUPLICATE_COMMAND"})
    void MAP003_BE_006_INVALID_QUERY_STILL_RETURNS_STRICT_EIGHT_FIELDS(String variant) throws Exception {
        Fixture fixture = fixture("INVALID-" + variant, false);
        EvidenceSnapshot before = snapshot(identity.projectSubjectRef());
        JsonNode response = invalidQuery(fixture, variant);
        EvidenceSnapshot after = snapshot(identity.projectSubjectRef());
        assertRejected(response, "PAYMENT_INTENT_QUERY_NOT_AVAILABLE");
        assertReadOnly(before, after, 1);
        writeEvidence("MAP003-BE-006", variant, response, before, after);
    }

    @ParameterizedTest(name = "MAP003 auth same shape {0}")
    @ValueSource(strings = {"NO_TOKEN", "WRONG_TOKEN"})
    void MAP003_BE_007_UNAUTHENTICATED_IS_STRICT_AND_EXISTENCE_UNIFORM(String variant) throws Exception {
        Fixture fixture = fixture("AUTH-" + variant, true);
        EvidenceSnapshot before = snapshot("UNAUTHENTICATED");
        JsonNode response = query(fixture, fixture.commandId(), fixture.idempotencyKey(),
                "NO_TOKEN".equals(variant) ? null : "wrong-token", String.valueOf(SESSION_VERSION), AUTH_SET, null);
        EvidenceSnapshot after = snapshot("UNAUTHENTICATED");
        assertRejected(response, "PAYMENT_INTENT_QUERY_NOT_AVAILABLE");
        assertReadOnly(before, after, 1);
        writeEvidence("MAP003-BE-007", variant, response, before, after);
    }

    @Test
    void MAP003_BE_009_BINDING_CONFLICT_IS_PUBLICLY_UNIFORM_AND_REVIEW_DEDUPED() throws Exception {
        Fixture fixture = fixture("BINDING-CONFLICT", true);
        store.installPaymentIntentResultBindingConflictForTest(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), fixture.orderRef(), fixture.commandId(), fixture.idempotencyKey());
        EvidenceSnapshot before = snapshot(identity.projectSubjectRef());
        JsonNode first = query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null);
        JsonNode second = query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null);
        EvidenceSnapshot after = snapshot(identity.projectSubjectRef());
        assertRejected(first, "PAYMENT_INTENT_QUERY_NOT_AVAILABLE");
        assertThat(second).isEqualTo(first);
        assertBusinessReadOnlyWithReviewDelta(before, after, 2, 1);
        assertReviewSignal(store.paymentIntentReviewSignalsForTest(identity.projectSubjectRef()).get(0),
                PaymentIntentReviewReason.PAYMENT_INTENT_RESULT_BINDING_CONFLICT, fixture.commandId());
        writeEvidence("MAP003-BE-009", "BINDING_CONFLICT_REVIEW_DEDUPED", second, before, after, 1);
    }

    @Test
    void MAP003_BE_010_CONTROLLED_RUNTIME_FAILURE_IS_PUBLICLY_UNIFORM_AND_REVIEW_DEDUPED() throws Exception {
        Fixture fixture = fixture("CONTROLLED-RUNTIME", true);
        store.failPaymentIntentResultQueryForTest(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), fixture.orderRef(), fixture.commandId(), fixture.idempotencyKey());
        EvidenceSnapshot before = snapshot(identity.projectSubjectRef());
        JsonNode first = query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null);
        JsonNode second = query(fixture, fixture.commandId(), fixture.idempotencyKey(), TOKEN,
                String.valueOf(SESSION_VERSION), AUTH_SET, null);
        EvidenceSnapshot after = snapshot(identity.projectSubjectRef());
        assertRejected(first, "PAYMENT_INTENT_QUERY_NOT_AVAILABLE");
        assertThat(second).isEqualTo(first);
        assertBusinessReadOnlyWithReviewDelta(before, after, 2, 1);
        assertReviewSignal(store.paymentIntentReviewSignalsForTest(identity.projectSubjectRef()).get(0),
                PaymentIntentReviewReason.CONTROLLED_RUNTIME_EXCEPTION, fixture.commandId());
        writeEvidence("MAP003-BE-010", "CONTROLLED_RUNTIME_REVIEW_DEDUPED", second, before, after, 1);
    }

    @ParameterizedTest(name = "MAP003 metadata observer sensitivity {0}")
    @ValueSource(strings = {"Command", "CommandAlias", "OrderVersion", "ProjectionVersion"})
    void MAP003_BE_011_METADATA_OBSERVERS_ARE_NOT_FIXED_ZERO(String boundary) throws Exception {
        Fixture fixture = fixture("SENSITIVITY-" + boundary, false);
        EvidenceSnapshot before = snapshot(identity.projectSubjectRef());
        store.writeObservedPaymentIntentMetadataForSensitivityTest(identity.projectSubjectRef(), fixture.orderRef(),
                boundary);
        EvidenceSnapshot after = snapshot(identity.projectSubjectRef());
        assertObserverDeltaOnly(before, after, boundary);
        writeSensitivityEvidence(boundary, before, after);
    }

    @ParameterizedTest(name = "MAP003 MyBatis original command cross-check {0}")
    @ValueSource(strings = {"COMMAND_FINGERPRINT_MISMATCH", "COMMAND_SEMANTIC_ACTION_MISMATCH"})
    void MAP003_BE_012_MYBATIS_COMMAND_BINDING_MISMATCH_FAILS_CLOSED_WITH_REVIEW(String variant)
            throws Exception {
        Fixture fixture = fixture("MYBATIS-" + variant, false);
        FlowMapper mapper = mock(FlowMapper.class);
        MyBatisFlowStore mysqlStore = new MyBatisFlowStore(mapper, mock(TransactionTemplate.class));
        String businessKey = MockFlowService.paymentIntentBusinessKey(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), fixture.orderRef());
        String semanticKey = MockFlowService.paymentIntentSemanticActionKey(
                LocalSyntheticOrderRecoveryService.ENVIRONMENT, identity.projectSubjectRef(), fixture.orderRef());
        Map<String, Object> row = new HashMap<>();
        row.put("command_id", fixture.commandId());
        row.put("idempotency_key", fixture.idempotencyKey());
        row.put("command_canonical_fingerprint", "COMMAND_FINGERPRINT_MISMATCH".equals(variant)
                ? "FP-COMMAND-MISMATCH" : "FP-MATCH");
        row.put("command_semantic_action_key", "COMMAND_SEMANTIC_ACTION_MISMATCH".equals(variant)
                ? semanticKey + ":COMMAND-MISMATCH" : semanticKey);
        row.put("payment_intent_ref", "PI-MYBATIS-LOCAL");
        row.put("environment", LocalSyntheticOrderRecoveryService.ENVIRONMENT);
        row.put("project_subject_ref", identity.projectSubjectRef());
        row.put("order_ref", fixture.orderRef());
        row.put("payment_intent_business_key", businessKey);
        row.put("payment_intent_semantic_action_key", semanticKey);
        row.put("payment_intent_request_fingerprint", "FP-MATCH");
        when(mapper.selectPaymentIntentResultByOriginalKeys(identity.projectSubjectRef(), fixture.orderRef(),
                fixture.commandId(), fixture.idempotencyKey())).thenReturn(row);
        MockFlowService mysqlService = new MockFlowService(mysqlStore);

        EvidenceSnapshot domainBefore = snapshot(identity.projectSubjectRef());
        long reviewBefore = mysqlStore.paymentIntentReviewSignalCountForTest(identity.projectSubjectRef());
        PaymentIntentResultResponse first = mysqlService.queryLocalSyntheticPaymentIntentResult(recovery,
                LocalSyntheticOrderRecoveryService.ENVIRONMENT, identity.projectSubjectRef(), identity.sessionRef(),
                fixture.orderRef(), fixture.commandId(), fixture.idempotencyKey(), String.valueOf(SESSION_VERSION),
                AUTH_SET, false);
        PaymentIntentResultResponse second = mysqlService.queryLocalSyntheticPaymentIntentResult(recovery,
                LocalSyntheticOrderRecoveryService.ENVIRONMENT, identity.projectSubjectRef(), identity.sessionRef(),
                fixture.orderRef(), fixture.commandId(), fixture.idempotencyKey(), String.valueOf(SESSION_VERSION),
                AUTH_SET, false);
        long reviewAfter = mysqlStore.paymentIntentReviewSignalCountForTest(identity.projectSubjectRef());
        EvidenceSnapshot domainAfter = snapshot(identity.projectSubjectRef());
        JsonNode response = json.valueToTree(second);
        assertStrictFields(response);
        assertRejected(response, "PAYMENT_INTENT_QUERY_NOT_AVAILABLE");
        assertThat(first).isEqualTo(second);
        assertThat(reviewAfter - reviewBefore).isEqualTo(1);
        PaymentIntentReviewSignal signal = mysqlStore.paymentIntentReviewSignalsForTest(
                identity.projectSubjectRef()).get(0);
        assertReviewSignal(signal, PaymentIntentReviewReason.ORIGINAL_COMMAND_BINDING_MISMATCH,
                fixture.commandId());
        writeMyBatisMismatchEvidence(variant, response, domainBefore, domainAfter, reviewBefore, reviewAfter, signal);
    }

    private Fixture fixture(String suffix, boolean createIntent) throws Exception {
        String unique = suffix + "-" + UUID.randomUUID();
        Quote quote = service.createQuote(identity.projectSubjectRef(), "8801700000000", "SYN-OP", "SYN-PRODUCT",
                "SYN-DENOM-1000", 1L, 1L, "CMD-Q-" + unique, "IDEM-Q-" + unique, MnpState.CONFIRMED);
        ObjectNode orderRequest = json.createObjectNode();
        orderRequest.put("commandId", "CMD-O-" + unique);
        orderRequest.put("idempotencyKey", "IDEM-O-" + unique);
        orderRequest.put("orderCreationPrecondition", MockFlowService.ORDER_CREATION_PRECONDITION);
        orderRequest.put("quoteRef", quote.quoteRef());
        orderRequest.put("sessionVersion", SESSION_VERSION);
        orderRequest.put("authorizationSetRef", AUTH_SET);
        JsonNode order = json.readTree(mvc.perform(post("/api/v1/orders")
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(orderRequest))).andReturn().getResponse().getContentAsString());
        String orderRef = order.path("resourceRef").asText();
        assertThat(orderRef).startsWith("O-");
        recovery.appendOrderRefForTest(AUTH_SET, orderRef);
        service.installPaymentEligibilityDecisionForTest(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), orderRef, AUTH_EVIDENCE, PaymentEligibilityDecisionStatus.ELIGIBLE,
                clock.instant().plusSeconds(600));
        String commandId = "CMD-PI-" + unique;
        String idempotencyKey = "IDEM-PI-" + unique;
        String paymentIntentRef = null;
        if (createIntent) {
            ObjectNode request = json.createObjectNode();
            request.put("commandId", commandId);
            request.put("idempotencyKey", idempotencyKey);
            request.put("paymentIntentCreationPrecondition", MockFlowService.PAYMENT_INTENT_CREATION_PRECONDITION);
            request.put("sessionVersion", SESSION_VERSION);
            request.put("authorizationSetRef", AUTH_SET);
            request.put("expectedProjectionVersion", 1);
            request.put("expectedAggregateVersion", 1);
            MvcResult result = mvc.perform(post("/api/v1/orders/{orderRef}/payment-intents", orderRef)
                    .header(TestAccessTokenFilter.HEADER_NAME, TOKEN).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(request))).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            paymentIntentRef = json.readTree(result.getResponse().getContentAsString()).path("resourceRef").asText();
            assertThat(paymentIntentRef).startsWith("PI-");
        }
        return new Fixture(orderRef, commandId, idempotencyKey, paymentIntentRef);
    }

    private JsonNode query(Fixture fixture, String commandId, String idempotencyKey, String token,
                           String sessionVersion, String authorizationSetRef, String extraName) throws Exception {
        var request = get("/api/v1/orders/{orderRef}/payment-intents/result", fixture.orderRef());
        if (commandId != null) request.queryParam("commandId", commandId);
        if (idempotencyKey != null) request.queryParam("idempotencyKey", idempotencyKey);
        if (sessionVersion != null) request.queryParam("sessionVersion", sessionVersion);
        if (authorizationSetRef != null) request.queryParam("authorizationSetRef", authorizationSetRef);
        if (extraName != null) request.queryParam(extraName, "unexpected");
        if (token != null) request.header(TestAccessTokenFilter.HEADER_NAME, token);
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode response = json.readTree(result.getResponse().getContentAsString());
        assertStrictFields(response);
        return response;
    }

    private JsonNode invalidQuery(Fixture fixture, String variant) throws Exception {
        var request = get("/api/v1/orders/{orderRef}/payment-intents/result", fixture.orderRef())
                .header(TestAccessTokenFilter.HEADER_NAME, TOKEN);
        if (!"MISSING_COMMAND".equals(variant)) request.queryParam("commandId", fixture.commandId());
        if (!"MISSING_IDEMPOTENCY".equals(variant)) request.queryParam("idempotencyKey", fixture.idempotencyKey());
        if (!"MISSING_SESSION".equals(variant)) request.queryParam("sessionVersion",
                "INVALID_SESSION_TYPE".equals(variant) ? "abc" : String.valueOf(SESSION_VERSION));
        if (!"MISSING_SET".equals(variant)) request.queryParam("authorizationSetRef", AUTH_SET);
        if ("EXTRA_FIELD".equals(variant)) request.queryParam("paymentIntentRef", "PI-CLIENT-SUPPLIED");
        if ("DUPLICATE_COMMAND".equals(variant)) request.queryParam("commandId", "CMD-SECOND");
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode response = json.readTree(result.getResponse().getContentAsString());
        assertStrictFields(response);
        return response;
    }

    private static void assertStrictFields(JsonNode response) {
        Set<String> fields = new java.util.HashSet<>();
        response.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).isEqualTo(STRICT_FIELDS);
    }

    private static void assertUnknown(JsonNode response) {
        assertThat(response.path("outcome").asText()).isEqualTo("UNKNOWN");
        assertThat(response.path("projectCode").asText()).isEqualTo("PAYMENT_INTENT_RESULT_UNKNOWN");
        assertThat(response.path("retryClass").asText()).isEqualTo("SAME_ACTION_QUERY_ONLY");
        assertThat(response.path("resourceRef").isNull()).isTrue();
        assertThat(response.path("aggregateVersion").isNull()).isTrue();
        assertThat(response.path("currentProjection").isNull()).isTrue();
        assertThat(Instant.parse(response.path("nextPollAt").asText())).isNotNull();
    }

    private static void assertFound(JsonNode response, String paymentIntentRef) {
        assertThat(response.path("outcome").asText()).isEqualTo("ACCEPTED");
        assertThat(response.path("projectCode").asText()).isEqualTo("PAYMENT_INTENT_RESULT_FOUND");
        assertThat(response.path("resourceRef").asText()).isEqualTo(paymentIntentRef);
        assertThat(response.path("retryClass").asText()).isEqualTo("NONE");
        assertThat(response.path("nextPollAt").isNull()).isTrue();
        assertThat(response.path("currentProjection").path("intentScope").asText())
                .isEqualTo("LOCAL_SYNTHETIC_ONLY");
        assertThat(response.path("currentProjection").path("paymentInitiated").asBoolean()).isFalse();
        assertThat(response.path("currentProjection").path("paymentConfirmed").asBoolean()).isFalse();
    }

    private static void assertRejected(JsonNode response, String projectCode) {
        assertThat(response.path("outcome").asText()).isEqualTo("REJECTED");
        assertThat(response.path("projectCode").asText()).isEqualTo(projectCode);
        assertThat(response.path("resourceRef").isNull()).isTrue();
        assertThat(response.path("aggregateVersion").isNull()).isTrue();
        assertThat(response.path("currentProjection").isNull()).isTrue();
        assertThat(response.path("retryClass").asText()).isEqualTo("NONE");
        assertThat(response.path("nextPollAt").isNull()).isTrue();
    }

    private EvidenceSnapshot snapshot(String querySubject) {
        return new EvidenceSnapshot(store.paymentIntentEvidenceSnapshot(identity.projectSubjectRef()),
                service.paymentIntentResultQueryCallCountForTest(querySubject),
                service.paymentIntentPostCallCountForTest(identity.projectSubjectRef()));
    }

    private static void assertReadOnly(EvidenceSnapshot before, EvidenceSnapshot after, long expectedQueries) {
        assertThat(after.queryCalls() - before.queryCalls()).isEqualTo(expectedQueries);
        assertThat(after.paymentIntentPostCalls() - before.paymentIntentPostCalls()).isZero();
        assertThat(after.domain()).isEqualTo(before.domain());
    }

    private static void assertBusinessReadOnlyWithReviewDelta(EvidenceSnapshot before, EvidenceSnapshot after,
                                                               long expectedQueries, long expectedReviewDelta) {
        assertThat(after.queryCalls() - before.queryCalls()).isEqualTo(expectedQueries);
        assertThat(after.paymentIntentPostCalls() - before.paymentIntentPostCalls()).isZero();
        PaymentIntentEvidenceSnapshot left = before.domain();
        PaymentIntentEvidenceSnapshot right = after.domain();
        assertThat(right.orders() - left.orders()).isZero();
        assertThat(right.commands() - left.commands()).isZero();
        assertThat(right.commandAliases() - left.commandAliases()).isZero();
        assertThat(right.orderVersions() - left.orderVersions()).isZero();
        assertThat(right.projectionVersions() - left.projectionVersions()).isZero();
        assertThat(right.semanticActions() - left.semanticActions()).isZero();
        assertThat(right.paymentIntents() - left.paymentIntents()).isZero();
        assertThat(right.paymentIntentBusinessKeys() - left.paymentIntentBusinessKeys()).isZero();
        assertThat(right.downstream()).isEqualTo(left.downstream());
        assertThat(right.reviewSignals() - left.reviewSignals()).isEqualTo(expectedReviewDelta);
    }

    private void assertObserverDeltaOnly(EvidenceSnapshot before, EvidenceSnapshot after, String boundary) {
        ObjectNode delta = deltaJson(before, after);
        delta.fieldNames().forEachRemaining(name -> assertThat(delta.path(name).asLong())
                .as("observer delta " + name).isEqualTo(name.equals(boundary) ? 1 : 0));
    }

    private static void assertReviewSignal(PaymentIntentReviewSignal signal, PaymentIntentReviewReason reason,
                                           String requestSecretMarker) {
        assertThat(signal.reason()).isEqualTo(reason);
        assertThat(signal.reviewSignalRef()).startsWith("RS-");
        assertThat(signal.evidenceRef()).startsWith("EV-");
        assertThat(signal.caseRef()).startsWith("CASE-");
        assertThat(signal.relatedIntentRef()).startsWith("PIH-");
        assertThat(signal.toString()).doesNotContain(requestSecretMarker);
    }

    private void writeSensitivityEvidence(String boundary, EvidenceSnapshot before, EvidenceSnapshot after)
            throws Exception {
        ObjectNode node = baseEvidence("MAP003-BE-011", boundary);
        node.put("defectRef", "MAP003-P1-OBSERVATION-DENOMINATOR");
        node.putObject("input").put("controlledLocalWriteBoundary", boundary);
        node.putObject("expected").put("observedDelta", 1).put("externalCall", 0);
        node.set("response", json.createObjectNode().put("observer", boundary).put("observed", true));
        node.set("before", countJson(before));
        node.set("after", countJson(after));
        node.set("delta", deltaJson(before, after));
        node.putObject("actual").put("observedDelta", deltaJson(before, after).path(boundary).asLong());
        writeCaseFile(node, "MAP003-BE-011", boundary);
    }

    private void writeMyBatisMismatchEvidence(String variant, JsonNode response,
                                               EvidenceSnapshot domainBefore, EvidenceSnapshot domainAfter,
                                               long reviewBefore, long reviewAfter,
                                               PaymentIntentReviewSignal signal) throws Exception {
        ObjectNode node = baseEvidence("MAP003-BE-012", variant);
        ObjectNode input = node.putObject("input");
        input.put("persistenceContract", "MYBATIS_LOCAL_MOCK_ROW");
        input.put("realMySql", "NOT_RUN");
        input.put("mismatch", variant);
        ObjectNode expected = node.putObject("expected");
        expected.put("publicProjectCode", "PAYMENT_INTENT_QUERY_NOT_AVAILABLE");
        expected.put("businessWriteDelta", 0);
        expected.put("reviewEvidenceDelta", 1);
        node.set("response", response.deepCopy());
        node.set("before", countJson(domainBefore).put("MyBatisReviewSignalEvidence", reviewBefore));
        node.set("after", countJson(domainAfter).put("MyBatisReviewSignalEvidence", reviewAfter));
        ObjectNode delta = deltaJson(domainBefore, domainAfter);
        delta.put("MyBatisReviewSignalEvidence", reviewAfter - reviewBefore);
        node.set("delta", delta);
        ObjectNode actual = node.putObject("actual");
        actual.put("reviewSignalRef", signal.reviewSignalRef());
        actual.put("evidenceRef", signal.evidenceRef());
        actual.put("caseRef", signal.caseRef());
        actual.put("relatedIntentRef", signal.relatedIntentRef());
        actual.put("reason", signal.reason().name());
        writeCaseFile(node, "MAP003-BE-012", variant);
    }

    private ObjectNode baseEvidence(String map003CaseId, String parameterId) throws Exception {
        ObjectNode node = json.createObjectNode();
        node.put("scenarioId", MATRIX_SCENARIO);
        node.put("subcaseId", fixedSubcaseFor(map003CaseId));
        node.put("parameterId", parameterId);
        node.put("map003CaseId", map003CaseId);
        node.put("executionStatus", "PASS");
        node.put("executionRunId", RUN_ID);
        node.put("executedAt", EXECUTED_AT);
        node.put("evidencePackageRef", "D5-M2-MAP003-BE-LOCAL-SYNTHETIC");
        node.put("implementationAggregateSha256", implementationAggregate());
        ObjectNode d3 = node.putObject("d3Sha256");
        d3.put("D3-03", D3_03_SHA);
        d3.put("D3-05", D3_05_SHA);
        d3.put("D3-REGISTRY", D3_REGISTRY_SHA);
        node.put("evidencePackageSha256", "PENDING_AFTER_ALL_CASES");
        node.put("evidencePackageDigest", "PENDING_AFTER_ALL_CASES");
        return node;
    }

    private void writeCaseFile(ObjectNode node, String map003CaseId, String parameterId) throws Exception {
        String filename = (map003CaseId + "__" + parameterId).replaceAll("[^A-Za-z0-9._-]", "_") + ".json";
        Files.writeString(EVIDENCE_DIRECTORY.resolve(filename),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(node) + "\n", StandardCharsets.UTF_8);
    }

    private void writeEvidence(String subcaseId, String parameterId, JsonNode response,
                               EvidenceSnapshot before, EvidenceSnapshot after) throws Exception {
        writeEvidence(subcaseId, parameterId, response, before, after, 0);
    }

    private void writeEvidence(String map003CaseId, String parameterId, JsonNode response,
                               EvidenceSnapshot before, EvidenceSnapshot after,
                               long expectedReviewEvidenceDelta) throws Exception {
        ObjectNode node = json.createObjectNode();
        node.put("scenarioId", MATRIX_SCENARIO);
        node.put("subcaseId", fixedSubcaseFor(map003CaseId));
        node.put("parameterId", parameterId);
        node.put("map003CaseId", map003CaseId);
        node.put("executionStatus", "PASS");
        node.put("executionRunId", RUN_ID);
        node.put("executedAt", EXECUTED_AT);
        node.put("evidencePackageRef", "D5-M2-MAP003-BE-LOCAL-SYNTHETIC");
        node.put("implementationAggregateSha256", implementationAggregate());
        ObjectNode d3 = node.putObject("d3Sha256");
        d3.put("D3-03", D3_03_SHA);
        d3.put("D3-05", D3_05_SHA);
        d3.put("D3-REGISTRY", D3_REGISTRY_SHA);
        node.putObject("input").put("localSyntheticOnly", true);
        node.putObject("expected").put("strictFieldCount", 8).put("businessWriteDelta", 0)
                .put("reviewEvidenceDelta", expectedReviewEvidenceDelta)
                .put("queryCallDelta", after.queryCalls() - before.queryCalls());
        node.set("response", response.deepCopy());
        node.set("before", countJson(before));
        node.set("after", countJson(after));
        node.set("delta", deltaJson(before, after));
        node.putObject("actual").put("projectCode", response.path("projectCode").asText())
                .put("strictFieldCount", response.size()).set("fullResponse", response.deepCopy());
        node.put("evidencePackageSha256", "PENDING_AFTER_ALL_CASES");
        node.put("evidencePackageDigest", "PENDING_AFTER_ALL_CASES");
        String filename = (map003CaseId + "__" + parameterId).replaceAll("[^A-Za-z0-9._-]", "_") + ".json";
        Files.writeString(EVIDENCE_DIRECTORY.resolve(filename),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(node) + "\n", StandardCharsets.UTF_8);
    }

    private ObjectNode countJson(EvidenceSnapshot snapshot) {
        PaymentIntentEvidenceSnapshot domain = snapshot.domain();
        OrderCreationSideEffectSnapshot side = domain.downstream();
        ObjectNode node = json.createObjectNode();
        node.put("QueryCall", snapshot.queryCalls());
        node.put("PaymentIntentPostCall", snapshot.paymentIntentPostCalls());
        node.put("Order", domain.orders());
        node.put("Command", domain.commands());
        node.put("CommandAlias", domain.commandAliases());
        node.put("OrderVersion", domain.orderVersions());
        node.put("ProjectionVersion", domain.projectionVersions());
        node.put("SemanticAction", domain.semanticActions());
        node.put("PaymentIntent", domain.paymentIntents());
        node.put("PaymentIntentBusinessKey", domain.paymentIntentBusinessKeys());
        node.put("ReviewSignalEvidence", domain.reviewSignals());
        node.put("PaymentAttempt", side.paymentAttempts());
        node.put("WechatPrepay", side.wechatPrepayCalls());
        node.put("requestPayment", side.requestPaymentCalls());
        node.put("Notification", side.paymentNotifications());
        node.put("DispatchIntent", side.dispatchIntents());
        node.put("W", side.wechatPaymentFacts());
        node.put("U", side.upstreamDebitFacts());
        node.put("D", side.deliveryFacts());
        node.put("R", side.refundFacts());
        node.put("L", side.localLedgerFacts());
        node.put("LedgerEntry", side.ledgerEntries());
        node.put("ExternalCall", side.externalCalls());
        return node;
    }

    private ObjectNode deltaJson(EvidenceSnapshot before, EvidenceSnapshot after) {
        ObjectNode left = countJson(before);
        ObjectNode right = countJson(after);
        ObjectNode delta = json.createObjectNode();
        left.fieldNames().forEachRemaining(name -> delta.put(name,
                right.path(name).asLong() - left.path(name).asLong()));
        return delta;
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

    private static String fixedSubcaseFor(String map003CaseId) {
        return switch (map003CaseId) {
            case "MAP003-BE-001", "MAP003-BE-002", "MAP003-BE-003", "MAP003-BE-008",
                    "MAP003-BE-009", "MAP003-BE-010", "MAP003-BE-011", "MAP003-BE-012" ->
                    MATRIX_WRITE_UNKNOWN;
            default -> MATRIX_NETWORK_INTERRUPTED;
        };
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

    private record Fixture(String orderRef, String commandId, String idempotencyKey, String paymentIntentRef) {}
    private record EvidenceSnapshot(PaymentIntentEvidenceSnapshot domain, long queryCalls,
                                    long paymentIntentPostCalls) {}
}
