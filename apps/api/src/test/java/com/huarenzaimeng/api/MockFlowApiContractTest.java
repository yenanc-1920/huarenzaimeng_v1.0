package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory"
})
@AutoConfigureMockMvc
class MockFlowApiContractTest {
    private static final String SUBJECT_HEADER = "X-Project-Subject-Ref";
    private static final String SUBJECT = "SUBJECT-API";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void coreMockFlowUsesStableEnvelopePriceFactsActionsAndDualVersions() throws Exception {
        JsonNode quoteEnvelope = json.readTree(mvc.perform(post("/api/v1/quotes")
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"8801700000000","operatorCode":"SYN-OP","productCode":"SYN-PRODUCT","mnpState":"CONFIRMED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.projectCode").value("OK"))
                .andExpect(jsonPath("$.data.maskedPhone").value("880****000"))
                .andReturn().getResponse().getContentAsString());
        String quoteRef = quoteEnvelope.path("data").path("quoteRef").asText();

        JsonNode orderEnvelope = json.readTree(mvc.perform(post("/api/v1/orders")
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(quoteRef, "CMD-FLOW-ORDER", "IDEM-FLOW-ORDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceSnapshot.quoteRef").value(quoteRef))
                .andExpect(jsonPath("$.data.priceSnapshot.operatorCode").value("SYN-OP"))
                .andExpect(jsonPath("$.data.facts.semantics")
                        .value("MOCK_PROJECTION_ONLY_NO_EXTERNAL_FACTS"))
                .andExpect(jsonPath("$.data.projectionVersion").value(1))
                .andExpect(jsonPath("$.data.aggregateVersion").value(1))
                .andExpect(jsonPath("$.data.allowedActions[0].actionCode").value("REQUEST_MOCK_PAYMENT"))
                .andExpect(jsonPath("$.data.allowedActions[0].expectedProjectionVersion").value(1))
                .andExpect(jsonPath("$.data.allowedActions[0].expectedAggregateVersion").value(1))
                .andReturn().getResponse().getContentAsString());
        String orderRef = orderEnvelope.path("data").path("orderRef").asText();

        mvc.perform(post("/api/v1/orders/{orderRef}/mock-payment", orderRef)
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
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commandId":"CMD-FLOW-TOP-FRESH","idempotencyKey":"IDEM-FLOW-TOP-FRESH","expectedProjectionVersion":3,"expectedAggregateVersion":3,"mnpState":"UNKNOWN"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.projectCode").value("IDEMPOTENCY_CONFLICT"));

        mvc.perform(get("/api/v1/orders/{orderRef}/projection", orderRef).header(SUBJECT_HEADER, SUBJECT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectionVersion").value(3))
                .andExpect(jsonPath("$.data.aggregateVersion").value(3));
    }

    @Test
    void missingHeaderAndMalformedBodyUseUniformProjectCodeEnvelope() throws Exception {
        mvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-HZM-Mock-Only", "true"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.projectCode").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/quotes").header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON).content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.projectCode").value("INVALID_REQUEST"));
    }

    @Test
    void idempotencyConflictIs409AndDoesNotReplaceOriginalOrder() throws Exception {
        String quoteRef = createQuote();
        String firstOrder = mvc.perform(post("/api/v1/orders")
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(quoteRef, "CMD-API-A", "IDEM-API-SAME")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/v1/orders")
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(quoteRef, "CMD-API-B", "IDEM-API-SAME")))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-HZM-Mock-Only", "true"))
                .andExpect(header().string("X-HZM-Mock-Semantics", "PROJECTION_ONLY_NO_EXTERNAL_FACTS"))
                .andExpect(jsonPath("$.projectCode").value("IDEMPOTENCY_CONFLICT"));

        String orderRef = firstOrder.replaceAll(".*\\\"orderRef\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mvc.perform(get("/api/v1/orders/{orderRef}/projection", orderRef))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/orders/{orderRef}/projection", orderRef)
                        .header(SUBJECT_HEADER, SUBJECT))
                .andExpect(status().isOk())
                .andExpect(header().string("X-HZM-Mock-Only", "true"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.projectCode").value("OK"))
                .andExpect(jsonPath("$.data.projectionVersion").value(1));
    }

    @Test
    void missingIdempotencyKeyIsRejectedByApiValidation() throws Exception {
        String quoteRef = createQuote();
        mvc.perform(post("/api/v1/orders")
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteRef\":\"" + quoteRef + "\",\"commandId\":\"CMD-MISSING\"}"))
                .andExpect(status().isBadRequest());
    }

    private String createQuote() throws Exception {
        String response = mvc.perform(post("/api/v1/quotes")
                        .header(SUBJECT_HEADER, SUBJECT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"8801700000000","operatorCode":"SYN-OP","productCode":"SYN-PRODUCT","mnpState":"CONFIRMED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-HZM-Mock-Only", "true"))
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\\\"quoteRef\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private static String orderJson(String quoteRef, String commandId, String idempotencyKey) {
        return "{\"quoteRef\":\"" + quoteRef + "\",\"commandId\":\"" + commandId
                + "\",\"idempotencyKey\":\"" + idempotencyKey + "\"}";
    }
}
