package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huarenzaimeng.api.config.TestAccessTokenFilter;
import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.ProjectProjection;
import com.huarenzaimeng.core.Quote;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory",
        "hz.test-access-token=local-synthetic-order-creation-secret"
})
@AutoConfigureMockMvc
class OrderCreationApiContractTest {
    private static final String TOKEN = "local-synthetic-order-creation-secret";
    private static final long SESSION_VERSION = 7L;
    private static final String AUTHORIZATION_SET_REF = "SYN-AS-M1";
    private static final String AUTHORIZATION_EVIDENCE_VERSION = "SYN-AUTH-EVIDENCE-M1";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MockFlowService service;
    @Autowired LocalSyntheticOrderRecoveryService recovery;
    @Autowired InMemoryFlowStore store;
    @Autowired InMemoryCatalogStore catalog;
    @Autowired Clock clock;

    private LocalSyntheticIdentity identity;

    @BeforeEach void prepareBuyer() {
        identity = LocalSyntheticIdentity.fromToken(TOKEN);
        catalog.resetForTest();
        recovery.resetForTest();
        recovery.installBuyerAuthorizationForTest(identity.projectSubjectRef(), identity.sessionRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF, AUTHORIZATION_EVIDENCE_VERSION);
    }

    @AfterEach void resetFixtures() {
        catalog.resetForTest();
        recovery.resetForTest();
    }

    @Test void firstCreationIsExactlyOneAndSameFingerprintRekeysReplayOriginal() throws Exception {
        Quote quote = quoteFor(identity.projectSubjectRef());
        long ordersBefore = store.orderCountForTest(identity.projectSubjectRef());
        long businessKeysBefore = store.orderBusinessKeyCountForTest();
        OrderCreationSideEffectSnapshot effectsBefore = effects();

        JsonNode first = body(postOrder(request("CMD-M1-FIRST", "IDEM-M1-FIRST", quote.quoteRef(),
                        SESSION_VERSION, AUTHORIZATION_SET_REF)))
                .path("currentProjection");
        String orderRef = first.path("orderRef").asText();
        assertThat(orderRef).isNotBlank();

        postOrder(request("CMD-M1-REKEY", "IDEM-M1-REKEY", quote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCode").value("ORDER_REPLAYED"))
                .andExpect(jsonPath("$.resourceRef").value(orderRef))
                .andExpect(jsonPath("$.currentProjection.orderRef").value(orderRef));

        assertThat(store.orderCountForTest(identity.projectSubjectRef()) - ordersBefore).isEqualTo(1);
        assertThat(store.orderBusinessKeyCountForTest() - businessKeysBefore).isEqualTo(1);
        ProjectProjection projection = service.getOrder(identity.projectSubjectRef(), orderRef);
        assertThat(projection.facts().payment()).isEqualTo("ABSENT_CONFIRMED");
        assertThat(projection.facts().upstreamDebit()).isEqualTo("ABSENT_CONFIRMED");
        assertThat(projection.facts().delivery()).isEqualTo("ABSENT_CONFIRMED");
        assertThat(projection.facts().refund()).isEqualTo("ABSENT_CONFIRMED");
        assertThat(projection.projectionVersion()).isEqualTo(1);
        assertThat(projection.aggregateVersion()).isEqualTo(1);
        assertThat(catalog.mutationCount()).isZero();
        assertThat(effects()).isEqualTo(effectsBefore);
    }

    @Test void normativeResponseBindsResourceProjectionSnapshotAndAllowedAction() throws Exception {
        Quote quote = quoteFor(identity.projectSubjectRef());
        String responseBody = postOrder(request("CMD-M1-RESPONSE", "IDEM-M1-RESPONSE", quote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF))
                .andExpect(status().isOk())
                .andExpect(header().string("X-HZM-Mock-Semantics", "LOCAL_SYNTHETIC_NO_EXTERNAL_FACTS"))
                .andExpect(jsonPath("$.requestRef").value("CMD-M1-RESPONSE"))
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.projectCode").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.retryClass").value("NONE"))
                .andExpect(jsonPath("$.aggregateVersion").value(1))
                .andExpect(jsonPath("$.resourceRef").isNotEmpty())
                .andExpect(jsonPath("$.currentProjection.orderRef").isNotEmpty())
                .andExpect(jsonPath("$.currentProjection.quoteRef").value(quote.quoteRef()))
                .andExpect(jsonPath("$.currentProjection.stateCode").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.currentProjection.priceSnapshot.totalAmountMinor").value(1000))
                .andExpect(jsonPath("$.currentProjection.priceSnapshot.currency").value("CNY"))
                .andExpect(jsonPath("$.currentProjection.projectionVersion").value(1))
                .andExpect(jsonPath("$.currentProjection.aggregateVersion").value(1))
                .andExpect(jsonPath("$.currentProjection.allowedActions[0].actionCode")
                        .value("CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT"))
                .andExpect(jsonPath("$.currentProjection.facts").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = json.readTree(responseBody);
        assertThat(response.path("resourceRef").asText())
                .isEqualTo(response.path("currentProjection").path("orderRef").asText());
        assertThat(response.path("aggregateVersion").asLong())
                .isEqualTo(response.path("currentProjection").path("aggregateVersion").asLong());
    }

    @Test void sameQuoteWithChangedAuthorizedFingerprintConflictsWithoutSecondOrder() throws Exception {
        Quote quote = quoteFor(identity.projectSubjectRef());
        postOrder(request("CMD-M1-FP-A", "IDEM-M1-FP-A", quote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF)).andExpect(status().isOk());
        long before = store.orderCountForTest(identity.projectSubjectRef());
        long businessBefore = store.orderBusinessKeyCountForTest();
        OrderCreationSideEffectSnapshot effectsBefore = effects();

        recovery.installBuyerAuthorizationForTest(identity.projectSubjectRef(), identity.sessionRef(), 8L,
                "SYN-AS-M1-V2", "SYN-AUTH-EVIDENCE-M1-V2");
        postOrder(request("CMD-M1-FP-B", "IDEM-M1-FP-B", quote.quoteRef(), 8L, "SYN-AS-M1-V2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.projectCode").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(store.orderCountForTest(identity.projectSubjectRef())).isEqualTo(before);
        assertThat(store.orderBusinessKeyCountForTest()).isEqualTo(businessBefore);
        assertThat(effects()).isEqualTo(effectsBefore);
    }

    @Test void reusedCommandIdWithNewIdempotencyKeyAndDifferentQuoteConflictsWithoutWrites() throws Exception {
        Quote firstQuote = quoteFor(identity.projectSubjectRef());
        Quote changedQuote = quoteFor(identity.projectSubjectRef());
        postOrder(request("CMD-M1-KEY-CONFLICT", "IDEM-M1-KEY-CONFLICT", firstQuote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF)).andExpect(status().isOk());
        long before = store.orderCountForTest(identity.projectSubjectRef());
        long businessBefore = store.orderBusinessKeyCountForTest();
        OrderCreationSideEffectSnapshot effectsBefore = effects();

        postOrder(request("CMD-M1-KEY-CONFLICT", "IDEM-M1-KEY-NEW", changedQuote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.projectCode").value("IDEMPOTENCY_CONFLICT"));
        assertThat(store.orderCountForTest(identity.projectSubjectRef())).isEqualTo(before);
        assertThat(store.orderBusinessKeyCountForTest()).isEqualTo(businessBefore);
        assertThat(effects()).isEqualTo(effectsBefore);
    }

    @Test void reusedIdempotencyKeyWithNewCommandIdAndDifferentQuoteConflictsWithoutWrites() throws Exception {
        Quote firstQuote = quoteFor(identity.projectSubjectRef());
        Quote changedQuote = quoteFor(identity.projectSubjectRef());
        postOrder(request("CMD-M1-IDEM-ORIGINAL", "IDEM-M1-ONLY-CONFLICT", firstQuote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF)).andExpect(status().isOk());
        long before = store.orderCountForTest(identity.projectSubjectRef());
        long businessBefore = store.orderBusinessKeyCountForTest();
        OrderCreationSideEffectSnapshot effectsBefore = effects();

        postOrder(request("CMD-M1-IDEM-NEW", "IDEM-M1-ONLY-CONFLICT", changedQuote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.projectCode").value("IDEMPOTENCY_CONFLICT"));
        assertThat(store.orderCountForTest(identity.projectSubjectRef())).isEqualTo(before);
        assertThat(store.orderBusinessKeyCountForTest()).isEqualTo(businessBefore);
        assertThat(effects()).isEqualTo(effectsBefore);
    }

    @Test void expiredLegacyCrossSubjectAndCatalogDriftAllFailClosedWithZeroOrderDelta() throws Exception {
        long before = store.orderCountForTest(identity.projectSubjectRef());

        Quote expired = new Quote("Q-EXPIRED-" + UUID.randomUUID(), "880****000", "SYN-OP",
                "SYN-PRODUCT", "SYN-DENOM-1000", 1L, 1L, 1000L, "CNY",
                clock.instant().minusSeconds(1));
        store.installQuoteForTest(identity.projectSubjectRef(), expired);
        assertUnavailable(expired.quoteRef(), "EXPIRED");

        Quote legacy = new Quote("Q-LEGACY-" + UUID.randomUUID(), "880****000", "SYN-OP",
                "SYN-PRODUCT", null, 0L, 0L, 1000L, "CNY", clock.instant().plusSeconds(600));
        store.installQuoteForTest(identity.projectSubjectRef(), legacy);
        assertUnavailable(legacy.quoteRef(), "LEGACY");

        Quote foreign = quoteFor("SYN-SUBJECT-FOREIGN-M1");
        assertUnavailable(foreign.quoteRef(), "FOREIGN");

        Quote stale = quoteFor(identity.projectSubjectRef());
        Instant now = clock.instant();
        catalog.installForTest(new CatalogBatch(2L, 2L, "SYN-APPROVAL-V2", now.minusSeconds(60),
                now.plusSeconds(3600), true, List.of("SYN-OP"), List.of(new CatalogItem("SYN-OP",
                "SYN-PRODUCT", "SYN-DENOM-1000", "PRESET_DENOMINATION", 1000L, "CNY"))));
        assertUnavailable(stale.quoteRef(), "STALE");

        Quote unknown = new Quote("Q-UNKNOWN-" + UUID.randomUUID(), "880****000", "SYN-OP",
                "SYN-PRODUCT", "SYN-DENOM-1000", 3L, 3L, 1000L, "CNY",
                clock.instant().plusSeconds(600));
        store.installQuoteForTest(identity.projectSubjectRef(), unknown);
        catalog.installForTest(new CatalogBatch(3L, 3L, "SYN-APPROVAL-UNKNOWN", now.minusSeconds(60),
                now.plusSeconds(3600), false, List.of(), List.of()));
        assertUnavailable(unknown.quoteRef(), "UNKNOWN");

        assertThat(store.orderCountForTest(identity.projectSubjectRef())).isEqualTo(before);
        assertThat(effects().total()).isZero();
    }

    @Test void replayAfterQuoteExpiryReturnsOriginalOrderWithoutWrites() throws Exception {
        Quote quote = quoteFor(identity.projectSubjectRef());
        JsonNode first = body(postOrder(request("CMD-M1-TIME-EXP-1", "IDEM-M1-TIME-EXP-1", quote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF)));
        String orderRef = first.path("resourceRef").asText();
        store.installQuoteForTest(identity.projectSubjectRef(), new Quote(quote.quoteRef(), quote.maskedPhone(),
                quote.operatorCode(), quote.productCode(), quote.denominationRef(),
                quote.supportedOperatorSetVersion(), quote.catalogVersion(), quote.totalAmountMinor(),
                quote.currency(), clock.instant().minusSeconds(1)));
        long ordersBefore = store.orderCountForTest(identity.projectSubjectRef());
        long businessBefore = store.orderBusinessKeyCountForTest();
        OrderCreationSideEffectSnapshot effectsBefore = effects();

        postOrder(request("CMD-M1-TIME-EXP-2", "IDEM-M1-TIME-EXP-2", quote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectCode").value("ORDER_REPLAYED"))
                .andExpect(jsonPath("$.resourceRef").value(orderRef));
        assertThat(store.orderCountForTest(identity.projectSubjectRef())).isEqualTo(ordersBefore);
        assertThat(store.orderBusinessKeyCountForTest()).isEqualTo(businessBefore);
        assertThat(effects()).isEqualTo(effectsBefore);
    }

    @Test void replayAfterCatalogDriftReturnsOriginalOrderWithoutWrites() throws Exception {
        Quote quote = quoteFor(identity.projectSubjectRef());
        JsonNode first = body(postOrder(request("CMD-M1-TIME-CAT-1", "IDEM-M1-TIME-CAT-1", quote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF)));
        String orderRef = first.path("resourceRef").asText();
        Instant now = clock.instant();
        catalog.installForTest(new CatalogBatch(2L, 2L, "SYN-APPROVAL-TIME-V2", now.minusSeconds(60),
                now.plusSeconds(3600), true, List.of("SYN-OP"), List.of(new CatalogItem("SYN-OP",
                "SYN-PRODUCT", "SYN-DENOM-1000", "PRESET_DENOMINATION", 1000L, "CNY"))));
        long ordersBefore = store.orderCountForTest(identity.projectSubjectRef());
        long businessBefore = store.orderBusinessKeyCountForTest();
        OrderCreationSideEffectSnapshot effectsBefore = effects();

        postOrder(request("CMD-M1-TIME-CAT-2", "IDEM-M1-TIME-CAT-2", quote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectCode").value("ORDER_REPLAYED"))
                .andExpect(jsonPath("$.resourceRef").value(orderRef));
        assertThat(store.orderCountForTest(identity.projectSubjectRef())).isEqualTo(ordersBefore);
        assertThat(store.orderBusinessKeyCountForTest()).isEqualTo(businessBefore);
        assertThat(effects()).isEqualTo(effectsBefore);
    }

    @Test void staleSessionWrongSetAndInvalidPreconditionFailBeforeOrderMutation() throws Exception {
        Quote quote = quoteFor(identity.projectSubjectRef());
        long before = store.orderCountForTest(identity.projectSubjectRef());
        long businessBefore = store.orderBusinessKeyCountForTest();
        OrderCreationSideEffectSnapshot effectsBefore = effects();
        postOrder(request("CMD-M1-STALE", "IDEM-M1-STALE", quote.quoteRef(),
                SESSION_VERSION - 1, AUTHORIZATION_SET_REF)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.projectCode").value("ORDER_CREATION_NOT_AVAILABLE"));
        postOrder(request("CMD-M1-SET", "IDEM-M1-SET", quote.quoteRef(),
                SESSION_VERSION, "SYN-AS-WRONG")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.projectCode").value("ORDER_CREATION_NOT_AVAILABLE"));
        postOrder(request("CMD-M1-PRE", "IDEM-M1-PRE", "WRONG_PRECONDITION", quote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.projectCode").value("ORDER_CREATION_PRECONDITION_INVALID"));
        assertThat(store.orderCountForTest(identity.projectSubjectRef())).isEqualTo(before);
        assertThat(store.orderBusinessKeyCountForTest()).isEqualTo(businessBefore);
        assertThat(effects()).isEqualTo(effectsBefore);
    }

    @Test void clientCannotSelfReportSubjectOrAuthorizationEvidence() throws Exception {
        Quote quote = quoteFor(identity.projectSubjectRef());
        long before = store.orderCountForTest(identity.projectSubjectRef());
        long businessBefore = store.orderBusinessKeyCountForTest();
        OrderCreationSideEffectSnapshot effectsBefore = effects();
        String body = request("CMD-M1-FORGED", "IDEM-M1-FORGED", quote.quoteRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF).replace("}",
                ",\"projectSubjectRef\":\"SYN-SUBJECT-FORGED\",\"authorizationEvidenceVersion\":\"FORGED\","
                        + "\"totalAmountMinor\":1,\"currency\":\"USD\",\"operatorCode\":\"FORGED\"}");
        postOrder(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.projectCode").value("INVALID_REQUEST"));
        assertThat(store.orderCountForTest(identity.projectSubjectRef())).isEqualTo(before);
        assertThat(store.orderBusinessKeyCountForTest()).isEqualTo(businessBefore);
        assertThat(effects()).isEqualTo(effectsBefore);
    }

    @Test void missingRequiredFieldAndAbsentTrustedTokenFailClosed() throws Exception {
        Quote quote = quoteFor(identity.projectSubjectRef());
        long before = store.orderCountForTest(identity.projectSubjectRef());
        long businessBefore = store.orderBusinessKeyCountForTest();
        OrderCreationSideEffectSnapshot effectsBefore = effects();
        postOrder("{\"commandId\":\"CMD-M1-MISSING\",\"idempotencyKey\":\"IDEM-M1-MISSING\"}")
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(request("CMD-M1-NO-TOKEN", "IDEM-M1-NO-TOKEN", quote.quoteRef(),
                                SESSION_VERSION, AUTHORIZATION_SET_REF)))
                .andExpect(status().isUnauthorized());
        assertThat(store.orderCountForTest(identity.projectSubjectRef())).isEqualTo(before);
        assertThat(store.orderBusinessKeyCountForTest()).isEqualTo(businessBefore);
        assertThat(effects()).isEqualTo(effectsBefore);
    }

    private Quote quoteFor(String subject) {
        String suffix = UUID.randomUUID().toString();
        return service.createQuote(subject, "8801700000000", "SYN-OP", "SYN-PRODUCT", "SYN-DENOM-1000",
                1L, 1L, "CMD-M1-QUOTE-" + suffix, "IDEM-M1-QUOTE-" + suffix, MnpState.CONFIRMED);
    }

    private void assertUnavailable(String quoteRef, String suffix) throws Exception {
        long ordersBefore = store.orderCountForTest(identity.projectSubjectRef());
        long businessBefore = store.orderBusinessKeyCountForTest();
        OrderCreationSideEffectSnapshot effectsBefore = effects();
        postOrder(request("CMD-M1-" + suffix, "IDEM-M1-" + suffix, quoteRef,
                SESSION_VERSION, AUTHORIZATION_SET_REF))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.projectCode").value("ORDER_CREATION_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(store.orderCountForTest(identity.projectSubjectRef())).isEqualTo(ordersBefore);
        assertThat(store.orderBusinessKeyCountForTest()).isEqualTo(businessBefore);
        assertThat(effects()).isEqualTo(effectsBefore);
    }

    private OrderCreationSideEffectSnapshot effects() {
        return store.sideEffectSnapshot(identity.projectSubjectRef());
    }

    private org.springframework.test.web.servlet.ResultActions postOrder(String body) throws Exception {
        return mvc.perform(post("/api/v1/orders").header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String request(String commandId, String idempotencyKey, String quoteRef,
                                  long sessionVersion, String authorizationSetRef) {
        return request(commandId, idempotencyKey, MockFlowService.ORDER_CREATION_PRECONDITION, quoteRef,
                sessionVersion, authorizationSetRef);
    }

    private static String request(String commandId, String idempotencyKey, String precondition, String quoteRef,
                                  long sessionVersion, String authorizationSetRef) {
        return "{\"commandId\":\"" + commandId + "\",\"idempotencyKey\":\"" + idempotencyKey
                + "\",\"orderCreationPrecondition\":\"" + precondition + "\",\"quoteRef\":\""
                + quoteRef + "\",\"sessionVersion\":" + sessionVersion
                + ",\"authorizationSetRef\":\"" + authorizationSetRef + "\"}";
    }
}
