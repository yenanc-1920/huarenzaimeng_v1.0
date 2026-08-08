package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huarenzaimeng.api.config.TestAccessTokenFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory",
        "hz.test-access-token=local-synthetic-order-recovery-secret"
})
@AutoConfigureMockMvc
class OrderRecoveryApiContractTest {
    private static final String TOKEN = "local-synthetic-order-recovery-secret";
    private static final String EVIDENCE_VERSION = "SYN-EVIDENCE-V1";
    private static final String PRECONDITION = LocalSyntheticOrderRecoveryService.CREATION_PRECONDITION;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LocalSyntheticOrderRecoveryService recovery;
    @Autowired InMemoryFlowStore flowStore;

    @AfterEach void reset() { recovery.resetForTest(); }

    @Test void recoveredSessionListsOnlyExactAuthorizedSetAndReplayHasOneSideEffect() throws Exception {
        LocalSyntheticIdentity identity = identity();
        seed("SYN-O-1", identity.projectSubjectRef(), EVIDENCE_VERSION, "AWAITING_PAYMENT");
        seed("SYN-O-OTHER", identity.projectSubjectRef(), EVIDENCE_VERSION, "DELIVERED");
        recovery.installEvidenceForTest("SYN-MATERIAL-ALLOW-O1",
                new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, EVIDENCE_VERSION, List.of("SYN-O-1")));

        String body = request("CMD-REC-1", "IDEM-REC-1", "SYN-O-1", "SYN-MATERIAL-ALLOW-O1");
        JsonNode first = postRecovery(body)
                .andExpect(status().isOk())
                .andExpect(header().string("X-HZM-Mock-Semantics", "LOCAL_SYNTHETIC_NO_REAL_IDENTITY"))
                .andExpect(jsonPath("$.data.outcome").value("RECOVERED"))
                .andExpect(jsonPath("$.data.authorization.sessionRole").value("BUYER"))
                .andExpect(jsonPath("$.data.authorization.sessionVersion").value(1))
                .andExpect(jsonPath("$.data.authorization.authorizedOrderRefs[0]").value("SYN-O-1"))
                .andReturnBody();
        String caseRef = first.path("data").path("recoveryCaseRef").asText();
        String setRef = first.path("data").path("authorization").path("authorizationSetRef").asText();

        postRecovery(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryCaseRef").value(caseRef));
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.authorizationSetCountForTest()).isEqualTo(1);
        assertThat(recovery.orderCountForTest()).isEqualTo(2);
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isEqualTo(1);

        mvc.perform(withToken(get("/api/v1/orders")
                        .param("sessionVersion", "1").param("authorizationSetRef", setRef)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectSubjectRef").value(identity.projectSubjectRef()))
                .andExpect(jsonPath("$.data.sessionRole").value("BUYER"))
                .andExpect(jsonPath("$.data.sessionVersion").value(1))
                .andExpect(jsonPath("$.data.authorizationSetRef").value(setRef))
                .andExpect(jsonPath("$.data.authorizationEvidenceVersion").value(EVIDENCE_VERSION))
                .andExpect(jsonPath("$.data.authorizedOrderRefs.length()").value(1))
                .andExpect(jsonPath("$.data.orders.length()").value(1))
                .andExpect(jsonPath("$.data.orders[0].orderRef").value("SYN-O-1"))
                .andExpect(jsonPath("$.data.orders[0].stateCode").value("AWAITING_PAYMENT"));

        String changed = request("CMD-REC-1", "IDEM-REC-1", "SYN-O-OTHER", "SYN-MATERIAL-ALLOW-O1");
        postRecovery(changed).andExpect(status().isConflict())
                .andExpect(jsonPath("$.projectCode").value("IDEMPOTENCY_CONFLICT"));
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.authorizationSetCountForTest()).isEqualTo(1);
        assertThat(recovery.orderCountForTest()).isEqualTo(2);
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isEqualTo(1);
    }

    @Test void allElevenPublicStateCodesAreExactAndNoInternalStateEscapes() throws Exception {
        LocalSyntheticIdentity identity = identity();
        List<String> refs = new ArrayList<>();
        int index = 0;
        for (UserOrderStateCode state : UserOrderStateCode.values()) {
            String ref = "SYN-STATE-" + (++index);
            refs.add(ref);
            seed(ref, identity.projectSubjectRef(), EVIDENCE_VERSION, state.name());
        }
        recovery.installEvidenceForTest("SYN-MATERIAL-ALL-STATES",
                new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, EVIDENCE_VERSION, refs));
        postRecovery(request("CMD-STATES", "IDEM-STATES", refs.get(0), "SYN-MATERIAL-ALL-STATES"))
                .andExpect(status().isOk());

        String response = mvc.perform(withToken(get("/api/v1/orders")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders.length()").value(11))
                .andReturn().getResponse().getContentAsString();
        Set<String> actual = new HashSet<>();
        for (JsonNode order : json.readTree(response).path("data").path("orders")) {
            actual.add(order.path("stateCode").asText());
            assertThat(order.has("paymentState") || order.has("upstreamDebitState")
                    || order.has("deliveryState") || order.has("evidenceRef")).isFalse();
        }
        Set<String> expected = new HashSet<>();
        for (UserOrderStateCode value : UserOrderStateCode.values()) expected.add(value.name());
        assertThat(actual).isEqualTo(expected);
    }

    @Test void rejectedUnknownAndExistenceFailuresNeverExpandAuthorizationOrLeakOrderFacts() throws Exception {
        LocalSyntheticIdentity identity = identity();
        seed("SYN-O-EXISTS", identity.projectSubjectRef(), EVIDENCE_VERSION, "SUPPORT_REVIEW");
        recovery.installEvidenceForTest("SYN-MATERIAL-REJECT",
                new SyntheticRecoveryEvidence(RecoveryOutcome.REJECTED, null, List.of()));
        recovery.installEvidenceForTest("SYN-MATERIAL-UNKNOWN",
                new SyntheticRecoveryEvidence(RecoveryOutcome.UNKNOWN, null, List.of()));
        recovery.installEvidenceForTest("SYN-MATERIAL-ALLOW-EXISTS",
                new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, EVIDENCE_VERSION,
                        List.of("SYN-O-EXISTS")));

        String rejected = body(postRecovery(request("CMD-REJECT", "IDEM-REJECT", "SYN-O-EXISTS",
                "SYN-MATERIAL-REJECT")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.data.retryClass").value("NONE")));
        assertNoOrderLeak(rejected);

        String absent = body(postRecovery(request("CMD-ABSENT", "IDEM-ABSENT", "SYN-O-NOT-THERE",
                "SYN-MATERIAL-NOT-REGISTERED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("REJECTED")));
        String insufficient = body(postRecovery(request("CMD-INSUFFICIENT", "IDEM-INSUFFICIENT", "SYN-O-NOT-THERE",
                "SYN-MATERIAL-ALLOW-EXISTS")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("REJECTED")));
        assertNoOrderLeak(absent);
        assertNoOrderLeak(insufficient);

        JsonNode unknown = postRecovery(request("CMD-UNKNOWN", "IDEM-UNKNOWN", "SYN-O-EXISTS",
                "SYN-MATERIAL-UNKNOWN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.retryClass").value("READ_SAFE"))
                .andReturnBody();
        String unknownBody = unknown.toString();
        assertNoOrderLeak(unknownBody);
        String caseRef = unknown.path("data").path("recoveryCaseRef").asText();
        mvc.perform(withToken(get("/api/v1/recovery-cases/{ref}", caseRef)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.outcome").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.recoveryCaseRef").value(caseRef));
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isZero();
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        mvc.perform(withToken(get("/api/v1/orders"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.projectCode").value("ORDER_LIST_NOT_AVAILABLE"));
    }

    @Test void staleWrongSetForeignOrderAndUnknownStateFailClosedWithoutPartialList() throws Exception {
        LocalSyntheticIdentity identity = identity();
        seed("SYN-O-1", identity.projectSubjectRef(), EVIDENCE_VERSION, "AWAITING_PAYMENT");
        seed("SYN-O-2", identity.projectSubjectRef(), EVIDENCE_VERSION, "DELIVERED");
        seed("SYN-O-FOREIGN", "SYN-SUBJECT-FOREIGN", EVIDENCE_VERSION, "DELIVERED");
        recovery.installEvidenceForTest("SYN-MATERIAL-O1",
                new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, EVIDENCE_VERSION, List.of("SYN-O-1")));
        JsonNode first = postRecovery(request("CMD-FIRST", "IDEM-FIRST", "SYN-O-1", "SYN-MATERIAL-O1"))
                .andExpect(status().isOk()).andReturnBody();
        String firstSet = first.path("data").path("authorization").path("authorizationSetRef").asText();

        mvc.perform(withToken(get("/api/v1/orders").param("sessionVersion", "0")
                        .param("authorizationSetRef", firstSet)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(withToken(get("/api/v1/orders").param("sessionVersion", "1")
                        .param("authorizationSetRef", "AS-SYN-WRONG")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").doesNotExist());

        recovery.appendOrderRefForTest(firstSet, "SYN-O-FOREIGN");
        mvc.perform(withToken(get("/api/v1/orders")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").doesNotExist());

        recovery.resetForTest();
        seed("SYN-O-1", identity.projectSubjectRef(), EVIDENCE_VERSION, "AWAITING_PAYMENT");
        seed("SYN-O-2", identity.projectSubjectRef(), EVIDENCE_VERSION, "DELIVERED");
        recovery.installEvidenceForTest("SYN-MATERIAL-TWO",
                new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, EVIDENCE_VERSION,
                        List.of("SYN-O-1", "SYN-O-2")));
        postRecovery(request("CMD-TWO", "IDEM-TWO", "SYN-O-1", "SYN-MATERIAL-TWO"))
                .andExpect(status().isOk());
        recovery.replaceOrderStateForTest("SYN-O-2", "THIRD_PARTY_SUCCESSFUL");
        mvc.perform(withToken(get("/api/v1/orders")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test void rejectedAndUnknownDoNotExpandGuestAndOnlyOneGuestToBuyerRecoveryIsAllowed() throws Exception {
        LocalSyntheticIdentity identity = identity();
        seed("SYN-O-1", identity.projectSubjectRef(), "SYN-EV-1", "AWAITING_PAYMENT");
        seed("SYN-O-2", identity.projectSubjectRef(), "SYN-EV-2", "PAYMENT_PROCESSING");
        recovery.installEvidenceForTest("SYN-MAT-1",
                new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, "SYN-EV-1", List.of("SYN-O-1")));
        recovery.installEvidenceForTest("SYN-MAT-2",
                new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, "SYN-EV-2", List.of("SYN-O-2")));
        recovery.installEvidenceForTest("SYN-MAT-REJECT",
                new SyntheticRecoveryEvidence(RecoveryOutcome.REJECTED, null, List.of()));
        recovery.installEvidenceForTest("SYN-MAT-UNKNOWN",
                new SyntheticRecoveryEvidence(RecoveryOutcome.UNKNOWN, null, List.of()));
        recovery.installAuthoritativeResultForTest("SYN-AUTH-V1-REJECTED",
                new SyntheticRecoveryEvidence(RecoveryOutcome.REJECTED, null, List.of()));

        postRecovery(request("CMD-V1-R", "IDEM-V1-R", "SYN-O-1", "SYN-MAT-REJECT"))
                .andExpect(jsonPath("$.data.outcome").value("REJECTED"));
        JsonNode unknown = postRecovery(request("CMD-V1-U", "IDEM-V1-U", "SYN-O-1", "SYN-MAT-UNKNOWN"))
                .andExpect(jsonPath("$.data.outcome").value("UNKNOWN")).andReturnBody();
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isZero();
        postAuthoritativeResult(unknown.path("data").path("recoveryCaseRef").asText(),
                "{\"authoritativeResultRef\":\"SYN-AUTH-V1-REJECTED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("REJECTED"));

        JsonNode recovered = postRecovery(request("CMD-V1", "IDEM-V1", "SYN-O-1", "SYN-MAT-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorization.sessionVersion").value(1)).andReturnBody();
        String setRef = recovered.path("data").path("authorization").path("authorizationSetRef").asText();
        postRecovery(request("CMD-V2", "IDEM-V2", "SYN-O-2", "SYN-MAT-2"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.projectCode").value("ORDER_RECOVERY_NOT_ELIGIBLE"));
        mvc.perform(withToken(get("/api/v1/orders").param("sessionVersion", "1")
                        .param("authorizationSetRef", setRef)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.orders.length()").value(1))
                .andExpect(jsonPath("$.data.orders[0].orderRef").value("SYN-O-1"));
        assertThat(recovery.authorizationSetCountForTest()).isEqualTo(1);
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isEqualTo(1);
    }

    @Test void endpointFaultAndMissingTestIdentityFailClosedWithoutCaseOrSessionMutation() throws Exception {
        LocalSyntheticIdentity identity = identity();
        recovery.installFaultForTest("SYN-MATERIAL-FAULT");
        postRecovery(request("CMD-FAULT", "IDEM-FAULT", "SYN-O-X", "SYN-MATERIAL-FAULT"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.projectCode").value("RECOVERY_TEMPORARILY_UNAVAILABLE"));
        assertThat(recovery.recoveryCaseCountForTest()).isZero();
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isZero();

        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/recovery-cases").contentType(MediaType.APPLICATION_JSON)
                        .content(request("CMD-NO-TOKEN", "IDEM-NO-TOKEN", "SYN-O-X", "SYN-MAT-X")))
                .andExpect(status().isUnauthorized());
    }

    @Test void freshSubjectOrderQueryAndInvalidPublicOrderFieldsFailClosedWithoutAnyMutation() throws Exception {
        LocalSyntheticIdentity identity = identity();
        assertThat(recovery.sessionCountForTest()).isZero();
        assertThat(recovery.recoveryCaseCountForTest()).isZero();
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.orderCountForTest()).isZero();
        assertThat(recovery.totalSessionTransitionCountForTest()).isZero();
        assertThat(flowStore.sideEffectSnapshot(identity.projectSubjectRef()).total()).isZero();
        mvc.perform(withToken(get("/api/v1/orders")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.projectCode").value("ORDER_LIST_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(recovery.sessionCountForTest()).isZero();
        assertThat(recovery.recoveryCaseCountForTest()).isZero();
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.orderCountForTest()).isZero();
        assertThat(recovery.totalSessionTransitionCountForTest()).isZero();
        assertThat(flowStore.sideEffectSnapshot(identity.projectSubjectRef()).total()).isZero();

        List<SyntheticOrderRecord> invalid = List.of(
                new SyntheticOrderRecord("", identity.projectSubjectRef(), EVIDENCE_VERSION,
                        LocalSyntheticOrderRecoveryService.ENVIRONMENT, "DELIVERED", 1L,
                        Instant.parse("2026-08-01T12:00:00Z"), true),
                new SyntheticOrderRecord("SYN-O-FIELDS", identity.projectSubjectRef(), EVIDENCE_VERSION,
                        LocalSyntheticOrderRecoveryService.ENVIRONMENT, null, 1L,
                        Instant.parse("2026-08-01T12:00:00Z"), true),
                new SyntheticOrderRecord("SYN-O-FIELDS", identity.projectSubjectRef(), EVIDENCE_VERSION,
                        LocalSyntheticOrderRecoveryService.ENVIRONMENT, "", 1L,
                        Instant.parse("2026-08-01T12:00:00Z"), true),
                new SyntheticOrderRecord("SYN-O-FIELDS", identity.projectSubjectRef(), EVIDENCE_VERSION,
                        LocalSyntheticOrderRecoveryService.ENVIRONMENT, "INTERNAL_SETTLED", 1L,
                        Instant.parse("2026-08-01T12:00:00Z"), true),
                new SyntheticOrderRecord("SYN-O-FIELDS", identity.projectSubjectRef(), EVIDENCE_VERSION,
                        LocalSyntheticOrderRecoveryService.ENVIRONMENT, "DELIVERED", 0L,
                        Instant.parse("2026-08-01T12:00:00Z"), true),
                new SyntheticOrderRecord("SYN-O-FIELDS", identity.projectSubjectRef(), EVIDENCE_VERSION,
                        LocalSyntheticOrderRecoveryService.ENVIRONMENT, "DELIVERED", 1L, null, true));
        for (SyntheticOrderRecord replacement : invalid) {
            recovery.resetForTest();
            seed("SYN-O-FIELDS", identity.projectSubjectRef(), EVIDENCE_VERSION, "DELIVERED");
            recovery.installEvidenceForTest("SYN-MAT-FIELDS",
                    new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, EVIDENCE_VERSION,
                            List.of("SYN-O-FIELDS")));
            postRecovery(request("CMD-FIELDS", "IDEM-FIELDS", "SYN-O-FIELDS", "SYN-MAT-FIELDS"))
                    .andExpect(status().isOk());
            long casesBefore = recovery.recoveryCaseCountForTest();
            long setsBefore = recovery.authorizationSetCountForTest();
            long ordersBefore = recovery.orderCountForTest();
            long transitionsBefore = recovery.totalSessionTransitionCountForTest();
            recovery.replaceOrderForTest("SYN-O-FIELDS", replacement);
            mvc.perform(withToken(get("/api/v1/orders")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.projectCode").value("ORDER_LIST_NOT_AVAILABLE"))
                    .andExpect(jsonPath("$.data").doesNotExist());
            assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(casesBefore);
            assertThat(recovery.authorizationSetCountForTest()).isEqualTo(setsBefore);
            assertThat(recovery.orderCountForTest()).isEqualTo(ordersBefore);
            assertThat(recovery.totalSessionTransitionCountForTest()).isEqualTo(transitionsBefore);
            assertThat(flowStore.sideEffectSnapshot(identity.projectSubjectRef()).total()).isZero();
        }
    }

    @Test void activeUnknownWindowRejectsChangedInputUntilOriginalCaseTerminatesAndIsSubjectScoped()
            throws Exception {
        LocalSyntheticIdentity identity = identity();
        LocalSyntheticIdentity other = new LocalSyntheticIdentity("SYN-SUBJECT-OTHER", "SYN-SESSION-OTHER");
        recovery.installEvidenceForTest("SYN-MAT-UNKNOWN-A",
                new SyntheticRecoveryEvidence(RecoveryOutcome.UNKNOWN, null, List.of()));
        recovery.installEvidenceForTest("SYN-MAT-UNKNOWN-B",
                new SyntheticRecoveryEvidence(RecoveryOutcome.UNKNOWN, null, List.of()));
        recovery.installAuthoritativeResultForTest("SYN-AUTH-WINDOW-REJECTED",
                new SyntheticRecoveryEvidence(RecoveryOutcome.REJECTED, null, List.of()));

        JsonNode first = postRecovery(request("CMD-U-A1", "IDEM-U-A1", "SYN-O-X", "SYN-MAT-UNKNOWN-A"))
                .andExpect(status().isOk()).andReturnBody();
        String canonicalRef = first.path("data").path("recoveryCaseRef").asText();
        postRecovery(request("CMD-U-A2", "IDEM-U-A2", "SYN-O-X", "SYN-MAT-UNKNOWN-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryCaseRef").value(canonicalRef));
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.sessionCountForTest()).isEqualTo(1);
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.orderCountForTest()).isZero();
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isZero();
        assertThat(flowStore.sideEffectSnapshot(identity.projectSubjectRef()).total()).isZero();

        long sessionsBefore = recovery.sessionCountForTest();
        postRecovery(request("CMD-U-B", "IDEM-U-B", "SYN-O-X", "SYN-MAT-UNKNOWN-B"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.projectCode").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.sessionCountForTest()).isEqualTo(sessionsBefore);
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.orderCountForTest()).isZero();
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isZero();
        assertThat(flowStore.sideEffectSnapshot(identity.projectSubjectRef()).total()).isZero();

        postRecovery(request("CMD-U-A2", "IDEM-U-A2", "SYN-O-Y", "SYN-MAT-UNKNOWN-A"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.projectCode").value("IDEMPOTENCY_CONFLICT"));
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.sessionCountForTest()).isEqualTo(sessionsBefore);
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.orderCountForTest()).isZero();
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isZero();
        assertThat(flowStore.sideEffectSnapshot(identity.projectSubjectRef()).total()).isZero();

        RecoveryCaseResponse otherCase = createDirect(other,
                command("CMD-U-OTHER", "IDEM-U-OTHER", "SYN-O-X", "SYN-MAT-UNKNOWN-B"));
        assertThat(otherCase.outcome()).isEqualTo(RecoveryOutcome.UNKNOWN);
        assertThat(otherCase.recoveryCaseRef()).isNotEqualTo(canonicalRef);
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(2);
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.totalSessionTransitionCountForTest()).isZero();

        postAuthoritativeResult(canonicalRef,
                "{\"authoritativeResultRef\":\"SYN-AUTH-WINDOW-REJECTED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("REJECTED"));
        postRecovery(request("CMD-U-B", "IDEM-U-B", "SYN-O-X", "SYN-MAT-UNKNOWN-B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.recoveryCaseRef").value(org.hamcrest.Matchers.not(canonicalRef)));
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(3);
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.totalSessionTransitionCountForTest()).isZero();
        assertThat(flowStore.sideEffectSnapshot(identity.projectSubjectRef()).total()).isZero();
    }

    @Test void lateTrustedResultConvergesUnknownExactlyOnceAndOnlyRecoveredMigratesSession() throws Exception {
        LocalSyntheticIdentity identity = identity();
        seed("SYN-O-LATE", identity.projectSubjectRef(), EVIDENCE_VERSION, "PAYMENT_PROCESSING");
        recovery.installEvidenceForTest("SYN-MAT-LATE",
                new SyntheticRecoveryEvidence(RecoveryOutcome.UNKNOWN, null, List.of()));
        recovery.installAuthoritativeResultForTest("SYN-AUTH-RECOVERED",
                new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, EVIDENCE_VERSION,
                        List.of("SYN-O-LATE")));
        recovery.installAuthoritativeResultForTest("SYN-AUTH-CONFLICT",
                new SyntheticRecoveryEvidence(RecoveryOutcome.REJECTED, null, List.of()));

        String originalBody = request("CMD-LATE", "IDEM-LATE", "SYN-O-LATE", "SYN-MAT-LATE");
        JsonNode unknown = postRecovery(originalBody).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("UNKNOWN")).andReturnBody();
        String caseRef = unknown.path("data").path("recoveryCaseRef").asText();
        String convergeBody = "{\"authoritativeResultRef\":\"SYN-AUTH-RECOVERED\"}";
        JsonNode recovered = postAuthoritativeResult(caseRef, convergeBody).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryCaseRef").value(caseRef))
                .andExpect(jsonPath("$.data.outcome").value("RECOVERED"))
                .andExpect(jsonPath("$.data.authorization.sessionVersion").value(1)).andReturnBody();
        String setRef = recovered.path("data").path("authorization").path("authorizationSetRef").asText();
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.authorizationSetCountForTest()).isEqualTo(1);
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isEqualTo(1);

        postAuthoritativeResult(caseRef, convergeBody).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorization.authorizationSetRef").value(setRef));
        postRecovery(originalBody).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("RECOVERED"))
                .andExpect(jsonPath("$.data.authorization.authorizationSetRef").value(setRef));
        postAuthoritativeResult(caseRef, "{\"authoritativeResultRef\":\"SYN-AUTH-CONFLICT\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.projectCode").value("RECOVERY_RESULT_CONFLICT"));
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.authorizationSetCountForTest()).isEqualTo(1);
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isEqualTo(1);

        recovery.resetForTest();
        recovery.installEvidenceForTest("SYN-MAT-REJECT-LATE",
                new SyntheticRecoveryEvidence(RecoveryOutcome.UNKNOWN, null, List.of()));
        recovery.installAuthoritativeResultForTest("SYN-AUTH-REJECTED",
                new SyntheticRecoveryEvidence(RecoveryOutcome.REJECTED, null, List.of()));
        JsonNode secondUnknown = postRecovery(request("CMD-LATE-R", "IDEM-LATE-R", "SYN-O-HIDDEN",
                "SYN-MAT-REJECT-LATE")).andExpect(status().isOk()).andReturnBody();
        String rejectedCaseRef = secondUnknown.path("data").path("recoveryCaseRef").asText();
        String rejectedBody = "{\"authoritativeResultRef\":\"SYN-AUTH-REJECTED\"}";
        postAuthoritativeResult(rejectedCaseRef, rejectedBody).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.data.authorization").doesNotExist());
        postAuthoritativeResult(rejectedCaseRef, rejectedBody).andExpect(status().isOk());
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isZero();

        recovery.resetForTest();
        recovery.installEvidenceForTest("SYN-MAT-RACE-WINDOW-A",
                new SyntheticRecoveryEvidence(RecoveryOutcome.UNKNOWN, null, List.of()));
        recovery.installEvidenceForTest("SYN-MAT-RACE-WINDOW-B",
                new SyntheticRecoveryEvidence(RecoveryOutcome.UNKNOWN, null, List.of()));
        LocalSyntheticOrderRecoveryService.RecoveryCommand windowA = command("CMD-RACE-WINDOW-A",
                "IDEM-RACE-WINDOW-A", "SYN-O-RACE-A", "SYN-MAT-RACE-WINDOW-A");
        LocalSyntheticOrderRecoveryService.RecoveryCommand windowB = command("CMD-RACE-WINDOW-B",
                "IDEM-RACE-WINDOW-B", "SYN-O-RACE-B", "SYN-MAT-RACE-WINDOW-B");
        List<RaceAttempt> changedMaterial = raceCapturing(
                () -> createDirect(identity, windowA), () -> createDirect(identity, windowB));
        assertThat(changedMaterial.stream().filter(result -> result.response() != null).count()).isEqualTo(1);
        assertThat(changedMaterial.stream()
                .filter(result -> "IDEMPOTENCY_CONFLICT".equals(result.error())).count()).isEqualTo(1);
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.sessionCountForTest()).isEqualTo(1);
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.orderCountForTest()).isZero();
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isZero();
        assertThat(flowStore.sideEffectSnapshot(identity.projectSubjectRef()).total()).isZero();
    }

    @Test void controlledMemorySchedulingKeepsOneCanonicalCaseAndAtMostOneSessionMigration() throws Exception {
        LocalSyntheticIdentity identity = identity();
        recovery.installEvidenceForTest("SYN-MAT-RACE-UNKNOWN",
                new SyntheticRecoveryEvidence(RecoveryOutcome.UNKNOWN, null, List.of()));
        LocalSyntheticOrderRecoveryService.RecoveryCommand unknownA = command("CMD-RACE-A", "IDEM-RACE-A",
                "SYN-O-RACE", "SYN-MAT-RACE-UNKNOWN");
        LocalSyntheticOrderRecoveryService.RecoveryCommand unknownB = command("CMD-RACE-B", "IDEM-RACE-B",
                "SYN-O-RACE", "SYN-MAT-RACE-UNKNOWN");
        List<RecoveryCaseResponse> unknownResults = race(
                () -> createDirect(identity, unknownA), () -> createDirect(identity, unknownB));
        assertThat(unknownResults).extracting(RecoveryCaseResponse::recoveryCaseRef).containsOnly(
                unknownResults.get(0).recoveryCaseRef());
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isZero();

        recovery.resetForTest();
        seed("SYN-O-RACE", identity.projectSubjectRef(), EVIDENCE_VERSION, "AWAITING_PAYMENT");
        recovery.installEvidenceForTest("SYN-MAT-RACE-RECOVERED",
                new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, EVIDENCE_VERSION,
                        List.of("SYN-O-RACE")));
        LocalSyntheticOrderRecoveryService.RecoveryCommand sameCommand = command("CMD-RACE-SAME",
                "IDEM-RACE-SAME", "SYN-O-RACE", "SYN-MAT-RACE-RECOVERED");
        List<RecoveryCaseResponse> replayResults = race(
                () -> createDirect(identity, sameCommand), () -> createDirect(identity, sameCommand));
        assertThat(replayResults).extracting(RecoveryCaseResponse::recoveryCaseRef).containsOnly(
                replayResults.get(0).recoveryCaseRef());
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.authorizationSetCountForTest()).isEqualTo(1);
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isEqualTo(1);

        recovery.resetForTest();
        recovery.installEvidenceForTest("SYN-MAT-RACE-CONFLICT-A",
                new SyntheticRecoveryEvidence(RecoveryOutcome.UNKNOWN, null, List.of()));
        recovery.installEvidenceForTest("SYN-MAT-RACE-CONFLICT-B",
                new SyntheticRecoveryEvidence(RecoveryOutcome.UNKNOWN, null, List.of()));
        LocalSyntheticOrderRecoveryService.RecoveryCommand conflictA = command("CMD-RACE-CONFLICT",
                "IDEM-RACE-CONFLICT", "SYN-O-RACE-A", "SYN-MAT-RACE-CONFLICT-A");
        LocalSyntheticOrderRecoveryService.RecoveryCommand conflictB = command("CMD-RACE-CONFLICT",
                "IDEM-RACE-CONFLICT", "SYN-O-RACE-B", "SYN-MAT-RACE-CONFLICT-B");
        List<RaceAttempt> conflicts = raceCapturing(
                () -> createDirect(identity, conflictA), () -> createDirect(identity, conflictB));
        assertThat(conflicts.stream().filter(result -> result.response() != null).count()).isEqualTo(1);
        assertThat(conflicts.stream().filter(result -> "IDEMPOTENCY_CONFLICT".equals(result.error())).count())
                .isEqualTo(1);
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.authorizationSetCountForTest()).isZero();
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isZero();
    }

    @Test void buyerCannotStartAnotherRecoveryOrExpandAuthorization() throws Exception {
        LocalSyntheticIdentity identity = identity();
        seed("SYN-O-BUYER-1", identity.projectSubjectRef(), EVIDENCE_VERSION, "AWAITING_PAYMENT");
        seed("SYN-O-BUYER-2", identity.projectSubjectRef(), EVIDENCE_VERSION, "DELIVERED");
        recovery.installEvidenceForTest("SYN-MAT-BUYER-1",
                new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, EVIDENCE_VERSION,
                        List.of("SYN-O-BUYER-1")));
        recovery.installEvidenceForTest("SYN-MAT-BUYER-2",
                new SyntheticRecoveryEvidence(RecoveryOutcome.RECOVERED, EVIDENCE_VERSION,
                        List.of("SYN-O-BUYER-2")));
        JsonNode first = postRecovery(request("CMD-BUYER-1", "IDEM-BUYER-1", "SYN-O-BUYER-1",
                "SYN-MAT-BUYER-1")).andExpect(status().isOk()).andReturnBody();
        String setRef = first.path("data").path("authorization").path("authorizationSetRef").asText();

        postRecovery(request("CMD-BUYER-2", "IDEM-BUYER-2", "SYN-O-BUYER-2", "SYN-MAT-BUYER-2"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.projectCode").value("ORDER_RECOVERY_NOT_ELIGIBLE"));
        assertThat(recovery.recoveryCaseCountForTest()).isEqualTo(1);
        assertThat(recovery.authorizationSetCountForTest()).isEqualTo(1);
        assertThat(recovery.sessionTransitionCountForTest(identity.sessionRef())).isEqualTo(1);
        mvc.perform(withToken(get("/api/v1/orders")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizationSetRef").value(setRef))
                .andExpect(jsonPath("$.data.authorizedOrderRefs.length()").value(1))
                .andExpect(jsonPath("$.data.authorizedOrderRefs[0]").value("SYN-O-BUYER-1"));
    }

    @Test void nonLocalProfileCannotUseSyntheticSessionEvenWithSyntheticLookingAttributes() {
        MockEnvironment release = new MockEnvironment();
        release.setActiveProfiles("release-mysql");
        LocalSyntheticOrderRecoveryService blocked = new LocalSyntheticOrderRecoveryService(
                Clock.systemUTC(), release, "in-memory");

        assertThatThrownBy(() -> blocked.listOrders("LOCAL_SYNTHETIC", "SYN-SUBJECT-FORGED",
                "SYN-SESSION-FORGED", null, null))
                .isInstanceOf(FlowRejectedException.class)
                .hasMessage("LOCAL_SYNTHETIC_IDENTITY_REQUIRED");
    }

    private LocalSyntheticIdentity identity() { return LocalSyntheticIdentity.fromToken(TOKEN); }

    private void seed(String orderRef, String subject, String evidenceVersion, String stateCode) {
        recovery.installOrderForTest(new SyntheticOrderRecord(orderRef, subject, evidenceVersion,
                LocalSyntheticOrderRecoveryService.ENVIRONMENT, stateCode, 1L,
                Instant.parse("2026-08-01T12:00:00Z"), true));
    }

    private Result postRecovery(String body) throws Exception {
        return new Result(mvc.perform(withToken(post("/api/v1/recovery-cases"))
                .contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private Result postAuthoritativeResult(String caseRef, String body) throws Exception {
        return new Result(mvc.perform(withToken(post(
                        "/api/v1/local-synthetic/recovery-cases/{ref}/authoritative-result", caseRef))
                .contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private LocalSyntheticOrderRecoveryService.RecoveryCommand command(String commandId, String idempotencyKey,
                                                                        String orderRef, String materialRef) {
        return new LocalSyntheticOrderRecoveryService.RecoveryCommand(commandId, idempotencyKey,
                LocalSyntheticOrderRecoveryService.recoveryInputFingerprint(orderRef, materialRef),
                PRECONDITION, orderRef, materialRef);
    }

    private RecoveryCaseResponse createDirect(LocalSyntheticIdentity identity,
                                               LocalSyntheticOrderRecoveryService.RecoveryCommand command) {
        return recovery.createRecoveryCase(LocalSyntheticOrderRecoveryService.ENVIRONMENT,
                identity.projectSubjectRef(), identity.sessionRef(), command);
    }

    @SafeVarargs
    private final List<RecoveryCaseResponse> race(Callable<RecoveryCaseResponse>... calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(calls.length);
        CountDownLatch ready = new CountDownLatch(calls.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RecoveryCaseResponse>> futures = new ArrayList<>();
            for (Callable<RecoveryCaseResponse> call : calls) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return call.call();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<RecoveryCaseResponse> results = new ArrayList<>();
            for (Future<RecoveryCaseResponse> future : futures) results.add(future.get(5, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @SafeVarargs
    private final List<RaceAttempt> raceCapturing(Callable<RecoveryCaseResponse>... calls) throws Exception {
        List<Callable<RaceAttempt>> wrapped = new ArrayList<>();
        for (Callable<RecoveryCaseResponse> call : calls) {
            wrapped.add(() -> {
                try {
                    return new RaceAttempt(call.call(), null);
                } catch (FlowRejectedException error) {
                    return new RaceAttempt(null, error.getMessage());
                }
            });
        }
        ExecutorService executor = Executors.newFixedThreadPool(wrapped.size());
        CountDownLatch ready = new CountDownLatch(wrapped.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RaceAttempt>> futures = new ArrayList<>();
            for (Callable<RaceAttempt> call : wrapped) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return call.call();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<RaceAttempt> results = new ArrayList<>();
            for (Future<RaceAttempt> future : futures) results.add(future.get(5, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private record RaceAttempt(RecoveryCaseResponse response, String error) {}

    private static <T extends org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> T withToken(T request) {
        request.header(TestAccessTokenFilter.HEADER_NAME, TOKEN);
        return request;
    }

    private static String request(String commandId, String idempotencyKey, String orderRef, String materialRef) {
        String fingerprint = LocalSyntheticOrderRecoveryService.recoveryInputFingerprint(orderRef, materialRef);
        return "{\"commandId\":\"" + commandId + "\",\"idempotencyKey\":\"" + idempotencyKey
                + "\",\"recoveryInputFingerprint\":\"" + fingerprint
                + "\",\"creationPrecondition\":\"" + PRECONDITION + "\",\"orderRef\":\"" + orderRef
                + "\",\"recoveryMaterialRef\":\"" + materialRef + "\"}";
    }

    private static void assertNoOrderLeak(String body) {
        assertThat(body).doesNotContain("authorization\"").doesNotContain("orders\"")
                .doesNotContain("amount").doesNotContain("phone");
    }

    private static String body(Result result) throws Exception { return result.andReturnBody().toString(); }

    private final class Result {
        private final org.springframework.test.web.servlet.ResultActions actions;
        Result(org.springframework.test.web.servlet.ResultActions actions) { this.actions = actions; }
        Result andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher); return this;
        }
        JsonNode andReturnBody() throws Exception {
            return json.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }
}
