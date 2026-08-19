package com.huarenzaimeng.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory", "hz.project-auth.content-token=trusted-content-token",
        "hz.project-auth.content-actor=ACTOR-CONTENT-TRUSTED",
        "hz.project-auth.authorization-ref=AUTH-CONTENT-TEST"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired ContentService service;
    @Autowired ContentStore store;
    @MockBean Clock clock;
    private String ref;
    private Instant now;

    @BeforeEach void setUp() {
        now = Instant.parse("2026-08-01T12:00:00Z");
        when(clock.instant()).thenAnswer(ignored -> now);
        ref = "CNT-" + UUID.randomUUID();
        service.createTestFixture(ref, "Synthetic company", "Local mock only", "LIFE_SERVICE");
    }

    @Test void publishesOnlyCompleteEvidenceAndPublicProjectionIsMinimal() throws Exception {
        review(1, "R1", "RI1"); publish(2, "P1", "PI1");
        mvc.perform(get("/project-api/v1/content/items"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items[*].contentRef", org.hamcrest.Matchers.hasItem(ref)))
                .andExpect(jsonPath("$.data.items[*].qualification",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("ELIGIBLE"))))
                .andExpect(jsonPath("$.data.items[0].sourceRef").doesNotExist());
        mvc.perform(get("/project-api/v1/content/items/{ref}", ref).param("contentVersion", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.sourceCategory").value("SELF_RESEARCH"))
                .andExpect(jsonPath("$.data.verificationScope")
                        .value("NAME_AND_PUBLIC_CONTACT_CHANNELS"))
                .andExpect(jsonPath("$.data.verifiedBy").doesNotExist())
                .andExpect(jsonPath("$.data.auditTrail").doesNotExist());
    }

    @Test void untrustedSelfReportedHeadersAreRejectedIdentically() throws Exception {
        mvc.perform(get("/project-api/v1/internal/content/items/{ref}", ref)
                        .header("X-Project-Role", "ROLE-CONTENT").header("X-Actor-Ref", "forged"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.projectCode")
                        .value("AUTHORIZATION_REQUIRED"));
        mvc.perform(get("/project-api/v1/internal/content/items/{ref}", ref)
                        .header("X-HZM-Test-Access-Token", "wrong"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.projectCode")
                        .value("AUTHORIZATION_REQUIRED"));
    }

    @Test void reportRechecksVersionAndEligibilityAndReturnsA120Receipt() throws Exception {
        review(1, "R2", "RI2"); publish(2, "P2", "PI2");
        mvc.perform(post("/project-api/v1/content/items/{ref}/reports", ref)
                        .contentType(MediaType.APPLICATION_JSON).content(report("E1", "EI1", 2, "wrong")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.projectCode")
                        .value("CONTENT_VERSION_STALE"));
        mvc.perform(post("/project-api/v1/content/items/{ref}/reports", ref)
                        .contentType(MediaType.APPLICATION_JSON).content(report("E2", "EI2", 3, "error")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status")
                        .value("CONTENT_ERROR_REPORTED"))
                .andExpect(jsonPath("$.data.reviewTarget").value("A120"));
        mvc.perform(get("/project-api/v1/internal/content/items/{ref}", ref).headers(auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.state").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.data.auditTrail[2].scope").value("PUBLIC_CONTENT_REPORT"))
                .andExpect(jsonPath("$.data.auditTrail[2].authorizationRef").value("AUTH-PUBLIC-REPORT"))
                .andExpect(jsonPath("$.data.auditTrail[2].result").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.auditTrail[2].evidenceRef").value("A120"));
    }

    @Test void expiryUsesOneControlledClockForListAndDetail() throws Exception {
        review(1, "R3", "RI3"); publish(2, "P3", "PI3");
        now = now.plusSeconds(3601);
        mvc.perform(get("/project-api/v1/content/items"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[*].contentRef",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(ref))));
        mvc.perform(get("/project-api/v1/content/items/{ref}", ref).param("contentVersion", "3"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.projectCode").value("CONTENT_QUALIFICATION_UNKNOWN"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test void unpublishRaceInvalidatesOldDetail() throws Exception {
        review(1, "R4", "RI4"); publish(2, "P4", "PI4");
        transition(3, "U4", "UI4", "UNPUBLISH");
        mvc.perform(get("/project-api/v1/content/items/{ref}", ref).param("contentVersion", "3"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.projectCode")
                        .value("CONTENT_VERSION_STALE"));
        mvc.perform(get("/project-api/v1/content/items/{ref}", ref).param("contentVersion", "4"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test void commandOrIdempotencyReuseWithDifferentParametersHasNoSideEffect() throws Exception {
        review(1, "R5", "RI5"); publish(2, "P5", "PI5");
        mvc.perform(post("/project-api/v1/content/items/{ref}/reports", ref)
                        .contentType(MediaType.APPLICATION_JSON).content(report("E5", "EI5", 3, "a")))
                .andExpect(status().isOk());
        mvc.perform(post("/project-api/v1/content/items/{ref}/reports", ref)
                        .contentType(MediaType.APPLICATION_JSON).content(report("E5", "OTHER", 3, "b")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.projectCode")
                        .value("IDEMPOTENCY_CONFLICT"));
        mvc.perform(post("/project-api/v1/content/items/{ref}/reports", ref)
                        .contentType(MediaType.APPLICATION_JSON).content(report("OTHER", "EI5", 3, "c")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.projectCode")
                        .value("IDEMPOTENCY_CONFLICT"));
        mvc.perform(get("/project-api/v1/internal/content/items/{ref}", ref).headers(auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.state").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.data.auditTrail.length()").value(3));
    }

    @Test void onlySelfResearchCanBecomePublicEligible() throws Exception {
        String body = reviewCommand("R-SOURCE", "RI-SOURCE", 1, "PARTNER_SUBMISSION");
        mvc.perform(post("/project-api/v1/internal/content/items/{ref}/transitions", ref)
                        .headers(auth()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("VERIFIED"));
        mvc.perform(post("/project-api/v1/internal/content/items/{ref}/transitions", ref)
                        .headers(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content(transitionCommand("P-SOURCE", "PI-SOURCE", 2, "PUBLISH")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.projectCode").value("CONTENT_NOT_ELIGIBLE"));
        mvc.perform(get("/project-api/v1/content/items"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[*].contentRef",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(ref))));
    }

    @Test void projectVersionsUsePositiveJavascriptSafeIntegerBoundary() throws Exception {
        String maxRef = "CNT-MAX-" + UUID.randomUUID();
        store.createForTest(eligible(maxRef, ProjectApiVersion.MAX));
        store.createForTest(eligible("CNT-OVER-" + UUID.randomUUID(), ProjectApiVersion.MAX + 1));

        mvc.perform(get("/project-api/v1/content/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].contentRef", org.hamcrest.Matchers.hasItem(maxRef)))
                .andExpect(jsonPath("$.data.items[*].contentVersion",
                        org.hamcrest.Matchers.hasItem(ProjectApiVersion.MAX)));
        mvc.perform(get("/project-api/v1/content/items/{ref}", maxRef)
                        .param("contentVersion", String.valueOf(ProjectApiVersion.MAX)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version")
                        .value(ProjectApiVersion.MAX));
        mvc.perform(get("/project-api/v1/content/items/{ref}", maxRef).param("contentVersion", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.projectCode").value("INVALID_REQUEST"));
        mvc.perform(get("/project-api/v1/content/items/{ref}", maxRef)
                        .param("contentVersion", String.valueOf(ProjectApiVersion.MAX + 1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.projectCode").value("INVALID_REQUEST"));

        review(1, "R-BOUND", "RI-BOUND"); publish(2, "P-BOUND", "PI-BOUND");
        mvc.perform(post("/project-api/v1/content/items/{ref}/reports", ref)
                        .contentType(MediaType.APPLICATION_JSON).content(report("E-ZERO", "EI-ZERO", 0, "zero")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.projectCode").value("INVALID_REQUEST"));
        String over = "{\"commandId\":\"E-OVER\",\"idempotencyKey\":\"EI-OVER\","
                + "\"contentVersion\":" + (ProjectApiVersion.MAX + 1) + ",\"expectedAggregateVersion\":3,"
                + "\"reason\":\"over\"}";
        mvc.perform(post("/project-api/v1/content/items/{ref}/reports", ref)
                        .contentType(MediaType.APPLICATION_JSON).content(over))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.projectCode").value("INVALID_REQUEST"));
        String maxExpected = "{\"commandId\":\"E-MAX\",\"idempotencyKey\":\"EI-MAX\","
                + "\"contentVersion\":3,\"expectedAggregateVersion\":" + ProjectApiVersion.MAX + ","
                + "\"reason\":\"max\"}";
        mvc.perform(post("/project-api/v1/content/items/{ref}/reports", ref)
                        .contentType(MediaType.APPLICATION_JSON).content(maxExpected))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.projectCode")
                        .value("CONTENT_VERSION_STALE"));
    }

    private void review(long version, String command, String idem) throws Exception {
        String body = reviewCommand(command, idem, version, "SELF_RESEARCH");
        mvc.perform(post("/project-api/v1/internal/content/items/{ref}/transitions", ref)
                        .headers(auth()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("VERIFIED"));
    }

    private String reviewCommand(String command, String idem, long version, String sourceCategory) {
        return "{\"commandId\":\"" + command + "\",\"idempotencyKey\":\"" + idem
                + "\",\"expectedAggregateVersion\":" + version + ",\"action\":\"RECORD_REVIEW\","
                + "\"reason\":\"synthetic\",\"sourceCategory\":\"" + sourceCategory + "\","
                + "\"sourceRef\":\"SYN-1\",\"verificationScope\":\"public fields\","
                + "\"verifiedBy\":\"content-team\",\"verifiedAt\":\"" + now + "\","
                + "\"validUntil\":\"" + now.plusSeconds(3600) + "\"}";
    }

    private void publish(long version, String command, String idem) throws Exception {
        transition(version, command, idem, "PUBLISH");
    }

    private void transition(long version, String command, String idem, String action) throws Exception {
        String body = transitionCommand(command, idem, version, action);
        mvc.perform(post("/project-api/v1/internal/content/items/{ref}/transitions", ref)
                        .headers(auth()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private static String transitionCommand(String command, String idem, long version, String action) {
        return "{\"commandId\":\"" + command + "\",\"idempotencyKey\":\"" + idem
                + "\",\"expectedAggregateVersion\":" + version + ",\"action\":\"" + action
                + "\",\"reason\":\"synthetic\"}";
    }

    private DirectoryContent eligible(String contentRef, long version) {
        return new DirectoryContent(contentRef, "Synthetic company", "Local mock only", "LIFE_SERVICE",
                "SELF_OPERATED_CHINA_COMPANY", "SELF_RESEARCH", "SYN-SOURCE", "public fields",
                "content-team", now, now.plusSeconds(3600), version, ContentState.PUBLISHED, false, now,
                java.util.List.of());
    }

    private static String report(String command, String idem, long version, String reason) {
        return "{\"commandId\":\"" + command + "\",\"idempotencyKey\":\"" + idem
                + "\",\"contentVersion\":" + version + ",\"expectedAggregateVersion\":" + version
                + ",\"reason\":\"" + reason + "\"}";
    }

    private static org.springframework.http.HttpHeaders auth() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-HZM-Test-Access-Token", "trusted-content-token");
        return headers;
    }
}
