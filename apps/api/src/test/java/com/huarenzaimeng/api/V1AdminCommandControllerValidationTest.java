package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class V1AdminCommandControllerValidationTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final V1AdminCommandController controller=new V1AdminCommandController(jdbc);
    private final ObjectMapper json=new ObjectMapper();
    private final MockMvc mvc=MockMvcBuilders.standaloneSetup(controller).build();

    @Test void oversizedProductFieldIsStableBadRequestBeforeIdempotencyClaim() throws Exception {
        var body=json.readTree("""
                {"ref":"PRODUCT-VALID-01","reason":"development input","countryCode":"BD","operatorCode":"GP",
                 "productType":"BALANCE","displayName":"%s","benefitText":"balance","providerCode":"WINLA",
                 "providerSku":"SKU-1","channelPriority":10}
                """.formatted("X".repeat(161)));
        mvc.perform(post("/admin-command/v1/products").header("Idempotency-Key","IDEMPOTENCY-VALID-0001")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"SUPER_ADMIN").requestAttr(AdminSessionFilter.TRUSTED_USER,"ADMIN-TEST")
                .contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }

    @Test void invalidProductEnumIsStableBadRequestBeforeIdempotencyClaim() throws Exception {
        var body=json.readTree("""
                {"ref":"PRODUCT-VALID-02","reason":"development input","countryCode":"BD","operatorCode":"GP",
                 "productType":"MYSTERY","displayName":"name","benefitText":"balance","providerCode":"WINLA",
                 "providerSku":"SKU-2","channelPriority":10}
                """);
        mvc.perform(post("/admin-command/v1/products").header("Idempotency-Key","IDEMPOTENCY-VALID-0002")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"SUPER_ADMIN").requestAttr(AdminSessionFilter.TRUSTED_USER,"ADMIN-TEST")
                .contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }

    @Test void invalidHolidayRuleTypeIsRejectedBeforeClaim() throws Exception {
        String body="""
                {"ref":"HOLIDAY-VALID-01","reason":"development input","countryCode":"BD","ruleType":"VACATION",
                 "displayName":"Invalid","sourceLabel":"manual","startDate":"2026-01-01","endDate":"2026-01-01",
                 "effectiveUntil":"2027-01-01T00:00:00Z"}
                """;
        mvc.perform(post("/admin-command/v1/holidays").header("Idempotency-Key","IDEMPOTENCY-VALID-0003")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"SUPER_ADMIN").requestAttr(AdminSessionFilter.TRUSTED_USER,"ADMIN-TEST")
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }

    @Test void invalidNormalizedTypeIsRejectedBeforeClaim() throws Exception {
        String body="""
                {"expectedVersion":1,"reason":"mapping correction","catalogBatchRef":"BATCH-1","rawSkuName":"sku",
                 "rawBenefitText":"benefit","supplierCost":1.00,"settlementCurrency":"CNY","supplierAvailability":"AVAILABLE",
                 "catalogSyncedAt":"2026-08-16T00:00:00Z","normalizedType":"VOICE_ONLY","normalizedOperator":"GP","mappingState":"MAPPED"}
                """;
        mvc.perform(put("/admin-command/v1/product-mappings/PRODUCT-VALID-01").header("Idempotency-Key","IDEMPOTENCY-VALID-0004")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"SUPER_ADMIN").requestAttr(AdminSessionFilter.TRUSTED_USER,"ADMIN-TEST")
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }

}
