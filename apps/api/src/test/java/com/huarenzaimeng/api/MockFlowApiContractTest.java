package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.huarenzaimeng.api.config.TestAccessTokenFilter;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory",
        "hz.test-access-token=mock-flow-contract-token"
})
@AutoConfigureMockMvc
class MockFlowApiContractTest {
    private static final String SUBJECT_HEADER = "X-Project-Subject-Ref";
    private static final String TOKEN = "mock-flow-contract-token";
    private static final LocalSyntheticIdentity IDENTITY = LocalSyntheticIdentity.fromToken(TOKEN);
    private static final String SUBJECT = IDENTITY.projectSubjectRef();
    private static final long SESSION_VERSION = 1L;
    private static final String AUTHORIZATION_SET_REF = "SYN-AS-MOCK-FLOW";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LocalSyntheticOrderRecoveryService recovery;

    @BeforeEach void prepareBuyer() {
        recovery.resetForTest();
        recovery.installBuyerAuthorizationForTest(SUBJECT, IDENTITY.sessionRef(), SESSION_VERSION,
                AUTHORIZATION_SET_REF, "SYN-AUTH-MOCK-FLOW");
    }

    @Test
    void coreMockFlowUsesStableEnvelopePriceFactsActionsAndDualVersions() throws Exception {
        JsonNode quoteEnvelope = json.readTree(mvc.perform(post("/api/v1/quotes")
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"8801700000000","operatorCode":"SYN-OP","productRef":"SYN-PRODUCT","denominationRef":"SYN-DENOM-1000","supportedOperatorSetVersion":1,"catalogVersion":1,"commandId":"CMD-QUOTE-FLOW","idempotencyKey":"IDEM-QUOTE-FLOW","mnpState":"CONFIRMED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.projectCode").value("OK"))
                .andExpect(jsonPath("$.data.maskedPhone").value("880****000"))
                .andReturn().getResponse().getContentAsString());
        String quoteRef = quoteEnvelope.path("data").path("quoteRef").asText();

        JsonNode orderEnvelope = json.readTree(mvc.perform(post("/api/v1/orders")
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(quoteRef, "CMD-FLOW-ORDER", "IDEM-FLOW-ORDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCode").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.currentProjection.priceSnapshot.quoteRef").value(quoteRef))
                .andExpect(jsonPath("$.currentProjection.priceSnapshot.operatorCode").value("SYN-OP"))
                .andExpect(jsonPath("$.currentProjection.facts").doesNotExist())
                .andExpect(jsonPath("$.currentProjection.projectionVersion").value(1))
                .andExpect(jsonPath("$.currentProjection.aggregateVersion").value(1))
                .andExpect(jsonPath("$.currentProjection.allowedActions[0].actionCode")
                        .value("CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT"))
                .andExpect(jsonPath("$.currentProjection.allowedActions[0].expectedProjectionVersion").value(1))
                .andExpect(jsonPath("$.currentProjection.allowedActions[0].expectedAggregateVersion").value(1))
                .andReturn().getResponse().getContentAsString());
        String orderRef = orderEnvelope.path("currentProjection").path("orderRef").asText();

        mvc.perform(post("/api/v1/orders/{orderRef}/mock-payment", orderRef)
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commandId":"CMD-FLOW-PAY","idempotencyKey":"IDEM-FLOW-PAY","expectedProjectionVersion":1,"expectedAggregateVersion":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.facts.payment").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.projectionVersion").value(2))
                .andExpect(jsonPath("$.data.aggregateVersion").value(2))
                .andExpect(jsonPath("$.data.allowedActions[0].actionCode").value("REQUEST_MOCK_TOPUP"));

        mvc.perform(post("/api/v1/orders/{orderRef}/mock-topup", orderRef)
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commandId":"CMD-FLOW-TOP","idempotencyKey":"IDEM-FLOW-TOP","expectedProjectionVersion":2,"expectedAggregateVersion":2,"mnpState":"UNKNOWN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.facts.upstreamDebit").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.facts.delivery").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.allowedActions[0].actionCode").value("WAIT_OR_CONTACT_SUPPORT"))
                .andExpect(jsonPath("$.data.allowedActions[0].expectedAggregateVersion").doesNotExist());

        mvc.perform(post("/api/v1/orders/{orderRef}/mock-topup", orderRef)
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commandId":"CMD-FLOW-TOP-FRESH","idempotencyKey":"IDEM-FLOW-TOP-FRESH","expectedProjectionVersion":3,"expectedAggregateVersion":3,"mnpState":"UNKNOWN"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.projectCode").value("IDEMPOTENCY_CONFLICT"));

        mvc.perform(get("/api/v1/orders/{orderRef}", orderRef)
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN).header(SUBJECT_HEADER, SUBJECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.projectCode").value("ORDER_DETAIL_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.resourceRef").doesNotExist())
                .andExpect(jsonPath("$.currentProjection").doesNotExist());
    }

    @Test
    void missingHeaderAndMalformedBodyUseUniformProjectCodeEnvelope() throws Exception {
        mvc.perform(post("/api/v1/quotes").header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-HZM-Mock-Only", "true"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.projectCode").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/quotes").header(SUBJECT_HEADER, SUBJECT)
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.projectCode").value("INVALID_REQUEST"));
    }

    @Test
    void sameFingerprintRekeyReplaysAndDoesNotReplaceOriginalOrder() throws Exception {
        String quoteRef = createQuote();
        String firstOrder = mvc.perform(post("/api/v1/orders")
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(quoteRef, "CMD-API-A", "IDEM-API-SAME")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/v1/orders")
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(quoteRef, "CMD-API-B", "IDEM-API-SAME")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-HZM-Mock-Only", "true"))
                .andExpect(header().string("X-HZM-Mock-Semantics", "LOCAL_SYNTHETIC_NO_EXTERNAL_FACTS"))
                .andExpect(jsonPath("$.projectCode").value("ORDER_REPLAYED"));

        String orderRef = json.readTree(firstOrder).path("resourceRef").asText();
        mvc.perform(get("/api/v1/orders/{orderRef}", orderRef)
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.projectCode").value("ORDER_DETAIL_NOT_AVAILABLE"));

        mvc.perform(get("/api/v1/orders/{orderRef}", orderRef)
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .header(SUBJECT_HEADER, SUBJECT))
                .andExpect(status().isOk())
                .andExpect(header().string("X-HZM-Mock-Only", "true"))
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.projectCode").value("ORDER_DETAIL_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.resourceRef").doesNotExist())
                .andExpect(jsonPath("$.currentProjection").doesNotExist());
    }

    @Test
    void missingIdempotencyKeyIsRejectedByApiValidation() throws Exception {
        String quoteRef = createQuote();
        mvc.perform(post("/api/v1/orders")
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteRef\":\"" + quoteRef + "\",\"commandId\":\"CMD-MISSING\","
                                + "\"orderCreationPrecondition\":\"ORDER_MUST_NOT_EXIST\",\"sessionVersion\":1,"
                                + "\"authorizationSetRef\":\"" + AUTHORIZATION_SET_REF + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private String createQuote() throws Exception {
        String response = mvc.perform(post("/api/v1/quotes")
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN)
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"8801700000000","operatorCode":"SYN-OP","productRef":"SYN-PRODUCT","denominationRef":"SYN-DENOM-1000","supportedOperatorSetVersion":1,"catalogVersion":1,"commandId":"CMD-QUOTE-HELPER-%s","idempotencyKey":"IDEM-QUOTE-HELPER-%s","mnpState":"CONFIRMED"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-HZM-Mock-Only", "true"))
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\\\"quoteRef\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private static String orderJson(String quoteRef, String commandId, String idempotencyKey) {
        return "{\"quoteRef\":\"" + quoteRef + "\",\"commandId\":\"" + commandId
                + "\",\"idempotencyKey\":\"" + idempotencyKey
                + "\",\"orderCreationPrecondition\":\"ORDER_MUST_NOT_EXIST\",\"sessionVersion\":"
                + SESSION_VERSION + ",\"authorizationSetRef\":\"" + AUTHORIZATION_SET_REF + "\"}";
    }
}
