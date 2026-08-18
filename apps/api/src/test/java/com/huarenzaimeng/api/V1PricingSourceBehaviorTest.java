package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class V1PricingSourceBehaviorTest {
    @Test void trialUsesBoundCatalogSnapshotAndRejectsClientCostMismatch() throws Exception {
        MockMvc mvc=mvc();
        mvc.perform(post("/admin-command/v1/price-versions/trial")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"SUPER_ADMIN").requestAttr(AdminSessionFilter.TRUSTED_USER,"ADMIN-1")
                .contentType("application/json").content(priceBody("99.00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.projectCode").value("SUPPLIER_COST_MISMATCH"));
    }

    @Test void trialSucceedsWithoutClientCostAndReportsPersistedCatalogCost() throws Exception {
        MockMvc mvc=mvc();
        mvc.perform(post("/admin-command/v1/price-versions/trial")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"SUPER_ADMIN").requestAttr(AdminSessionFilter.TRUSTED_USER,"ADMIN-1")
                .contentType("application/json").content(priceBody(null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supplierCost").value(10.0));
    }

    private static MockMvc mvc(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(invocation->{
            String sql=invocation.getArgument(0,String.class);
            if(sql.contains("hz_supplier_catalog_item_snapshot"))return List.of(Map.of(
                    "supplier_cost",new BigDecimal("10.0000"),"settlement_currency","BDT","batch_ref","BATCH-1","provider_sku","SKU-1"));
            if(sql.contains("hz_fx_rate_snapshot"))return List.of(Map.of(
                    "source_code","FIXTURE","base_currency","BDT","quote_currency","CNY","rate_value",new BigDecimal("0.06000000"),
                    "observed_at",Timestamp.from(Instant.parse("2026-08-18T00:00:00Z")),"valid_until",Timestamp.from(Instant.parse("2027-08-18T00:00:00Z"))));
            return List.of();
        });
        return MockMvcBuilders.standaloneSetup(new V1AdminCommandController(jdbc)).build();
    }

    private static String priceBody(String supplierCost){
        String optional=supplierCost==null?"":"\"supplierCost\":"+supplierCost+",";
        return "{"+optional+"\"productRef\":\"PRODUCT-1\",\"settlementCurrency\":\"BDT\",\"supplierSourceRef\":\"BATCH-1/SKU-1\","+
                "\"fxSource\":\"FIXTURE\",\"fxSnapshotRef\":\"FX-1\",\"fxDirection\":\"BDT_TO_CNY\","+
                "\"bufferRate\":0.02,\"markupRate\":0.20,\"wechatFeeRate\":0.006,\"taxRate\":0.00,"+
                "\"minimumMarginRate\":0.10,\"roundingRule\":\"CEILING_0_01\",\"promotionBearer\":\"NONE\"}";
    }
}
