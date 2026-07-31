package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory"
})
@AutoConfigureMockMvc
class MockFlowControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void prepaymentUnknownIsRejectedWithoutCreatingPaymentEligibility() throws Exception {
        mvc.perform(post("/api/v1/quotes")
                        .header("X-Project-Subject-Ref", "SUBJECT-CONTROLLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"8801700000000","operatorCode":"SYN-OP","productCode":"SYN-PRODUCT","mnpState":"UNKNOWN"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-HZM-Mock-Only", "true"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.projectCode").value("PREPAY_MNP_NOT_CONFIRMED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
