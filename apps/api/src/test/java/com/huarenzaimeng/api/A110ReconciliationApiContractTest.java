package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
        "spring.profiles.active=mock",
        "hz.a110.mode=local-synthetic",
        "hz.test-access-token=a110-local-synthetic-token"
})
@AutoConfigureMockMvc
class A110ReconciliationApiContractTest {
    private static final String TOKEN = "a110-local-synthetic-token";
    private static final String TOKEN_HEADER = "X-HZM-Test-Access-Token";
    private static final String SUBJECT = LocalSyntheticIdentity.fromToken(TOKEN).projectSubjectRef();
    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
    private static final Set<String> TOP_FIELDS = Set.of("requestRef", "viewState", "projectCode",
            "schemaVersion", "roleProjection", "roleBindingVersion", "authorizationDecisionVersion",
            "projectionVersion", "items", "allowedActions", "retryClass");
    private static final Set<String> FIN_FIELDS = Set.of("reconciliationRef", "orderRef", "supportRef",
            "factSummaries", "differenceCategories", "ageState", "responsibilityCode", "timeline",
            "nextReviewPoint", "updatedAt", "projectionVersion", "displayVersion");
    private static final Set<String> CS_FIELDS = Set.of("reconciliationRef", "supportRef", "orderRef",
            "maskedSubjectSummary", "userFacingSummary", "confirmedItems", "unconfirmedItems",
            "responsibilityCode", "nextReviewPoint", "updatedAt", "projectionVersion");
    private static final Set<String> FACT_FIELDS = Set.of("factState", "amountMinor", "currency",
            "occurredAt", "observedAt");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired A110ReconciliationService service;
    @Autowired A110ReconciliationSideEffectProbe sideEffects;

    @BeforeEach
    void prepare() { service.installFixtureForTest(finFixture("READY", List.of(finItem()))); }

    @Test
    void fin_ready_uses_strict_fin_projection_and_one_read_only_query() throws Exception {
        A110ReconciliationSnapshot before = service.snapshotForTest();
        JsonNode body = read(TOKEN);
        A110ReconciliationSnapshot after = service.snapshotForTest();

        assertStrictTop(body, "READY", "FIN");
        JsonNode item = body.path("items").get(0);
        assertThat(fields(item)).isEqualTo(FIN_FIELDS);
        assertThat(fields(item.path("factSummaries"))).containsExactlyInAnyOrder("W", "U", "D", "R", "L");
        item.path("factSummaries").forEach(value -> assertThat(fields(value)).isEqualTo(FACT_FIELDS));
        assertThat(item.has("maskedSubjectSummary")).isFalse();
        assertReadOnly(before, after);
    }

    @Test
    void cs_ready_uses_strict_cs_projection_without_fin_fields() throws Exception {
        service.installFixtureForTest(csFixture("READY", List.of(csItem())));
        JsonNode body = read(TOKEN);

        assertStrictTop(body, "READY", "CS");
        JsonNode item = body.path("items").get(0);
        assertThat(fields(item)).isEqualTo(CS_FIELDS);
        assertThat(item.has("factSummaries")).isFalse();
        assertThat(item.has("timeline")).isFalse();
        assertThat(item.has("differenceCategories")).isFalse();
    }

    @Test
    void content_unauthenticated_cross_subject_and_nonexistent_are_same_shape() throws Exception {
        service.installFixtureForTest(fixture("CONTENT", "AUTHORIZED", "READY", SUBJECT, List.of()));
        JsonNode content = read(TOKEN);
        JsonNode missingIdentity = read(null);

        service.installFixtureForTest(finFixture("ACCESS_DENIED", List.of()));
        JsonNode nonexistent = read(TOKEN);
        service.installFixtureForTest(fixture("FIN", "AUTHORIZED", "READY", "SYN-OTHER-SUBJECT", List.of(finItem())));
        JsonNode crossSubject = read(TOKEN);

        for (JsonNode body : List.of(content, missingIdentity, nonexistent, crossSubject)) assertDenied(body);
    }

    @Test
    void authorization_and_version_failures_close_without_items() throws Exception {
        for (A110ReconciliationFixture fixture : List.of(
                fixture("FIN", "UNKNOWN", "READY", SUBJECT, List.of(finItem())),
                fixture("FIN", "REVOKED", "READY", SUBJECT, List.of(finItem())),
                finFixture("VERSION_CONFLICT", List.of()))) {
            service.installFixtureForTest(fixture);
            JsonNode body = read(TOKEN);
            assertThat(body.path("items").isEmpty()).isTrue();
            assertThat(body.path("roleProjection").isNull()).isEqualTo(
                    Set.of("AUTHORITY_UNKNOWN", "REVOKED").contains(body.path("viewState").asText()));
        }
    }

    @Test
    void invalid_query_body_and_client_authority_headers_fail_closed() throws Exception {
        JsonNode query = response(mvc.perform(get("/api/v1/admin/reconciliations")
                .header(TOKEN_HEADER, TOKEN).queryParam("role", "FIN")).andReturn());
        JsonNode body = response(mvc.perform(get("/api/v1/admin/reconciliations")
                .header(TOKEN_HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn());
        JsonNode header = response(mvc.perform(get("/api/v1/admin/reconciliations")
                .header(TOKEN_HEADER, TOKEN).header("X-Role", "FIN")).andReturn());
        for (JsonNode value : List.of(query, body, header)) assertDenied(value);
    }

    @Test
    void server_states_have_unique_project_codes_and_frozen_actions() throws Exception {
        Map<String, List<String>> expected = Map.ofEntries(
                Map.entry("EMPTY", List.of("READ_REFRESH", "NAVIGATE_A100", "NAVIGATE_A140")),
                Map.entry("READ_ERROR", List.of("READ_REFRESH")),
                Map.entry("UNAVAILABLE", List.of("READ_REFRESH")),
                Map.entry("VERSION_CONFLICT", List.of("READ_REFRESH")),
                Map.entry("LONG_RUNNING_UNKNOWN", List.of("READ_REFRESH", "NAVIGATE_A100", "NAVIGATE_A140")),
                Map.entry("ASYMMETRIC_FACTS", List.of("READ_REFRESH", "NAVIGATE_A100", "NAVIGATE_A140")),
                Map.entry("REFUND_DELIVERY_CONFLICT", List.of("READ_REFRESH", "NAVIGATE_A100", "NAVIGATE_A140")));
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            List<?> items = switch (entry.getKey()) {
                case "LONG_RUNNING_UNKNOWN" -> List.of(longRunningUnknownItem());
                case "ASYMMETRIC_FACTS" -> List.of(asymmetricItem());
                case "REFUND_DELIVERY_CONFLICT" -> List.of(refundDeliveryConflictItem());
                default -> List.of();
            };
            service.installFixtureForTest(finFixture(entry.getKey(), items));
            JsonNode body = read(TOKEN);
            assertThat(body.path("viewState").asText()).isEqualTo(entry.getKey());
            assertThat(body.path("projectCode").asText()).isEqualTo("A110_" + entry.getKey());
            assertThat(json.convertValue(body.path("allowedActions"), List.class)).isEqualTo(entry.getValue());
        }
    }

    @Test
    void wrong_item_schema_duplicate_projection_or_unknown_fields_fail_closed() throws Exception {
        service.installFixtureForTest(fixture("FIN", "AUTHORIZED", "READY", SUBJECT, List.of(csItem())));
        assertThat(read(TOKEN).path("viewState").asText()).isEqualTo("READ_ERROR");
        service.installFixtureForTest(finFixture("READY", List.of(finItem(), finItem())));
        assertThat(read(TOKEN).path("viewState").asText()).isEqualTo("READ_ERROR");
    }

    @Test
    void fin_and_cs_item_projection_versions_must_match_top_level_without_writes() throws Exception {
        A110FinItem fin = finItem();
        A110FinItem mismatchedFin = new A110FinItem(fin.reconciliationRef(), fin.orderRef(), fin.supportRef(),
                fin.factSummaries(), fin.differenceCategories(), fin.ageState(), fin.responsibilityCode(),
                fin.timeline(), fin.nextReviewPoint(), fin.updatedAt(), 2L, fin.displayVersion());
        A110CsItem cs = csItem();
        A110CsItem mismatchedCs = new A110CsItem(cs.reconciliationRef(), cs.supportRef(), cs.orderRef(),
                cs.maskedSubjectSummary(), cs.userFacingSummary(), cs.confirmedItems(), cs.unconfirmedItems(),
                cs.responsibilityCode(), cs.nextReviewPoint(), cs.updatedAt(), 2L);
        for (A110ReconciliationFixture fixture : List.of(
                finFixture("READY", List.of(mismatchedFin)), csFixture("READY", List.of(mismatchedCs)))) {
            service.installFixtureForTest(fixture);
            A110ReconciliationSnapshot before = service.snapshotForTest();
            JsonNode body = read(TOKEN);
            A110ReconciliationSnapshot after = service.snapshotForTest();
            assertThat(body.path("viewState").asText()).isEqualTo("READ_ERROR");
            assertThat(body.path("items").isEmpty()).isTrue();
            assertReadOnly(before, after);
        }
    }

    @Test
    void read_outcome_must_match_fin_fact_and_difference_state() throws Exception {
        service.installFixtureForTest(finFixture("READY", List.of(refundDeliveryConflictItem())));
        assertThat(read(TOKEN).path("viewState").asText()).isEqualTo("READ_ERROR");
        service.installFixtureForTest(finFixture("REFUND_DELIVERY_CONFLICT",
                List.of(refundDeliveryConflictItem())));
        assertThat(read(TOKEN).path("viewState").asText()).isEqualTo("REFUND_DELIVERY_CONFLICT");
        service.installFixtureForTest(finFixture("ASYMMETRIC_FACTS", List.of(asymmetricItem())));
        assertThat(read(TOKEN).path("viewState").asText()).isEqualTo("ASYMMETRIC_FACTS");
        service.installFixtureForTest(finFixture("LONG_RUNNING_UNKNOWN", List.of(longRunningUnknownItem())));
        assertThat(read(TOKEN).path("viewState").asText()).isEqualTo("LONG_RUNNING_UNKNOWN");
    }

    @Test
    void refund_delivery_conflict_is_derived_from_facts_and_time_before_category_consistency() throws Exception {
        service.installFixtureForTest(finFixture("READY", List.of(refundDeliveryFactsItem(List.of()))));
        assertThat(read(TOKEN).path("viewState").asText()).isEqualTo("READ_ERROR");
        service.installFixtureForTest(finFixture("REFUND_DELIVERY_CONFLICT",
                List.of(refundDeliveryFactsItem(List.of("MISSING")))));
        assertThat(read(TOKEN).path("viewState").asText()).isEqualTo("READ_ERROR");
        A110FinItem noLateDelivery = finItem();
        A110FinItem falseCategory = new A110FinItem(noLateDelivery.reconciliationRef(), noLateDelivery.orderRef(),
                noLateDelivery.supportRef(), noLateDelivery.factSummaries(), List.of("REFUND_DELIVERY_CONFLICT"),
                noLateDelivery.ageState(), noLateDelivery.responsibilityCode(), noLateDelivery.timeline(),
                noLateDelivery.nextReviewPoint(), noLateDelivery.updatedAt(), noLateDelivery.projectionVersion(),
                noLateDelivery.displayVersion());
        service.installFixtureForTest(finFixture("REFUND_DELIVERY_CONFLICT", List.of(falseCategory)));
        assertThat(read(TOKEN).path("viewState").asText()).isEqualTo("READ_ERROR");
    }

    @Test
    void nullable_fin_support_and_fin_cs_next_review_fields_remain_present() throws Exception {
        A110FinItem fin = finItem();
        A110FinItem nullableFin = new A110FinItem(fin.reconciliationRef(), fin.orderRef(), null,
                fin.factSummaries(), fin.differenceCategories(), fin.ageState(), fin.responsibilityCode(),
                fin.timeline(), null, fin.updatedAt(), fin.projectionVersion(), fin.displayVersion());
        service.installFixtureForTest(finFixture("READY", List.of(nullableFin)));
        JsonNode finBody = read(TOKEN);
        assertThat(finBody.path("viewState").asText()).isEqualTo("READY");
        assertThat(finBody.path("items").get(0).has("supportRef")).isTrue();
        assertThat(finBody.path("items").get(0).path("supportRef").isNull()).isTrue();
        assertThat(finBody.path("items").get(0).path("nextReviewPoint").isNull()).isTrue();

        A110CsItem cs = csItem();
        A110CsItem nullableCs = new A110CsItem(cs.reconciliationRef(), cs.supportRef(), cs.orderRef(),
                cs.maskedSubjectSummary(), cs.userFacingSummary(), cs.confirmedItems(), cs.unconfirmedItems(),
                cs.responsibilityCode(), null, cs.updatedAt(), cs.projectionVersion());
        service.installFixtureForTest(csFixture("READY", List.of(nullableCs)));
        JsonNode csBody = read(TOKEN);
        assertThat(csBody.path("viewState").asText()).isEqualTo("READY");
        assertThat(csBody.path("items").get(0).has("nextReviewPoint")).isTrue();
        assertThat(csBody.path("items").get(0).path("nextReviewPoint").isNull()).isTrue();
    }

    @Test
    void observer_is_sensitive_for_every_frozen_write_and_external_boundary() {
        for (A110ReconciliationSideEffectProbe.Boundary boundary
                : A110ReconciliationSideEffectProbe.Boundary.values()) {
            sideEffects.resetForTest();
            Map<String, Long> before = sideEffects.snapshot();
            sideEffects.observeForSensitivityTest(boundary);
            Map<String, Long> after = sideEffects.snapshot();
            assertThat(after.get(boundary.name()) - before.get(boundary.name())).isEqualTo(1L);
        }
    }

    private JsonNode read(String token) throws Exception {
        var request = get("/api/v1/admin/reconciliations");
        if (token != null) request.header(TOKEN_HEADER, token);
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        return response(result);
    }

    private JsonNode response(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private static void assertStrictTop(JsonNode body, String state, String role) {
        assertThat(fields(body)).isEqualTo(TOP_FIELDS);
        assertThat(body.path("schemaVersion").asText()).isEqualTo("A110_RECONCILIATION_READ_V1");
        assertThat(body.path("viewState").asText()).isEqualTo(state);
        assertThat(body.path("projectCode").asText()).isEqualTo("A110_" + state);
        assertThat(body.path("roleProjection").asText()).isEqualTo(role);
        assertThat(body.path("requestRef").asText()).startsWith("SYN-A110-");
    }

    private static void assertDenied(JsonNode body) {
        assertThat(fields(body)).isEqualTo(TOP_FIELDS);
        assertThat(body.path("viewState").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.path("projectCode").asText()).isEqualTo("A110_ACCESS_DENIED");
        assertThat(body.path("roleProjection").isNull()).isTrue();
        assertThat(body.path("roleBindingVersion").isNull()).isTrue();
        assertThat(body.path("authorizationDecisionVersion").isNull()).isTrue();
        assertThat(body.path("projectionVersion").isNull()).isTrue();
        assertThat(body.path("items").isEmpty()).isTrue();
        assertThat(body.path("allowedActions").isEmpty()).isTrue();
    }

    private static void assertReadOnly(A110ReconciliationSnapshot before, A110ReconciliationSnapshot after) {
        assertThat(after.fixtureRevision()).isEqualTo(before.fixtureRevision());
        before.counters().forEach((boundary, value) -> assertThat(after.counters().get(boundary) - value)
                .as(boundary).isEqualTo(boundary.equals("QueryCall") ? 1L : 0L));
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(result::add);
        return result;
    }

    private static A110ReconciliationFixture finFixture(String state, List<?> items) {
        return fixture("FIN", "AUTHORIZED", state, SUBJECT, items);
    }

    private static A110ReconciliationFixture csFixture(String state, List<?> items) {
        return fixture("CS", "AUTHORIZED", state, SUBJECT, items);
    }

    private static A110ReconciliationFixture fixture(String role, String authorizationState, String state,
                                                      String subject, List<?> items) {
        return new A110ReconciliationFixture(true, "LOCAL_SYNTHETIC", subject, role, "SYN-RBV-1",
                A110ReconciliationService.READ_SCOPE, "SYN-ADV-1", authorizationState, 1L, state, items);
    }

    private static A110FinItem finItem() {
        Map<String, A110FactSummary> facts = Map.of(
                "W", fact("CONFIRMED", 1000L, "BDT"),
                "U", fact("CONFIRMED", 1000L, "BDT"),
                "D", fact("CONFIRMED", 1000L, "BDT"),
                "R", fact("ABSENT_CONFIRMED", null, null),
                "L", fact("CONFIRMED", 1000L, "BDT"));
        return new A110FinItem("SYN-REC-001", "SYN-ORDER-001", "SYN-SUPPORT-001", facts,
                List.of(), "CURRENT", "FIN_REVIEW",
                List.of(new A110TimelineEntry("W", "CONFIRMED", NOW.minusSeconds(60), NOW)),
                NOW.plusSeconds(3600), NOW, 1L, "SYN-DISPLAY-V1");
    }

    private static A110FinItem refundDeliveryConflictItem() {
        return refundDeliveryFactsItem(List.of("REFUND_DELIVERY_CONFLICT"));
    }

    private static A110FinItem refundDeliveryFactsItem(List<String> categories) {
        A110FinItem base = finItem();
        Map<String, A110FactSummary> facts = new java.util.HashMap<>(base.factSummaries());
        facts.put("R", new A110FactSummary("CONFIRMED", 1000L, "BDT", NOW.minusSeconds(120), NOW));
        facts.put("D", new A110FactSummary("CONFIRMED", 1000L, "BDT", NOW.minusSeconds(60), NOW));
        return new A110FinItem(base.reconciliationRef(), base.orderRef(), base.supportRef(), Map.copyOf(facts),
                categories, base.ageState(), base.responsibilityCode(), base.timeline(),
                base.nextReviewPoint(), base.updatedAt(), base.projectionVersion(), base.displayVersion());
    }

    private static A110FinItem asymmetricItem() {
        A110FinItem base = finItem();
        return new A110FinItem(base.reconciliationRef(), base.orderRef(), base.supportRef(), base.factSummaries(),
                List.of("MISSING"), base.ageState(), base.responsibilityCode(), base.timeline(),
                base.nextReviewPoint(), base.updatedAt(), base.projectionVersion(), base.displayVersion());
    }

    private static A110FinItem longRunningUnknownItem() {
        A110FinItem base = finItem();
        Map<String, A110FactSummary> facts = new java.util.HashMap<>(base.factSummaries());
        facts.put("W", fact("UNKNOWN", null, null));
        facts.put("U", fact("UNKNOWN", null, null));
        facts.put("D", fact("UNKNOWN", null, null));
        return new A110FinItem(base.reconciliationRef(), base.orderRef(), base.supportRef(), Map.copyOf(facts),
                List.of(), "LONG_RUNNING", base.responsibilityCode(), base.timeline(), base.nextReviewPoint(),
                base.updatedAt(), base.projectionVersion(), base.displayVersion());
    }

    private static A110FactSummary fact(String state, Long amount, String currency) {
        return new A110FactSummary(state, amount, currency,
                "ABSENT_CONFIRMED".equals(state) ? null : NOW.minusSeconds(60), NOW);
    }

    private static A110CsItem csItem() {
        return new A110CsItem("SYN-REC-001", "SYN-SUPPORT-001", "SYN-ORDER-001", "已脱敏主体",
                "充值结果仍在核对", List.of("PAYMENT_RECEIVED"), List.of("DELIVERY_UNKNOWN"),
                "CS_FOLLOW_UP", NOW.plusSeconds(3600), NOW, 1L);
    }
}
