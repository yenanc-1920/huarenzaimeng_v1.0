package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.huarenzaimeng.api.P021OrderDetailDomain.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory", "hz.p021.mode=local-synthetic", "hz.p021.fixture-mode=no-default",
        "hz.test-access-token=p021-local-synthetic-contract-token"
})
class P021OrderDetailEvidenceFinalRunTest {
    static final String IMPLEMENTATION_SHA = "C4A8D77A5422354C546473DAF013893952349A447349B3E8CC9F89A884CF0ADE";
    static final String D3_REGISTRY_SHA = "6321D16CAC17D426801F26F5AD643BAB418D08FF72FD299BCEA7FB20C9522A0C";
    static final String IDENTITY_SHA = "A0134787E0484705ADE5E32383D778D7851BC2DAD72FE9F6873752CBE639A8DE";
    static final String TOKEN = "p021-local-synthetic-contract-token";
    static final String PROCESS_EVIDENCE_REF = "process-evidence.json";
    static final Map<String, String> FIXED_INPUTS = Map.ofEntries(
            Map.entry("D1RegistrySha", "364F73B28E2BC1C4D3827D5B9332C1CEB6D93C928859A226CD8842FF283D9BD8"),
            Map.entry("D2RegistrySha", "516EA3ACA003BEF18A49D5961F13DCCE313CAD4C1B60C44964519CA793CE52BF"),
            Map.entry("D3_03Sha", "33CCC6BB94B2D2A95E798C7CAD44C839CC35D8B577727492C64F10776E6FDA95"),
            Map.entry("D3_04Sha", "B12E780F19AB333458018C908324EADA6F84E1ECD35349FB739A7DC5AE22E4DB"),
            Map.entry("D3_05Sha", "CC27E94902FF2BCE86E02EE553F2E03F7E7752603AD247D826E619F929856282"),
            Map.entry("D3RegistrySha", D3_REGISTRY_SHA),
            Map.entry("D4_05Sha", "4054C8A4DA3C4D32BB7AF10F562827D399CB24380A474B765383791902617618"),
            Map.entry("D4_08Sha", "13DE50B5306F6CA2AEDA735484772DFCDEC00CDC11E0BE082B43787CD2D1250B"),
            Map.entry("D4RebindRecordSha", "65F59B4F6F4A5A7A6757D65862D09492217A236859D02B3A026242FDEDA62468"));
    static final Map<String, String> IMPLEMENTATION_FILES = Map.ofEntries(
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/LocalSyntheticOrderRecoveryService.java", "B99CCE05F16C634852216F2E43EDF61DDB5875A0871B9C8E7648712253B9B6D6"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/MockFlowController.java", "206148EEC6E1C7F183B0DDA938C2B591B952951071AB5C746980544BE3567E4C"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailBoundaryAdapters.java", "5F3917190EF6B876B501D492784C2A006A1AD748FF75A5D2C9E29A7622D5F185"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailController.java", "BC5C73C4D059CBC6304A28CAF546588C75CE74F212288550E85D62C675D8540B"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailDomain.java", "ABECE82E7F63954AF8A99B92F212F70366FB1D2996B4815A92BCFE8920BBB525"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailFixtureLoader.java", "612CC0503C0B50C98E06F9EBBB13B67CFFF31FC4A34FCF7912297020C0A8B1CD"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailService.java", "CD343493A6763562D3E7647C9FF405E6F9EC090DB65F45582FE138DCD9D9776C"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailSideEffectProbe.java", "1036AAC2DE7302A11F678511381C36EFCE766AD076ACBE4F7CDD8DE0256FF004"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/config/TestAccessTokenFilter.java", "BD53C39F56B091B42F3D0569A87EACFE39C1BB07A88210ACC842106F60A03D91"),
            Map.entry("apps/api/src/test/java/com/huarenzaimeng/api/MockFlowApiContractTest.java", "7093D57111BAE5CF2CA902F012AD897510C3D9EF9D86F1AC118312A5131E7514"),
            Map.entry("apps/api/src/test/java/com/huarenzaimeng/api/P021OrderDetailApiContractTest.java", "A36EB8EDD25D1E8E7EF7259CC32E98872020F3FE479A89FAB8710AA2A5835CDF"));
    static final LocalSyntheticIdentity ID = LocalSyntheticIdentity.fromToken(TOKEN);

    @Autowired P021OrderDetailService service;
    @Autowired LocalSyntheticOrderRecoveryService recovery;
    @Autowired ObjectMapper json;
    private Fixture activeFixture;

    @Test
    void singleAuthorizedFinalRun() throws Exception {
        Assumptions.assumeTrue("FINAL_RUN".equals(System.getProperty("p021.evidence.confirm")),
                "formal P021 evidence requires explicit single-run authorization properties");
        String runId = require(System.getProperty("p021.evidence.runId"));
        String authorizationRef = require(System.getProperty("p021.evidence.authorizationRef"));
        String authorizationRecordSha = require(System.getProperty("p021.evidence.authorizationRecordSha"));
        Path staging = Path.of(require(System.getProperty("p021.evidence.staging"))).toAbsolutePath().normalize();
        assertThat(Files.isDirectory(staging)).isTrue();
        assertThat(Files.list(staging).findAny()).isEmpty();
        for (CaseSpec spec : cases()) executeAndWrite(spec, runId, authorizationRef, authorizationRecordSha, staging);
        assertThat(Files.list(staging).filter(path -> path.getFileName().toString().endsWith(".json")).count()).isEqualTo(38);
    }

    private void executeAndWrite(CaseSpec spec, String runId, String authorizationRef,
                                 String authorizationRecordSha, Path staging) throws Exception {
        resetAndInstall(base(UserOrderStateCode.AWAITING_PAYMENT, null));
        Map<String, Long> before = service.countsForTest();
        Actual actual = execute(spec);
        Map<String, Long> after = service.countsForTest();
        Map<String, Long> delta = delta(before, after);
        Expected expected = oracle(spec);
        assertMatches(spec, expected, actual, delta);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("Consumable", false); evidence.put("ExecutionStatus", "PASS");
        evidence.put("ScenarioId", spec.scenarioId()); evidence.put("SubcaseId", spec.scenarioId());
        evidence.put("ParameterId", spec.parameterId()); evidence.put("ExecutionRunId", runId);
        evidence.put("ExecutedAt", Instant.now().toString()); evidence.put("D3RegistrySha", D3_REGISTRY_SHA);
        evidence.put("ImplementationAggregateSha", IMPLEMENTATION_SHA); evidence.put("IdentitySetSha", IDENTITY_SHA);
        evidence.put("AuthorizationRecordRef", authorizationRef); evidence.put("AuthorizationRecordSha", authorizationRecordSha);
        Map<String, Object> input = new LinkedHashMap<>(spec.input());
        input.put("FixtureDigest", actual.fixtureDigest()); input.put("CompleteRequest", completeRequest(spec));
        input.put("FixedInputs", FIXED_INPUTS); input.put("ImplementationFiles", IMPLEMENTATION_FILES);
        input.put("AuthorizationRef", authorizationRef);
        evidence.put("Input", input); evidence.put("Expected", expected); evidence.put("Actual", actual);
        evidence.put("CompleteResponse", actual.completeResponse()); evidence.put("Before", before);
        evidence.put("After", after); evidence.put("Delta", delta); evidence.put("QueryCallDelta", delta.get("QueryCall"));
        evidence.put("WriteDelta23", writeDelta23(delta));
        evidence.put("ProcessEvidenceRef", PROCESS_EVIDENCE_REF);
        evidence.put("EvidencePackageRef", "P021-BE-" + runId + "/" + spec.scenarioId() + "/" + spec.parameterId());
        Path output = staging.resolve(spec.scenarioId() + "__" + spec.parameterId() + ".json");
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence) + "\n", StandardCharsets.UTF_8);
    }

    private Actual execute(CaseSpec spec) throws Exception {
        String scenario = spec.scenarioId(); String parameter = spec.parameterId();
        if (scenario.matches("ORD03-P021-00[1-9].*") || scenario.matches("ORD03-P021-01[01].*")) {
            UserOrderStateCode state = stateFor(scenario); String support = supports(state) ? "SUPPORT-P021-1" : null;
            resetAndInstall(base(state, support)); return actual(serviceRead(), null);
        }
        if (scenario.contains("012-")) return existence(parameter);
        if (scenario.contains("013-")) return sessionDrift(parameter);
        if (scenario.contains("014-")) return authorizationDrift(parameter);
        if (scenario.contains("015-")) return timeline(parameter);
        if (scenario.contains("016-")) return strictDto(parameter);
        if (scenario.contains("017-")) return price(parameter);
        if (scenario.contains("018-")) return zeroSideEffect(parameter);
        throw new IllegalArgumentException("unmapped case " + spec);
    }

    private Actual existence(String parameter) {
        if (parameter.equals("P001-UNAUTH")) return actual(service.read("LOCAL_SYNTHETIC", null, null, P021OrderDetailFixtureLoader.ORDER_REF), null);
        if (parameter.equals("P002-CROSS-SUBJECT")) return actual(service.read("LOCAL_SYNTHETIC", "SYN-FOREIGN", ID.sessionRef(), P021OrderDetailFixtureLoader.ORDER_REF), null);
        if (parameter.equals("P003-NOT-FOUND")) return actual(service.read("LOCAL_SYNTHETIC", ID.projectSubjectRef(), ID.sessionRef(), "ORDER-P021-MISSING"), null);
        recovery.revokeBuyerAuthorizationForTest(P021OrderDetailFixtureLoader.AUTHORIZATION_SET_REF);
        return actual(serviceRead(), null);
    }

    private Actual sessionDrift(String parameter) {
        long version = parameter.equals("P001-EXPIRED") ? 0 : 2;
        recovery.installLocalSyntheticBuyerAuthorization(ID.projectSubjectRef(), ID.sessionRef(), version,
                P021OrderDetailFixtureLoader.AUTHORIZATION_SET_REF, P021OrderDetailFixtureLoader.AUTHORIZATION_EVIDENCE_VERSION,
                List.of(P021OrderDetailFixtureLoader.ORDER_REF));
        return actual(serviceRead(), null);
    }

    private Actual authorizationDrift(String parameter) {
        String set = parameter.equals("P001-SET") ? "AUTH-SET-P021-OTHER" : P021OrderDetailFixtureLoader.AUTHORIZATION_SET_REF;
        String evidence = parameter.equals("P002-EVIDENCE") ? "AUTH-EVIDENCE-P021-OTHER" : P021OrderDetailFixtureLoader.AUTHORIZATION_EVIDENCE_VERSION;
        List<String> orders = parameter.equals("P003-ORDERREF") ? List.of() : List.of(P021OrderDetailFixtureLoader.ORDER_REF);
        recovery.installLocalSyntheticBuyerAuthorization(ID.projectSubjectRef(), ID.sessionRef(), 1, set, evidence, orders);
        return actual(serviceRead(), null);
    }

    private Actual timeline(String parameter) {
        Fixture fixture = base(UserOrderStateCode.PAYMENT_PROCESSING, null);
        TimelineItem first = new TimelineItem("TL-1", 1, 1, UserOrderStateCode.AWAITING_PAYMENT,
                Instant.parse("2026-08-03T00:00:00Z"), "WAITING_PAYMENT");
        TimelineItem second = switch (parameter) {
            case "P001-HIGHER" -> new TimelineItem("TL-2", 2, 2, UserOrderStateCode.PAYMENT_PROCESSING, Instant.parse("2026-08-03T00:01:00Z"), "PROCESSING");
            case "P002-LATE-LOWER" -> new TimelineItem("TL-2", 2, 1, UserOrderStateCode.PAYMENT_PROCESSING, Instant.parse("2026-08-03T00:01:00Z"), "LATE");
            case "P003-ROLLBACK" -> new TimelineItem("TL-2", 2, 1, UserOrderStateCode.AWAITING_PAYMENT, Instant.parse("2026-08-03T00:01:00Z"), "ROLLBACK");
            default -> new TimelineItem("TL-1", 2, 2, UserOrderStateCode.PAYMENT_PROCESSING, Instant.parse("2026-08-03T00:01:00Z"), "CONFLICT");
        };
        Projection p = fixture.projection();
        Projection changed = new Projection(p.orderRef(), p.aggregateVersion(), 2, UserOrderStateCode.PAYMENT_PROCESSING,
                p.priceSnapshotSummary(), p.confirmedItems(), p.unknownItems(), p.responsibilityCode(), p.updatedAt(),
                p.nextReviewPoint(), List.of(first, second), actions(2, UserOrderStateCode.PAYMENT_PROCESSING, null), null);
        resetAndInstall(rebind(fixture, changed, fixture.priceSnapshotDigest())); return actual(serviceRead(), null);
    }

    private Actual strictDto(String parameter) throws Exception {
        Response response = serviceRead(); ObjectNode tree = (ObjectNode) json.valueToTree(response);
        switch (parameter) {
            case "P001-MISSING" -> tree.remove("projectCode"); case "P002-ADDITIONAL" -> tree.put("extra", "forbidden");
            case "P003-UNKNOWN-ENUM" -> ((ObjectNode) tree.path("currentProjection")).put("stateCode", "UNKNOWN_INTERNAL");
            case "P004-NULL" -> ((ObjectNode) tree.path("currentProjection")).putNull("priceSnapshotSummary");
            case "P005-TYPE" -> tree.set("aggregateVersion", json.createObjectNode().put("bad", 1));
            case "P006-CROSS-FIELD" -> tree.put("resourceRef", "ORDER-P021-OTHER"); default -> throw new IllegalArgumentException(parameter);
        }
        boolean rejected;
        try { rejected = !service.isStrictResponse(json.treeToValue(tree, Response.class)); } catch (Exception error) { rejected = true; }
        return new Actual("STRICT_DTO_REJECTED", null, rejected, tree, activeFixture.fixtureDigest(), defaultRequest());
    }

    private Actual price(String parameter) {
        Fixture fixture = base(UserOrderStateCode.AWAITING_PAYMENT, null);
        if (parameter.equals("P001-SAME")) return actual(serviceRead(), null);
        if (parameter.equals("P002-DIGEST-DRIFT")) resetAndInstall(withPrice(fixture, fixture.projection().priceSnapshotSummary(), "WRONG"));
        else if (parameter.equals("P003-UNMASKED")) {
            PriceSnapshotSummary bad = new PriceSnapshotSummary("PRICE-P021-SYN-1", 125000, "BDT", "DISPLAY-V1", "+8801712345678",
                    "SYN Operator", "SYN Package", "1000 BDT", "BDT", Instant.parse("2026-08-03T01:00:00Z"));
            resetAndInstall(withPrice(fixture, bad, P021OrderDetailService.snapshotDigest(bad)));
        } else {
            Projection p = fixture.projection(); Projection missing = new Projection(p.orderRef(), p.aggregateVersion(), p.projectionVersion(),
                    p.stateCode(), null, p.confirmedItems(), p.unknownItems(), p.responsibilityCode(), p.updatedAt(), p.nextReviewPoint(), p.timeline(), p.allowedActions(), p.supportRef());
            resetAndInstall(rebind(fixture, missing, "INVALID"));
        }
        return actual(serviceRead(), null);
    }

    private Actual zeroSideEffect(String parameter) {
        if (parameter.equals("P001-SUCCESS")) return actual(serviceRead(), null);
        if (parameter.equals("P002-REJECTED")) return actual(service.read("LOCAL_SYNTHETIC", "SYN-FOREIGN", ID.sessionRef(), P021OrderDetailFixtureLoader.ORDER_REF), null);
        Fixture fixture = base(UserOrderStateCode.AWAITING_PAYMENT, null);
        resetAndInstall(new Fixture(fixture.syntheticMarker(), fixture.environment(), fixture.realityEvidenceLevel(), fixture.projectSubjectRef(),
                fixture.sessionRole(), fixture.sessionVersion(), fixture.authorizationSetRef(), fixture.authorizationEvidenceVersion(), fixture.authorizedOrderRefs(),
                fixture.projection(), fixture.priceSnapshotDigest(), fixture.fixtureSchemaVersion(), "INVALID"));
        return actual(serviceRead(), null);
    }

    private Expected oracle(CaseSpec spec) {
        String scenario = spec.scenarioId(); String parameter = spec.parameterId();
        if (scenario.matches("ORD03-P021-00[1-9].*") || scenario.matches("ORD03-P021-01[01].*"))
            return expectedSuccess(stateFor(scenario), normalProjection(stateFor(scenario)));
        if (scenario.contains("016-")) return new Expected("STRICT_DTO_REJECTED", null, true, 1L, zero23(), frozenStrictMutation(parameter));
        if (scenario.contains("015-") && parameter.equals("P001-HIGHER")) return expectedSuccess(UserOrderStateCode.PAYMENT_PROCESSING, higherTimelineProjection());
        if (scenario.contains("017-") && parameter.equals("P001-SAME")) return expectedSuccess(UserOrderStateCode.AWAITING_PAYMENT, normalProjection(UserOrderStateCode.AWAITING_PAYMENT));
        if (scenario.contains("018-") && parameter.equals("P001-SUCCESS")) return expectedSuccess(UserOrderStateCode.AWAITING_PAYMENT, normalProjection(UserOrderStateCode.AWAITING_PAYMENT));
        if (scenario.contains("012-") || scenario.contains("013-") || scenario.contains("014-") || (scenario.contains("018-") && parameter.equals("P002-REJECTED")))
            return expectedRejected("ORDER_DETAIL_NOT_AVAILABLE", "NONE");
        return expectedRejected("ORDER_DETAIL_READ_ERROR", "READ_SAFE");
    }

    private void assertMatches(CaseSpec spec, Expected expected, Actual actual, Map<String, Long> delta) {
        assertThat(actual.projectCode()).as(spec.identity()).isEqualTo(expected.projectCode());
        assertThat(actual.stateCode()).as(spec.identity()).isEqualTo(expected.stateCode());
        assertThat(actual.validatorRejected()).as(spec.identity()).isEqualTo(expected.validatorRejected());
        JsonNode actualTree = json.valueToTree(actual.completeResponse());
        JsonNode expectedTree = json.valueToTree(expected.completeResponse());
        assertThat(actualTree).as(spec.identity()).isEqualTo(expectedTree);
        assertCompleteResponseShape(spec, actual);
        assertThat(delta.get("QueryCall")).as(spec.identity()).isEqualTo(expected.queryCallDelta());
        assertThat(writeDelta23(delta)).as(spec.identity()).isEqualTo(expected.writeDelta23());
        assertThat(delta.get("FileWrite")).isZero(); assertThat(delta.get("QueueWrite")).isZero();
        assertThat(delta.get("NotificationSend")).isZero();
    }

    private Expected expectedSuccess(UserOrderStateCode state, Projection projection) {
        Response response = new Response(null, "ACCEPTED", "ORDER_DETAIL_READ", P021OrderDetailFixtureLoader.ORDER_REF,
                projection.aggregateVersion(), projection, "NONE", null);
        return new Expected("ORDER_DETAIL_READ", state.name(), false, 1L, zero23(), response);
    }

    private Expected expectedRejected(String projectCode, String retryClass) {
        Response response = new Response(null, "REJECTED", projectCode, null, null, null, retryClass, null);
        return new Expected(projectCode, null, false, 1L, zero23(), response);
    }

    private Projection normalProjection(UserOrderStateCode state) {
        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        String support = supports(state) ? "SUPPORT-P021-1" : null;
        PriceSnapshotSummary price = frozenPrice();
        TimelineItem timeline = new TimelineItem("TIMELINE-P021-2", 1, 2, state, now, "ORDER_STATUS_" + state.name());
        return new Projection(P021OrderDetailFixtureLoader.ORDER_REF, 2, 2, state, price,
                List.of("PAYMENT_CONFIRMATION"), List.of("TOPUP_RESULT"),
                supports(state) ? "SUPPORT_REVIEW" : state == UserOrderStateCode.AWAITING_PAYMENT ? "USER_PAYMENT" : "SYSTEM_RECHECK",
                now, null, List.of(timeline), actions(2, state, support), support);
    }

    private Projection higherTimelineProjection() {
        Projection normal = normalProjection(UserOrderStateCode.PAYMENT_PROCESSING);
        TimelineItem first = new TimelineItem("TL-1", 1, 1, UserOrderStateCode.AWAITING_PAYMENT,
                Instant.parse("2026-08-03T00:00:00Z"), "WAITING_PAYMENT");
        TimelineItem second = new TimelineItem("TL-2", 2, 2, UserOrderStateCode.PAYMENT_PROCESSING,
                Instant.parse("2026-08-03T00:01:00Z"), "PROCESSING");
        return new Projection(normal.orderRef(), normal.aggregateVersion(), 2, normal.stateCode(), normal.priceSnapshotSummary(),
                normal.confirmedItems(), normal.unknownItems(), normal.responsibilityCode(), normal.updatedAt(), normal.nextReviewPoint(),
                List.of(first, second), normal.allowedActions(), normal.supportRef());
    }

    private PriceSnapshotSummary frozenPrice() {
        return new PriceSnapshotSummary("PRICE-P021-SYN-1", 125000, "BDT", "DISPLAY-V1", "******1234",
                "SYN Operator", "SYN Package", "1000 BDT", "BDT", Instant.parse("2026-08-03T01:00:00Z"));
    }

    private JsonNode frozenStrictMutation(String parameter) {
        ObjectNode tree = (ObjectNode) json.valueToTree(new Response(null, "ACCEPTED", "ORDER_DETAIL_READ",
                P021OrderDetailFixtureLoader.ORDER_REF, 2L, normalProjection(UserOrderStateCode.AWAITING_PAYMENT), "NONE", null));
        switch (parameter) {
            case "P001-MISSING" -> tree.remove("projectCode"); case "P002-ADDITIONAL" -> tree.put("extra", "forbidden");
            case "P003-UNKNOWN-ENUM" -> ((ObjectNode) tree.path("currentProjection")).put("stateCode", "UNKNOWN_INTERNAL");
            case "P004-NULL" -> ((ObjectNode) tree.path("currentProjection")).putNull("priceSnapshotSummary");
            case "P005-TYPE" -> tree.set("aggregateVersion", json.createObjectNode().put("bad", 1));
            case "P006-CROSS-FIELD" -> tree.put("resourceRef", "ORDER-P021-OTHER"); default -> throw new IllegalArgumentException(parameter);
        }
        return tree;
    }

    private void assertCompleteResponseShape(CaseSpec spec, Actual actual) {
        JsonNode body = json.valueToTree(actual.completeResponse());
        if (actual.validatorRejected()) {
            assertThat(body.isObject()).as(spec.identity()).isTrue();
            return;
        }
        assertThat(fieldNames(body)).as(spec.identity()).containsExactlyInAnyOrder("requestRef", "outcome", "projectCode",
                "resourceRef", "aggregateVersion", "currentProjection", "retryClass", "nextPollAt");
        if (!"ORDER_DETAIL_READ".equals(actual.projectCode())) {
            assertThat(body.path("resourceRef").isNull()).isTrue(); assertThat(body.path("aggregateVersion").isNull()).isTrue();
            assertThat(body.path("currentProjection").isNull()).isTrue(); return;
        }
        JsonNode projection = body.path("currentProjection");
        assertThat(fieldNames(projection)).containsExactlyInAnyOrder("orderRef", "aggregateVersion", "projectionVersion", "stateCode",
                "priceSnapshotSummary", "confirmedItems", "unknownItems", "responsibilityCode", "updatedAt", "nextReviewPoint",
                "timeline", "allowedActions", "supportRef");
        assertThat(fieldNames(projection.path("priceSnapshotSummary"))).containsExactlyInAnyOrder("priceSnapshotRef", "totalMinor", "currency",
                "displayVersion", "maskedTarget", "brandDisplayName", "productDisplayName", "targetValueDisplay", "targetCurrency", "validUntil");
        projection.path("timeline").forEach(item -> assertThat(fieldNames(item)).containsExactlyInAnyOrder("timelineItemRef", "sequence",
                "projectionVersion", "stateCode", "occurredAt", "userMessageCode"));
        projection.path("allowedActions").forEach(action -> assertThat(fieldNames(action)).containsExactlyInAnyOrder("actionCode", "enabled",
                "actionBindingVersion", "supportRef"));
        assertThat(body.path("resourceRef")).isEqualTo(projection.path("orderRef"));
        assertThat(body.path("aggregateVersion")).isEqualTo(projection.path("aggregateVersion"));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>(); node.fieldNames().forEachRemaining(names::add); return names;
    }

    private static Map<String, Object> completeRequest(CaseSpec spec) {
        boolean unauthenticated = spec.scenarioId().contains("012-") && spec.parameterId().equals("P001-UNAUTH");
        String orderRef = spec.scenarioId().contains("012-") && spec.parameterId().equals("P003-NOT-FOUND")
                ? "ORDER-P021-MISSING" : P021OrderDetailFixtureLoader.ORDER_REF;
        String subject = spec.scenarioId().contains("012-") && spec.parameterId().equals("P002-CROSS-SUBJECT")
                ? "SYN-FOREIGN" : ID.projectSubjectRef();
        return Map.of("method", "GET", "path", "/api/v1/orders/" + orderRef, "body", "<absent>", "query", Map.of(),
                "trustedSessionPresent", !unauthenticated, "projectSubjectRef", unauthenticated ? "<absent>" : subject);
    }

    private static Map<String, Object> defaultRequest() {
        return Map.of("method", "GET", "path", "/api/v1/orders/" + P021OrderDetailFixtureLoader.ORDER_REF,
                "body", "<absent>", "query", Map.of(), "trustedSessionPresent", true, "projectSubjectRef", ID.projectSubjectRef());
    }

    static List<CaseSpec> cases() {
        List<CaseSpec> result = new ArrayList<>();
        add(result, "ORD03-P021-001-AWAITING-PAYMENT", "P001-STATE"); add(result, "ORD03-P021-002-PAYMENT-PROCESSING", "P001-STATE");
        add(result, "ORD03-P021-003-PAID-AWAITING-TOPUP", "P001-STATE"); add(result, "ORD03-P021-004-TOPUP-PROCESSING", "P001-STATE");
        add(result, "ORD03-P021-005-TOPUP-UNKNOWN", "P001-SUPPORT", "P002-NO-AUTO"); add(result, "ORD03-P021-006-DELIVERED", "P001-STATE");
        add(result, "ORD03-P021-007-CONFIRMED-NOT-DELIVERED", "P001-SUPPORT"); add(result, "ORD03-P021-008-REFUND-PROCESSING", "P001-STATE");
        add(result, "ORD03-P021-009-REFUNDED", "P001-STATE"); add(result, "ORD03-P021-010-DELIVERY-REFUND-CONFLICT", "P001-SUPPORT");
        add(result, "ORD03-P021-011-SUPPORT-REVIEW", "P001-SUPPORT");
        add(result, "ORD03-P021-012-EXISTENCE-SAME-SHAPE", "P001-UNAUTH", "P002-CROSS-SUBJECT", "P003-NOT-FOUND", "P004-REVOKED");
        add(result, "ORD03-P021-013-SESSION-VERSION-DRIFT", "P001-EXPIRED", "P002-MISMATCH");
        add(result, "ORD03-P021-014-AUTHORIZATION-SET-DRIFT", "P001-SET", "P002-EVIDENCE", "P003-ORDERREF");
        add(result, "ORD03-P021-015-MONOTONIC-TIMELINE", "P001-HIGHER", "P002-LATE-LOWER", "P003-ROLLBACK", "P004-CONFLICT");
        add(result, "ORD03-P021-016-STRICT-DTO", "P001-MISSING", "P002-ADDITIONAL", "P003-UNKNOWN-ENUM", "P004-NULL", "P005-TYPE", "P006-CROSS-FIELD");
        add(result, "ORD03-P021-017-PRICE-SNAPSHOT-IDENTITY", "P001-SAME", "P002-DIGEST-DRIFT", "P003-UNMASKED", "P004-MISSING");
        add(result, "ORD03-P021-018-READ-ZERO-SIDE-EFFECT", "P001-SUCCESS", "P002-REJECTED", "P003-ERROR");
        return List.copyOf(result);
    }

    private static void add(List<CaseSpec> result, String scenario, String... parameters) {
        for (String parameter : parameters) result.add(new CaseSpec(scenario, parameter, Map.of("LocalSynthetic", true, "Parameter", parameter)));
    }
    private static UserOrderStateCode stateFor(String scenario) {
        if (scenario.contains("001-")) return UserOrderStateCode.AWAITING_PAYMENT; if (scenario.contains("002-")) return UserOrderStateCode.PAYMENT_PROCESSING;
        if (scenario.contains("003-")) return UserOrderStateCode.PAID_AWAITING_TOPUP; if (scenario.contains("004-")) return UserOrderStateCode.TOPUP_PROCESSING;
        if (scenario.contains("005-")) return UserOrderStateCode.TOPUP_RESULT_UNKNOWN; if (scenario.contains("006-")) return UserOrderStateCode.DELIVERED;
        if (scenario.contains("007-")) return UserOrderStateCode.CONFIRMED_NOT_DELIVERED; if (scenario.contains("008-")) return UserOrderStateCode.REFUND_PROCESSING;
        if (scenario.contains("009-")) return UserOrderStateCode.REFUNDED; if (scenario.contains("010-")) return UserOrderStateCode.DELIVERY_REFUND_CONFLICT_REVIEW;
        return UserOrderStateCode.SUPPORT_REVIEW;
    }
    private Response serviceRead() { return service.read("LOCAL_SYNTHETIC", ID.projectSubjectRef(), ID.sessionRef(), P021OrderDetailFixtureLoader.ORDER_REF); }
    private Actual actual(Response response, Boolean rejected) { return new Actual(response.projectCode(), response.currentProjection() == null ? null : response.currentProjection().stateCode().name(), Boolean.TRUE.equals(rejected), response, activeFixture.fixtureDigest(), defaultRequest()); }
    private void resetAndInstall(Fixture fixture) { recovery.resetForTest(); service.resetForTest(); activeFixture=fixture; recovery.installLocalSyntheticBuyerAuthorization(ID.projectSubjectRef(), ID.sessionRef(), 1,
            P021OrderDetailFixtureLoader.AUTHORIZATION_SET_REF, P021OrderDetailFixtureLoader.AUTHORIZATION_EVIDENCE_VERSION, List.of(P021OrderDetailFixtureLoader.ORDER_REF)); service.installLocalSyntheticFixture(fixture); }
    private static Fixture base(UserOrderStateCode state, String support) { return P021OrderDetailFixtureLoader.fixture(ID, state, 2, 2, support, List.of("PAYMENT_CONFIRMATION"), List.of("TOPUP_RESULT")); }
    private static Fixture rebind(Fixture f, Projection p, String priceDigest) { Fixture draft = new Fixture(f.syntheticMarker(), f.environment(), f.realityEvidenceLevel(), f.projectSubjectRef(), f.sessionRole(), f.sessionVersion(), f.authorizationSetRef(), f.authorizationEvidenceVersion(), f.authorizedOrderRefs(), p, priceDigest, f.fixtureSchemaVersion(), null); return new Fixture(draft.syntheticMarker(), draft.environment(), draft.realityEvidenceLevel(), draft.projectSubjectRef(), draft.sessionRole(), draft.sessionVersion(), draft.authorizationSetRef(), draft.authorizationEvidenceVersion(), draft.authorizedOrderRefs(), draft.projection(), draft.priceSnapshotDigest(), draft.fixtureSchemaVersion(), P021OrderDetailService.fixtureDigest(draft)); }
    private static Fixture withPrice(Fixture f, PriceSnapshotSummary price, String digest) { Projection p=f.projection(); return rebind(f,new Projection(p.orderRef(),p.aggregateVersion(),p.projectionVersion(),p.stateCode(),price,p.confirmedItems(),p.unknownItems(),p.responsibilityCode(),p.updatedAt(),p.nextReviewPoint(),p.timeline(),p.allowedActions(),p.supportRef()),digest); }
    private static List<AllowedAction> actions(long version, UserOrderStateCode state, String support) { List<AllowedAction> a=new ArrayList<>(); a.add(new AllowedAction("REFRESH_ORDER_DETAIL",true,version,null)); if(supports(state)) a.add(new AllowedAction("OPEN_SUPPORT",true,version,support)); a.add(new AllowedAction("SAFE_BACK",true,version,null)); return List.copyOf(a); }
    private static boolean supports(UserOrderStateCode state) { return state==UserOrderStateCode.TOPUP_RESULT_UNKNOWN||state==UserOrderStateCode.CONFIRMED_NOT_DELIVERED||state==UserOrderStateCode.DELIVERY_REFUND_CONFLICT_REVIEW||state==UserOrderStateCode.SUPPORT_REVIEW; }
    private static Map<String,Long> delta(Map<String,Long>b,Map<String,Long>a){Map<String,Long>r=new LinkedHashMap<>();a.forEach((k,v)->r.put(k,v-b.getOrDefault(k,0L)));return Map.copyOf(r);}
    private static Map<String,Long> writeDelta23(Map<String,Long>d){Map<String,Long>r=new LinkedHashMap<>(); for(P021OrderDetailSideEffectProbe.Counter c:P021OrderDetailSideEffectProbe.Counter.values()){if(c==P021OrderDetailSideEffectProbe.Counter.QueryCall||c==P021OrderDetailSideEffectProbe.Counter.FileWrite||c==P021OrderDetailSideEffectProbe.Counter.QueueWrite||c==P021OrderDetailSideEffectProbe.Counter.NotificationSend)continue;r.put(c.name(),d.get(c.name()));}return Map.copyOf(r);}
    private static Map<String,Long> zero23(){Map<String,Long>r=new LinkedHashMap<>();for(P021OrderDetailSideEffectProbe.Counter c:P021OrderDetailSideEffectProbe.Counter.values()){if(c==P021OrderDetailSideEffectProbe.Counter.QueryCall||c==P021OrderDetailSideEffectProbe.Counter.FileWrite||c==P021OrderDetailSideEffectProbe.Counter.QueueWrite||c==P021OrderDetailSideEffectProbe.Counter.NotificationSend)continue;r.put(c.name(),0L);}return Map.copyOf(r);}
    private static String require(String value){if(value==null||value.isBlank())throw new IllegalStateException("missing final-run property");return value;}

    record CaseSpec(String scenarioId,String parameterId,Map<String,Object> input){String identity(){return scenarioId+"|"+scenarioId+"|"+parameterId;}}
    record Expected(String projectCode,String stateCode,boolean validatorRejected,long queryCallDelta,
                    Map<String,Long> writeDelta23,Object completeResponse){}
    record Actual(String projectCode,String stateCode,boolean validatorRejected,Object completeResponse,
                  String fixtureDigest,Map<String,Object> completeRequest){}
}
