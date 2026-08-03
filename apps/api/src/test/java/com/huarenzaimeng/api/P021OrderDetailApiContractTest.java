package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.huarenzaimeng.api.P021OrderDetailDomain.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory", "hz.p021.mode=local-synthetic", "hz.p021.fixture-mode=no-default",
        "hz.test-access-token=p021-local-synthetic-contract-token"
})
@AutoConfigureMockMvc
class P021OrderDetailApiContractTest {
    private static final String TOKEN = "p021-local-synthetic-contract-token";
    private static final LocalSyntheticIdentity ID = LocalSyntheticIdentity.fromToken(TOKEN);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LocalSyntheticOrderRecoveryService recovery;
    @Autowired P021OrderDetailService service;
    @Autowired P021OrderDetailBoundaryAdapters boundaryAdapters;

    @BeforeEach void reset() { install(base(UserOrderStateCode.AWAITING_PAYMENT, null)); }
    @AfterEach void clean() { recovery.resetForTest(); service.resetForTest(); }

    @ParameterizedTest
    @EnumSource(UserOrderStateCode.class)
    void allElevenStatesReturnStrictReadOnlyDetail(UserOrderStateCode state) throws Exception {
        String support = supports(state) ? "SUPPORT-P021-1" : null;
        install(base(state, support)); Map<String, Long> before = service.countsForTest();
        JsonNode body = json.readTree(mvc.perform(authenticated(get("/api/v1/orders/{orderRef}", P021OrderDetailFixtureLoader.ORDER_REF)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.projectCode").value(PROJECT_CODE_READ))
                .andExpect(jsonPath("$.currentProjection.stateCode").value(state.name()))
                .andReturn().getResponse().getContentAsString());
        assertStrictShape(body); assertReadDelta(before, service.countsForTest(), 1);
        List<String> actions = new ArrayList<>(); body.path("currentProjection").path("allowedActions")
                .forEach(node -> actions.add(node.path("actionCode").asText()));
        assertThat(actions).containsExactlyElementsOf(support == null
                ? List.of("REFRESH_ORDER_DETAIL", "SAFE_BACK")
                : List.of("REFRESH_ORDER_DETAIL", "OPEN_SUPPORT", "SAFE_BACK"));
    }

    @Test void invalidBodyQueryUnauthForeignMissingRevokedAndDriftAreSameShape() throws Exception {
        String expected = unavailableJson();
        assertSame(expected, mvc.perform(get("/api/v1/orders/{orderRef}", P021OrderDetailFixtureLoader.ORDER_REF))
                .andReturn().getResponse().getContentAsString());
        assertSame(expected, mvc.perform(authenticated(get("/api/v1/orders/{orderRef}", "ORDER-P021-MISSING")))
                .andReturn().getResponse().getContentAsString());
        assertSame(expected, mvc.perform(authenticated(get("/api/v1/orders/{orderRef}", P021OrderDetailFixtureLoader.ORDER_REF)
                        .queryParam("unexpected", "1"))).andReturn().getResponse().getContentAsString());
        assertSame(expected, mvc.perform(authenticated(get("/api/v1/orders/{orderRef}", P021OrderDetailFixtureLoader.ORDER_REF)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andReturn().getResponse().getContentAsString());
        assertThat(service.read(ENVIRONMENT, "SYN-SUBJECT-FOREIGN", ID.sessionRef(),
                P021OrderDetailFixtureLoader.ORDER_REF)).isEqualTo(unavailable());
        recovery.revokeBuyerAuthorizationForTest(P021OrderDetailFixtureLoader.AUTHORIZATION_SET_REF);
        assertThat(service.read(ENVIRONMENT, ID.projectSubjectRef(), ID.sessionRef(),
                P021OrderDetailFixtureLoader.ORDER_REF)).isEqualTo(unavailable());
        install(base(UserOrderStateCode.AWAITING_PAYMENT, null));
        recovery.installLocalSyntheticBuyerAuthorization(ID.projectSubjectRef(), ID.sessionRef(), 1,
                P021OrderDetailFixtureLoader.AUTHORIZATION_SET_REF,
                P021OrderDetailFixtureLoader.AUTHORIZATION_EVIDENCE_VERSION, List.of());
        assertThat(service.read(ENVIRONMENT, ID.projectSubjectRef(), ID.sessionRef(),
                P021OrderDetailFixtureLoader.ORDER_REF)).isEqualTo(unavailable());
        install(base(UserOrderStateCode.AWAITING_PAYMENT, null));
        recovery.installLocalSyntheticBuyerAuthorization(ID.projectSubjectRef(), ID.sessionRef(), 2,
                P021OrderDetailFixtureLoader.AUTHORIZATION_SET_REF, "AUTH-EVIDENCE-P021-V2",
                List.of(P021OrderDetailFixtureLoader.ORDER_REF));
        assertThat(service.read(ENVIRONMENT, ID.projectSubjectRef(), ID.sessionRef(),
                P021OrderDetailFixtureLoader.ORDER_REF)).isEqualTo(unavailable());
    }

    @Test void strictNestedFieldCountsAndCrossBindingsAreExact() throws Exception {
        JsonNode body = read(); JsonNode projection = body.path("currentProjection");
        assertThat(body.size()).isEqualTo(8); assertThat(projection.size()).isEqualTo(13);
        assertThat(projection.path("priceSnapshotSummary").size()).isEqualTo(10);
        assertThat(projection.path("timeline").get(0).size()).isEqualTo(6);
        projection.path("allowedActions").forEach(action -> assertThat(action.size()).isEqualTo(4));
        assertThat(body.path("resourceRef").asText()).isEqualTo(projection.path("orderRef").asText());
        assertThat(body.path("aggregateVersion").asLong()).isEqualTo(projection.path("aggregateVersion").asLong());
        assertThat(projection.path("timeline").get(0).path("projectionVersion").asLong())
                .isEqualTo(projection.path("projectionVersion").asLong());
    }

    @Test void strictDtoMutationsAreRejectedBeforeUse() throws Exception {
        Response response = service.read(ENVIRONMENT, ID.projectSubjectRef(), ID.sessionRef(),
                P021OrderDetailFixtureLoader.ORDER_REF);
        for (String mutation : List.of("MISSING", "ADDITIONAL", "UNKNOWN_ENUM", "NULL", "TYPE", "CROSS")) {
            ObjectNode tree = (ObjectNode) json.valueToTree(response);
            switch (mutation) {
                case "MISSING" -> tree.remove("projectCode");
                case "ADDITIONAL" -> tree.put("extra", "forbidden");
                case "UNKNOWN_ENUM" -> ((ObjectNode) tree.path("currentProjection")).put("stateCode", "THIRD_PARTY_SUCCESS");
                case "NULL" -> ((ObjectNode) tree.path("currentProjection")).putNull("priceSnapshotSummary");
                case "TYPE" -> tree.set("aggregateVersion", json.createObjectNode().put("bad", 1));
                case "CROSS" -> tree.put("resourceRef", "ORDER-P021-OTHER");
            }
            boolean rejected;
            try { rejected = !service.isStrictResponse(json.treeToValue(tree, Response.class)); }
            catch (Exception error) { rejected = true; }
            assertThat(rejected).as(mutation).isTrue();
        }
        for (String pointer : List.of("currentProjection", "currentProjection/priceSnapshotSummary",
                "currentProjection/timeline/0", "currentProjection/allowedActions/0")) {
            ObjectNode tree = (ObjectNode) json.valueToTree(response);
            JsonNode target = tree;
            for (String segment : pointer.split("/")) {
                target = segment.chars().allMatch(Character::isDigit)
                        ? target.path(Integer.parseInt(segment)) : target.path(segment);
            }
            ((ObjectNode) target).put("extra", "forbidden");
            assertThatThrownBy(() -> json.treeToValue(tree, Response.class)).as(pointer).isInstanceOf(Exception.class);
        }
    }

    @Test void monotonicTimelineAcceptsHigherAndRejectsLateRollbackAndConflict() {
        Fixture valid = base(UserOrderStateCode.PAYMENT_PROCESSING, null);
        TimelineItem first = new TimelineItem("TL-1", 1, 1, UserOrderStateCode.AWAITING_PAYMENT,
                Instant.parse("2026-08-03T00:00:00Z"), "WAITING_PAYMENT");
        TimelineItem second = new TimelineItem("TL-2", 2, 2, UserOrderStateCode.PAYMENT_PROCESSING,
                Instant.parse("2026-08-03T00:01:00Z"), "PAYMENT_PROCESSING");
        install(withTimeline(valid, 2, UserOrderStateCode.PAYMENT_PROCESSING, List.of(first, second)));
        assertThat(readDirect().projectCode()).isEqualTo(PROJECT_CODE_READ);

        install(withTimeline(valid, 2, UserOrderStateCode.AWAITING_PAYMENT, List.of(first, second)));
        assertThat(readDirect().projectCode()).isEqualTo(PROJECT_CODE_ERROR);
        TimelineItem rollback = new TimelineItem("TL-2", 2, 1, UserOrderStateCode.AWAITING_PAYMENT,
                Instant.parse("2026-08-03T00:01:00Z"), "ROLLBACK");
        install(withTimeline(valid, 2, UserOrderStateCode.PAYMENT_PROCESSING, List.of(first, rollback)));
        assertThat(readDirect().projectCode()).isEqualTo(PROJECT_CODE_ERROR);
        TimelineItem conflict = new TimelineItem("TL-1", 2, 2, UserOrderStateCode.PAYMENT_PROCESSING,
                Instant.parse("2026-08-03T00:01:00Z"), "CONFLICT");
        install(withTimeline(valid, 2, UserOrderStateCode.PAYMENT_PROCESSING, List.of(first, conflict)));
        assertThat(readDirect().projectCode()).isEqualTo(PROJECT_CODE_ERROR);
    }

    @Test void supportReferenceAndOpenSupportAreStrictlyEquivalent() {
        Fixture support = base(UserOrderStateCode.TOPUP_RESULT_UNKNOWN, "SUPPORT-P021-1");
        install(support);
        assertThat(readDirect().projectCode()).isEqualTo(PROJECT_CODE_READ);

        Projection p = support.projection();
        Projection missingReference = new Projection(p.orderRef(), p.aggregateVersion(), p.projectionVersion(),
                p.stateCode(), p.priceSnapshotSummary(), p.confirmedItems(), p.unknownItems(), p.responsibilityCode(),
                p.updatedAt(), p.nextReviewPoint(), p.timeline(), actions(p.projectionVersion(), p.stateCode(), null), null);
        install(rebind(support, missingReference, support.priceSnapshotDigest()));
        assertThat(readDirect().projectCode()).isEqualTo(PROJECT_CODE_ERROR);

        Fixture ordinary = base(UserOrderStateCode.AWAITING_PAYMENT, null);
        Projection ordinaryProjection = ordinary.projection();
        Projection unexpectedReference = new Projection(ordinaryProjection.orderRef(), ordinaryProjection.aggregateVersion(),
                ordinaryProjection.projectionVersion(), ordinaryProjection.stateCode(), ordinaryProjection.priceSnapshotSummary(),
                ordinaryProjection.confirmedItems(), ordinaryProjection.unknownItems(), ordinaryProjection.responsibilityCode(),
                ordinaryProjection.updatedAt(), ordinaryProjection.nextReviewPoint(), ordinaryProjection.timeline(),
                ordinaryProjection.allowedActions(), "SUPPORT-P021-UNAPPROVED");
        install(rebind(ordinary, unexpectedReference, ordinary.priceSnapshotDigest()));
        assertThat(readDirect().projectCode()).isEqualTo(PROJECT_CODE_ERROR);
    }

    @Test void fixtureDigestBindsEveryUserVisibleProjectionSemantic() {
        Fixture fixture = base(UserOrderStateCode.TOPUP_RESULT_UNKNOWN, "SUPPORT-P021-1");
        Projection p = fixture.projection();
        List<Projection> mutations = List.of(
                new Projection(p.orderRef(), p.aggregateVersion(), p.projectionVersion(), p.stateCode(), p.priceSnapshotSummary(),
                        List.of("CHANGED_CONFIRMED"), p.unknownItems(), p.responsibilityCode(), p.updatedAt(), p.nextReviewPoint(), p.timeline(), p.allowedActions(), p.supportRef()),
                new Projection(p.orderRef(), p.aggregateVersion(), p.projectionVersion(), p.stateCode(), p.priceSnapshotSummary(),
                        p.confirmedItems(), List.of("CHANGED_UNKNOWN"), p.responsibilityCode(), p.updatedAt(), p.nextReviewPoint(), p.timeline(), p.allowedActions(), p.supportRef()),
                new Projection(p.orderRef(), p.aggregateVersion(), p.projectionVersion(), p.stateCode(), p.priceSnapshotSummary(),
                        p.confirmedItems(), p.unknownItems(), "SYSTEM_RECHECK", p.updatedAt(), p.nextReviewPoint(), p.timeline(), p.allowedActions(), p.supportRef()),
                new Projection(p.orderRef(), p.aggregateVersion(), p.projectionVersion(), p.stateCode(), p.priceSnapshotSummary(),
                        p.confirmedItems(), p.unknownItems(), p.responsibilityCode(), p.updatedAt().plusSeconds(1), p.nextReviewPoint(), p.timeline(), p.allowedActions(), p.supportRef()),
                new Projection(p.orderRef(), p.aggregateVersion(), p.projectionVersion(), p.stateCode(), p.priceSnapshotSummary(),
                        p.confirmedItems(), p.unknownItems(), p.responsibilityCode(), p.updatedAt(), p.updatedAt().plusSeconds(60), p.timeline(), p.allowedActions(), p.supportRef()),
                new Projection(p.orderRef(), p.aggregateVersion(), p.projectionVersion(), p.stateCode(), p.priceSnapshotSummary(),
                        p.confirmedItems(), p.unknownItems(), p.responsibilityCode(), p.updatedAt(), p.nextReviewPoint(),
                        List.of(new TimelineItem("TIMELINE-CHANGED", 1, p.projectionVersion(), p.stateCode(), p.updatedAt(), "CHANGED_TIMELINE")), p.allowedActions(), p.supportRef()),
                new Projection(p.orderRef(), p.aggregateVersion(), p.projectionVersion(), p.stateCode(), p.priceSnapshotSummary(),
                        p.confirmedItems(), p.unknownItems(), p.responsibilityCode(), p.updatedAt(), p.nextReviewPoint(), p.timeline(),
                        List.of(new AllowedAction("REFRESH_ORDER_DETAIL", true, p.projectionVersion(), null),
                                new AllowedAction("OPEN_SUPPORT", true, p.projectionVersion(), p.supportRef()),
                                new AllowedAction("SAFE_BACK", true, p.projectionVersion(), null)), "SUPPORT-P021-CHANGED")
        );
        for (Projection mutation : mutations) {
            Fixture tampered = withProjectionKeepingDigest(fixture, mutation);
            assertThat(P021OrderDetailService.fixtureDigest(tampered)).isNotEqualTo(fixture.fixtureDigest());
            install(tampered);
            assertThat(readDirect().projectCode()).isEqualTo(PROJECT_CODE_ERROR);
        }
    }

    @Test void priceSnapshotIdentityMaskAndPresenceFailClosed() {
        Fixture fixture = base(UserOrderStateCode.AWAITING_PAYMENT, null);
        install(withPrice(fixture, fixture.projection().priceSnapshotSummary(), "WRONG-DIGEST"));
        assertThat(readDirect().projectCode()).isEqualTo(PROJECT_CODE_ERROR);
        PriceSnapshotSummary unmasked = new PriceSnapshotSummary("PRICE-P021-SYN-1", 125000, "BDT", "DISPLAY-V1",
                "+8801712345678", "SYN Operator", "SYN Package", "1000 BDT", "BDT",
                Instant.parse("2026-08-03T01:00:00Z"));
        install(withPrice(fixture, unmasked, P021OrderDetailService.snapshotDigest(unmasked)));
        assertThat(readDirect().projectCode()).isEqualTo(PROJECT_CODE_ERROR);
        for (String bypass : List.of("+88017******78", "৮৮০১******৭৮", "1234 5678****", "1234****5678")) {
            PriceSnapshotSummary attempted = new PriceSnapshotSummary("PRICE-P021-SYN-1", 125000, "BDT", "DISPLAY-V1",
                    bypass, "SYN Operator", "SYN Package", "1000 BDT", "BDT", Instant.parse("2026-08-03T01:00:00Z"));
            install(withPrice(fixture, attempted, P021OrderDetailService.snapshotDigest(attempted)));
            assertThat(readDirect().projectCode()).as(bypass).isEqualTo(PROJECT_CODE_ERROR);
        }
        Projection p = fixture.projection(); Projection missing = new Projection(p.orderRef(), p.aggregateVersion(),
                p.projectionVersion(), p.stateCode(), null, p.confirmedItems(), p.unknownItems(),
                p.responsibilityCode(), p.updatedAt(), p.nextReviewPoint(), p.timeline(), p.allowedActions(), p.supportRef());
        install(rebind(fixture, missing, "INVALID"));
        assertThat(readDirect().projectCode()).isEqualTo(PROJECT_CODE_ERROR);
    }

    @Test void concurrentReadsAreReadOnlyAndCountExactlyOnceEach() throws Exception {
        int workers = 12; Map<String, Long> before = service.countsForTest();
        ExecutorService pool = Executors.newFixedThreadPool(workers); CountDownLatch ready = new CountDownLatch(workers), start = new CountDownLatch(1);
        List<Future<Response>> futures = new ArrayList<>();
        for (int index = 0; index < workers; index++) futures.add(pool.submit(() -> { ready.countDown(); start.await(); return readDirect(); }));
        ready.await(); start.countDown(); for (Future<Response> future : futures) assertThat(future.get().projectCode()).isEqualTo(PROJECT_CODE_READ); pool.shutdown();
        assertReadDelta(before, service.countsForTest(), workers);
    }

    @Test void everyObservedBoundaryIsSensitiveAndBusinessReadsKeepItZero() {
        Map<String, Long> baseline = service.countsForTest();
        for (P021OrderDetailSideEffectProbe.Counter counter : P021OrderDetailSideEffectProbe.Counter.values()) {
            if (counter == P021OrderDetailSideEffectProbe.Counter.QueryCall) continue;
            service.resetForTest(); boundaryAdapters.exerciseControlledBoundary(counter);
            assertThat(service.countsForTest().get(counter.name())).isEqualTo(1);
            service.resetForTest();
            assertThatThrownBy(() -> boundaryAdapters.assertDisconnectedBoundaryIsDetected(counter))
                    .isInstanceOf(AssertionError.class).hasMessageContaining(counter.name());
            assertThat(service.countsForTest().get(counter.name())).isZero();
        }
        install(base(UserOrderStateCode.AWAITING_PAYMENT, null)); baseline = service.countsForTest(); readDirect();
        assertReadDelta(baseline, service.countsForTest(), 1);
    }

    @Test void nonLocalAndDisabledRealityCannotProduceDetail() {
        assertThat(service.read("PRODUCTION", ID.projectSubjectRef(), ID.sessionRef(),
                P021OrderDetailFixtureLoader.ORDER_REF)).isEqualTo(unavailable());
        org.springframework.mock.env.MockEnvironment release = new org.springframework.mock.env.MockEnvironment();
        release.setActiveProfiles("release");
        P021OrderDetailService disabled = new P021OrderDetailService(recovery,
                new P021OrderDetailSideEffectProbe(), release, "local-synthetic", "in-memory");
        assertThat(disabled.read(ENVIRONMENT, ID.projectSubjectRef(), ID.sessionRef(),
                P021OrderDetailFixtureLoader.ORDER_REF)).isEqualTo(unavailable());
    }

    private JsonNode read() throws Exception { return json.readTree(mvc.perform(authenticated(get("/api/v1/orders/{orderRef}",
            P021OrderDetailFixtureLoader.ORDER_REF))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    private Response readDirect() { return service.read(ENVIRONMENT, ID.projectSubjectRef(), ID.sessionRef(),
            P021OrderDetailFixtureLoader.ORDER_REF); }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.header(com.huarenzaimeng.api.config.TestAccessTokenFilter.HEADER_NAME, TOKEN);
    }
    private void install(Fixture fixture) {
        recovery.resetForTest(); service.resetForTest();
        recovery.installLocalSyntheticBuyerAuthorization(ID.projectSubjectRef(), ID.sessionRef(), 1,
                P021OrderDetailFixtureLoader.AUTHORIZATION_SET_REF,
                P021OrderDetailFixtureLoader.AUTHORIZATION_EVIDENCE_VERSION,
                List.of(P021OrderDetailFixtureLoader.ORDER_REF));
        service.installLocalSyntheticFixture(fixture);
    }
    private static Fixture base(UserOrderStateCode state, String support) {
        return P021OrderDetailFixtureLoader.fixture(ID, state, 2, 2, support,
                List.of("PAYMENT_CONFIRMATION"), List.of("TOPUP_RESULT"));
    }
    private static Fixture withTimeline(Fixture fixture, long projectionVersion, UserOrderStateCode state,
                                        List<TimelineItem> timeline) {
        Projection p = fixture.projection(); Projection changed = new Projection(p.orderRef(), p.aggregateVersion(),
                projectionVersion, state, p.priceSnapshotSummary(), p.confirmedItems(), p.unknownItems(),
                p.responsibilityCode(), p.updatedAt(), p.nextReviewPoint(), timeline,
                actions(projectionVersion, state, p.supportRef()), supports(state) ? p.supportRef() : null);
        return rebind(fixture, changed, fixture.priceSnapshotDigest());
    }
    private static Fixture withPrice(Fixture fixture, PriceSnapshotSummary price, String digest) {
        Projection p = fixture.projection(); Projection changed = new Projection(p.orderRef(), p.aggregateVersion(),
                p.projectionVersion(), p.stateCode(), price, p.confirmedItems(), p.unknownItems(),
                p.responsibilityCode(), p.updatedAt(), p.nextReviewPoint(), p.timeline(), p.allowedActions(), p.supportRef());
        return rebind(fixture, changed, digest);
    }
    private static Fixture withProjectionKeepingDigest(Fixture fixture, Projection projection) {
        return new Fixture(fixture.syntheticMarker(), fixture.environment(), fixture.realityEvidenceLevel(),
                fixture.projectSubjectRef(), fixture.sessionRole(), fixture.sessionVersion(), fixture.authorizationSetRef(),
                fixture.authorizationEvidenceVersion(), fixture.authorizedOrderRefs(), projection,
                fixture.priceSnapshotDigest(), fixture.fixtureSchemaVersion(), fixture.fixtureDigest());
    }
    private static Fixture rebind(Fixture fixture, Projection projection, String priceDigest) {
        Fixture draft = new Fixture(fixture.syntheticMarker(), fixture.environment(), fixture.realityEvidenceLevel(),
                fixture.projectSubjectRef(), fixture.sessionRole(), fixture.sessionVersion(), fixture.authorizationSetRef(),
                fixture.authorizationEvidenceVersion(), fixture.authorizedOrderRefs(), projection, priceDigest,
                fixture.fixtureSchemaVersion(), "PENDING");
        return new Fixture(draft.syntheticMarker(), draft.environment(), draft.realityEvidenceLevel(), draft.projectSubjectRef(),
                draft.sessionRole(), draft.sessionVersion(), draft.authorizationSetRef(), draft.authorizationEvidenceVersion(),
                draft.authorizedOrderRefs(), draft.projection(), draft.priceSnapshotDigest(), draft.fixtureSchemaVersion(),
                P021OrderDetailService.fixtureDigest(draft));
    }
    private static List<AllowedAction> actions(long version, UserOrderStateCode state, String support) {
        List<AllowedAction> result = new ArrayList<>(); result.add(new AllowedAction("REFRESH_ORDER_DETAIL", true, version, null));
        if (supports(state) && support != null) result.add(new AllowedAction("OPEN_SUPPORT", true, version, support));
        result.add(new AllowedAction("SAFE_BACK", true, version, null)); return List.copyOf(result);
    }
    private static boolean supports(UserOrderStateCode state) { return state == UserOrderStateCode.TOPUP_RESULT_UNKNOWN
            || state == UserOrderStateCode.CONFIRMED_NOT_DELIVERED || state == UserOrderStateCode.DELIVERY_REFUND_CONFLICT_REVIEW
            || state == UserOrderStateCode.SUPPORT_REVIEW; }
    private static void assertReadDelta(Map<String, Long> before, Map<String, Long> after, long queryDelta) {
        before.forEach((name, value) -> assertThat(after.get(name) - value).as(name)
                .isEqualTo(name.equals("QueryCall") ? queryDelta : 0));
    }
    private static void assertStrictShape(JsonNode body) {
        assertThat(body.size()).isEqualTo(8); assertThat(body.path("currentProjection").size()).isEqualTo(13);
        assertThat(body.path("currentProjection").path("priceSnapshotSummary").size()).isEqualTo(10);
        body.path("currentProjection").path("timeline").forEach(item -> assertThat(item.size()).isEqualTo(6));
        body.path("currentProjection").path("allowedActions").forEach(action -> assertThat(action.size()).isEqualTo(4));
    }
    private static void assertSame(String expected, String actual) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules(); assertThat(mapper.readTree(actual)).isEqualTo(mapper.readTree(expected));
    }
    private static String unavailableJson() { return "{\"requestRef\":null,\"outcome\":\"REJECTED\",\"projectCode\":\"ORDER_DETAIL_NOT_AVAILABLE\",\"resourceRef\":null,\"aggregateVersion\":null,\"currentProjection\":null,\"retryClass\":\"NONE\",\"nextPollAt\":null}"; }
    private static Response unavailable() { return new Response(null, "REJECTED", PROJECT_CODE_UNAVAILABLE, null, null, null, "NONE", null); }
}
