package com.huarenzaimeng.api;

import com.huarenzaimeng.api.config.TestAccessTokenFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
        "hz.persistence.mode=in-memory", "hz.test-access-token=p014-runtime-token", "hz.p014.mode=local-synthetic",
        "hz.p014.fixture-mode=explicit-local-synthetic-sample"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class P014TopupRuntimeApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired P014TopupService service;
    @Autowired P014TopupFixtureLoader fixtureLoader;

    @BeforeEach void resetFixture() {
        service.resetForTest();
        fixtureLoader.load();
    }

    @Test void explicit_no_default_fixture_reaches_all_three_controller_routes() throws Exception {
        String body="""
                {"commandId":"CMD-P014-RUNTIME-1","idempotencyKey":"IDEM-P014-RUNTIME-1","topupCreationPrecondition":"TOPUP_INTENT_MUST_NOT_EXIST","sessionVersion":1,"authorizationSetRef":"AUTHSET-P014-SYN-001","expectedProjectionVersion":1,"expectedAggregateVersion":1}
                """;
        mvc.perform(post("/api/v1/orders/{orderRef}/topup-intents",P014TopupFixtureLoader.ORDER_REF)
                        .header(TestAccessTokenFilter.HEADER_NAME,"p014-runtime-token").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectCode").value("TOPUP_INTENT_CREATED"))
                .andExpect(jsonPath("$.currentProjection.schemaVersion").value("P014_TOPUP_PROGRESS_V1"))
                .andExpect(jsonPath("$.currentProjection.priceSnapshotSummary.*").isArray())
                .andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/v1/orders/{orderRef}/projection",P014TopupFixtureLoader.ORDER_REF)
                        .header(TestAccessTokenFilter.HEADER_NAME,"p014-runtime-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectCode").value("TOPUP_PROGRESS_READ"));
        mvc.perform(get("/api/v1/orders/{orderRef}/topup-intents/result",P014TopupFixtureLoader.ORDER_REF)
                        .header(TestAccessTokenFilter.HEADER_NAME,"p014-runtime-token")
                        .param("commandId","CMD-P014-RUNTIME-1").param("idempotencyKey","IDEM-P014-RUNTIME-1")
                        .param("sessionVersion","1").param("authorizationSetRef","AUTHSET-P014-SYN-001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectCode").value("TOPUP_INTENT_RESULT_FOUND"));
    }

    @Test void release_and_no_default_modes_have_zero_fixture_qualification() throws Exception {
        Profile controllerProfile=P014TopupController.class.getAnnotation(Profile.class);
        ConditionalOnProperty loaderGate=P014TopupFixtureLoader.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(controllerProfile.value()).containsExactlyInAnyOrder("mock","test");
        assertThat(loaderGate.name()).containsExactly("hz.p014.fixture-mode");
        assertThat(loaderGate.havingValue()).isEqualTo("explicit-local-synthetic-sample");
        assertThat(loaderGate.matchIfMissing()).isFalse();
        assertThat(Files.readString(Path.of("src/main/resources/application-mock.yml"))).contains("fixture-mode: no-default");
        assertThat(Files.readString(Path.of("src/main/resources/application-release-mysql.yml"))).doesNotContain("explicit-local-synthetic-sample");
    }

    @Test void missing_and_wrong_test_tokens_are_same_shape_without_existence_leak() throws Exception {
        String body="""
                {"commandId":"CMD-P014-DENIED","idempotencyKey":"IDEM-P014-DENIED","topupCreationPrecondition":"TOPUP_INTENT_MUST_NOT_EXIST","sessionVersion":1,"authorizationSetRef":"AUTHSET-P014-SYN-001","expectedProjectionVersion":1,"expectedAggregateVersion":1}
                """;
        String missing=mvc.perform(post("/api/v1/orders/{orderRef}/topup-intents",P014TopupFixtureLoader.ORDER_REF)
                        .contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCode").value("TOPUP_NOT_AVAILABLE")).andReturn().getResponse().getContentAsString();
        String wrong=mvc.perform(post("/api/v1/orders/{orderRef}/topup-intents",P014TopupFixtureLoader.ORDER_REF)
                        .header(TestAccessTokenFilter.HEADER_NAME,"wrong").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectCode").value("TOPUP_NOT_AVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(wrong).isEqualTo(missing).doesNotContain(P014TopupFixtureLoader.ORDER_REF);
    }

    @Test void invalid_get_shapes_are_same_shape_and_rejected_before_business_read() throws Exception {
        assertInputRejected(get("/api/v1/orders/{orderRef}/projection", P014TopupFixtureLoader.ORDER_REF)
                .header(TestAccessTokenFilter.HEADER_NAME, "p014-runtime-token")
                .contentType(APPLICATION_JSON).content("{}"), "TOPUP_PROGRESS_NOT_AVAILABLE");
        assertInputRejected(get("/api/v1/orders/{orderRef}/projection", P014TopupFixtureLoader.ORDER_REF)
                .header(TestAccessTokenFilter.HEADER_NAME, "p014-runtime-token").param("unknown", "x"),
                "TOPUP_PROGRESS_NOT_AVAILABLE");
        assertInputRejected(validResultGet().contentType(APPLICATION_JSON).content("{}"),
                "TOPUP_INTENT_QUERY_NOT_AVAILABLE");
        assertInputRejected(get("/api/v1/orders/{orderRef}/topup-intents/result", P014TopupFixtureLoader.ORDER_REF)
                .header(TestAccessTokenFilter.HEADER_NAME, "p014-runtime-token")
                .param("commandId", "CMD").param("idempotencyKey", "IDEM").param("sessionVersion", "1"),
                "TOPUP_INTENT_QUERY_NOT_AVAILABLE");
        assertInputRejected(validResultGet().param("unknown", "x"), "TOPUP_INTENT_QUERY_NOT_AVAILABLE");
        assertInputRejected(validResultGet().param("commandId", "SECOND"), "TOPUP_INTENT_QUERY_NOT_AVAILABLE");
        assertInputRejected(get("/api/v1/orders/{orderRef}/topup-intents/result", P014TopupFixtureLoader.ORDER_REF)
                .header(TestAccessTokenFilter.HEADER_NAME, "p014-runtime-token")
                .param("commandId", " ").param("idempotencyKey", "IDEM").param("sessionVersion", "1")
                .param("authorizationSetRef", "AUTHSET-P014-SYN-001"), "TOPUP_INTENT_QUERY_NOT_AVAILABLE");
        assertInputRejected(get("/api/v1/orders/{orderRef}/topup-intents/result", P014TopupFixtureLoader.ORDER_REF)
                .header(TestAccessTokenFilter.HEADER_NAME, "p014-runtime-token")
                .param("commandId", "CMD").param("idempotencyKey", "IDEM").param("sessionVersion", "0")
                .param("authorizationSetRef", "AUTHSET-P014-SYN-001"), "TOPUP_INTENT_QUERY_NOT_AVAILABLE");
    }

    private MockHttpServletRequestBuilder validResultGet() {
        return get("/api/v1/orders/{orderRef}/topup-intents/result", P014TopupFixtureLoader.ORDER_REF)
                .header(TestAccessTokenFilter.HEADER_NAME, "p014-runtime-token")
                .param("commandId", "CMD").param("idempotencyKey", "IDEM").param("sessionVersion", "1")
                .param("authorizationSetRef", "AUTHSET-P014-SYN-001");
    }

    private void assertInputRejected(MockHttpServletRequestBuilder request, String projectCode) throws Exception {
        Map<String, Long> before = service.countsForTest();
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.requestRef").doesNotExist())
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.projectCode").value(projectCode))
                .andExpect(jsonPath("$.resourceRef").doesNotExist())
                .andExpect(jsonPath("$.aggregateVersion").doesNotExist())
                .andExpect(jsonPath("$.currentProjection").doesNotExist())
                .andExpect(jsonPath("$.retryClass").value("NONE"))
                .andExpect(jsonPath("$.nextPollAt").doesNotExist());
        assertThat(service.countsForTest()).containsExactlyInAnyOrderEntriesOf(before);
    }
}
