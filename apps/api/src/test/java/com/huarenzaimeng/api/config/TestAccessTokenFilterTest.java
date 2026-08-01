package com.huarenzaimeng.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory",
        "hz.test-access-token=test-only-secret"
})
@AutoConfigureMockMvc
class TestAccessTokenFilterTest {
    private static final String UNAUTHORIZED = "{\"status\":\"UNAUTHORIZED\"}";

    @Autowired MockMvc mvc;

    @Test
    void missingAndIncorrectTokensReturnTheSameUnauthorizedResponseForApiReads() throws Exception {
        String missing = mvc.perform(get("/api/v1/orders/ORDER/projection"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        String incorrect = mvc.perform(get("/api/v1/orders/ORDER/projection")
                        .header(TestAccessTokenFilter.HEADER_NAME, "incorrect"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(missing).isEqualTo(UNAUTHORIZED).isEqualTo(incorrect);
    }

    @Test
    void apiWritesRequireTokenAndCorrectTokenContinuesToController() throws Exception {
        mvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(UNAUTHORIZED));

        mvc.perform(post("/api/v1/quotes")
                        .header(TestAccessTokenFilter.HEADER_NAME, "test-only-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actuatorHealthRemainsPublicWhenTokenProtectionIsEnabled() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
