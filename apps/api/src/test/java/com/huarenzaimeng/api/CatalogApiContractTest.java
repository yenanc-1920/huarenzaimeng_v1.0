package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory"
})
@AutoConfigureMockMvc
class CatalogApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired InMemoryCatalogStore catalog;
    @Autowired MockFlowService flow;

    @AfterEach void reset() { catalog.resetForTest(); }

    @Test void publicCatalogSeparatesSupportedUnsupportedUnknownAndEmptySet() throws Exception {
        mvc.perform(get("/api/v1/catalog").param("operatorCode", "SYN-OP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operatorQualification").value("SUPPORTED"))
                .andExpect(jsonPath("$.data.supportedOperatorSetVersion").value(1))
                .andExpect(jsonPath("$.data.catalogVersion").value(1))
                .andExpect(jsonPath("$.data.items[0].denominationRef").value("SYN-DENOM-1000"))
                .andExpect(jsonPath("$.data.items[0].amountMinor").value(1000));

        mvc.perform(get("/api/v1/catalog").param("operatorCode", "SYN-NOT-IN-BATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operatorQualification").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        catalog.installForTest(batch(false, List.of("SYN-OP"), syntheticItems()));
        mvc.perform(get("/api/v1/catalog").param("operatorCode", "SYN-OP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operatorQualification").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.supportedOperatorSetVersion").doesNotExist())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        catalog.installForTest(batch(true, List.of(), List.of()));
        mvc.perform(get("/api/v1/catalog").param("operatorCode", "SYN-OP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operatorQualification").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        assertThat(catalog.mutationCount()).isZero();
    }

    @Test void missingOperatorCodeFailsClosedEvenWhenApprovedCatalogIsEmpty() throws Exception {
        catalog.installForTest(batch(true, List.of(), List.of()));

        mvc.perform(get("/api/v1/catalog"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.projectCode").value("OPERATOR_CODE_REQUIRED"))
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/v1/catalog").param("operatorCode", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.projectCode").value("OPERATOR_CODE_REQUIRED"));
        assertThat(catalog.read(null).operatorQualification()).isEqualTo(OperatorQualification.UNKNOWN);
        assertThat(catalog.mutationCount()).isZero();
    }

    @Test void quoteUsesServerPresetAndSameCommandReplayCreatesExactlyOneQuote() throws Exception {
        String subject = "SUBJECT-CATALOG-" + UUID.randomUUID();
        String request = quote("CMD-CAT-1", "IDEM-CAT-1", 1, 1, "SYN-OP", "SYN-DENOM-1000");
        JsonNode first = json.readTree(mvc.perform(post("/api/v1/quotes").header("X-Project-Subject-Ref", subject)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmountMinor").value(1000))
                .andExpect(jsonPath("$.data.currency").value("CNY"))
                .andExpect(jsonPath("$.data.supportedOperatorSetVersion").value(1))
                .andExpect(jsonPath("$.data.catalogVersion").value(1))
                .andReturn().getResponse().getContentAsString());
        String firstRef = first.path("data").path("quoteRef").asText();

        mvc.perform(post("/api/v1/quotes").header("X-Project-Subject-Ref", subject)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.quoteRef").value(firstRef));
        mvc.perform(post("/api/v1/quotes").header("X-Project-Subject-Ref", subject)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("8801700000000", "8801800000000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.projectCode").value("IDEMPOTENCY_CONFLICT"));
        assertThat(flow.quoteCount(subject)).isEqualTo(1);
    }

    @Test void staleUnsupportedUnknownAndNonPresetBranchesCreateZeroQuotes() throws Exception {
        rejectWithoutQuote(quote("CMD-STALE", "IDEM-STALE", 2, 1, "SYN-OP", "SYN-DENOM-1000"),
                "SUPPORTED_OPERATOR_SET_VERSION_STALE");
        rejectWithoutQuote(quote("CMD-CATALOG-STALE", "IDEM-CATALOG-STALE", 1, 2, "SYN-OP",
                "SYN-DENOM-1000"), "CATALOG_VERSION_STALE");
        rejectWithoutQuote(quote("CMD-UNSUPPORTED", "IDEM-UNSUPPORTED", 1, 1, "SYN-OTHER",
                "SYN-DENOM-1000"), "OPERATOR_UNSUPPORTED");
        rejectWithoutQuote(quote("CMD-CUSTOM", "IDEM-CUSTOM", 1, 1, "SYN-OP", "CUSTOM-AMOUNT"),
                "PRESET_CATALOG_ITEM_NOT_FOUND");

        catalog.installForTest(batch(false, List.of("SYN-OP"), syntheticItems()));
        rejectWithoutQuote(quote("CMD-UNKNOWN", "IDEM-UNKNOWN", 1, 1, "SYN-OP", "SYN-DENOM-1000"),
                "OPERATOR_QUALIFICATION_UNKNOWN");
    }

    @Test void adminWriteIsAlwaysIneligibleWithoutRealAuthorizationBinding() throws Exception {
        mvc.perform(post("/api/v1/admin/catalog-operations")
                        .header("AuthorizationRef", "SELF-REPORTED-NOT-TRUSTED")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.projectCode").value("CATALOG_WRITE_AUTHORIZATION_REQUIRED"));
        assertThat(catalog.mutationCount()).isZero();
    }

    private void rejectWithoutQuote(String request, String code) throws Exception {
        String subject = "SUBJECT-REJECT-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/quotes").header("X-Project-Subject-Ref", subject)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.projectCode").value(code));
        assertThat(flow.quoteCount(subject)).isZero();
    }

    private static String quote(String command, String idem, long setVersion, long catalogVersion,
                                String operator, String denomination) {
        return "{\"phone\":\"8801700000000\",\"operatorCode\":\"" + operator
                + "\",\"productRef\":\"SYN-PRODUCT\",\"denominationRef\":\"" + denomination
                + "\",\"supportedOperatorSetVersion\":" + setVersion + ",\"catalogVersion\":" + catalogVersion
                + ",\"commandId\":\"" + command + "\",\"idempotencyKey\":\"" + idem
                + "\",\"mnpState\":\"CONFIRMED\"}";
    }

    private static CatalogBatch batch(boolean known, List<String> operators, List<CatalogItem> items) {
        return new CatalogBatch(1, 1, "APPROVAL-E3-SYNTHETIC-ONLY", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600), known, operators, items);
    }
    private static List<CatalogItem> syntheticItems() {
        return List.of(new CatalogItem("SYN-OP", "SYN-PRODUCT", "SYN-DENOM-1000",
                "PRESET_DENOMINATION", 1000, "CNY"));
    }
}
