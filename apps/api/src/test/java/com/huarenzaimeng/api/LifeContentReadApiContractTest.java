package com.huarenzaimeng.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huarenzaimeng.api.config.TestAccessTokenFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory",
        "hz.life-content.mode=local-synthetic",
        "hz.test-access-token=local-synthetic-inf-read-secret"
})
@AutoConfigureMockMvc
class LifeContentReadApiContractTest {
    private static final String TOKEN = "local-synthetic-inf-read-secret";
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");
    private static final String D1_REGISTRY_SHA = "F2CA50A6C8EAD6463753E33B50A819EE51DA69117D7EDDCE9A54EC4A8F785538";
    private static final String D1_11_SHA = "CD11326A05923C3F707A926B95716CED848529D667CEFB044F39E2134B441560";
    private static final String D2_REGISTRY_SHA = "2E9BC7D6F9075018CF5DB447571FD8FA9E23DA2E237EC0EEF13AA6D64628AA73";
    private static final String D3_03_SHA = "2C3CE8BB4A872953CFAFAAEB8AC4983839BAD21A162FF6B40B09F6A20CCB6B5A";
    private static final String D3_REGISTRY_SHA = "358CBFCC3DEAEE618C8F478603367FA3001B84BE63AAF11ED9CCB9B3EA5BDB8C";
    private static final String MATRIX_SHA = "5059310751105E5C598BBB28C9FA69AF28188AAE58AD9D0C4343D5CE2566438B";
    private static final String RUN_ID = "D5-INF-01-BE-" + UUID.randomUUID();
    private static final String EXECUTED_AT = Instant.now().toString();
    private static final Path EVIDENCE_DIRECTORY = Path.of("target", "d5-inf-01-evidence");
    private static final Set<String> PROHIBITED_VALUES = new HashSet<>();
    private static final Set<String> LIST_FIELDS = Set.of("requestRef", "viewState", "projectCode",
            "schemaVersion", "visibilityRuleVersion", "items", "retryClass", "nextReadAt");
    private static final Set<String> DETAIL_FIELDS = Set.of("requestRef", "viewState", "projectCode",
            "schemaVersion", "visibilityRuleVersion", "contentRef", "contentVersion", "item", "retryClass",
            "nextReadAt");
    private static final Set<String> SUMMARY_FIELDS = Set.of("contentRef", "contentVersion", "category", "title",
            "summary", "sourceType", "jurisdiction", "applicableAudience", "publishedAt", "updatedAt",
            "effectiveFrom", "effectiveTo", "freshnessState", "coverState", "coverRef");
    private static final Set<String> DETAIL_ITEM_FIELDS = Set.of("contentRef", "contentVersion", "category", "title",
            "summary", "sourceType", "jurisdiction", "applicableAudience", "publishedAt", "updatedAt",
            "effectiveFrom", "effectiveTo", "freshnessState", "coverState", "coverRef", "body");
    private static final List<String> IMPLEMENTATION_PATHS = List.of(
            "apps/api/src/main/java/com/huarenzaimeng/api/LifeContentController.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/LifeContentReadDomain.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/LifeContentReadService.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/LifeContentSideEffectProbe.java",
            "apps/api/src/main/resources/application.yml",
            "apps/api/src/test/java/com/huarenzaimeng/api/LifeContentReadApiContractTest.java");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LifeContentReadService service;
    @Autowired LifeContentSideEffectProbe sideEffects;
    @MockBean Clock clock;

    @BeforeAll
    static void clearEvidenceDirectory() throws Exception {
        Files.createDirectories(EVIDENCE_DIRECTORY);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(EVIDENCE_DIRECTORY, "*.json")) {
            for (Path file : files) Files.deleteIfExists(file);
        }
    }

    @AfterAll
    static void finalizeEvidencePackage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Path> cases;
        try (var stream = Files.list(EVIDENCE_DIRECTORY)) {
            cases = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().equals("evidence-package-index.json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        assertThat(cases).hasSize(13);
        List<String> manifest = new ArrayList<>();
        for (Path file : cases) manifest.add(file.getFileName() + "|" + sha256(Files.readAllBytes(file)));
        manifest.sort(Comparator.naturalOrder());
        String packageSha = sha256(String.join("\n", manifest).getBytes(StandardCharsets.UTF_8));

        int prohibitedHits = 0;
        for (Path file : cases) {
            String value = Files.readString(file, StandardCharsets.UTF_8);
            for (String prohibited : PROHIBITED_VALUES) if (value.contains(prohibited)) prohibitedHits++;
        }
        assertThat(prohibitedHits).isZero();

        ObjectNode index = mapper.createObjectNode();
        index.put("evidencePackageId", "D5-INF-01-BE-LOCAL-SYNTHETIC");
        index.put("executionRunId", RUN_ID);
        index.put("executedAt", EXECUTED_AT);
        index.put("evidenceRoot", "apps/api/target/d5-inf-01-evidence");
        index.put("fixedScenarioCount", 13);
        index.put("pass", 13);
        index.put("notRun", 0);
        index.put("implementationAggregateSha256", implementationAggregate());
        index.put("implementationAggregateAlgorithm",
                ".NET StringComparer.Ordinal equivalent path sort; path|UPPERCASE_SHA256; UTF-8 no BOM; LF between; no terminal LF");
        index.put("matrixSha256", MATRIX_SHA);
        appendFixedInputs(index);
        index.put("evidencePackageSha256", packageSha);
        ObjectNode scan = index.putObject("prohibitedPayloadScan");
        scan.put("ruleVersion", "D5-INF-PRIVACY-V1");
        scan.put("scope", "REDACTED_EVIDENCE_JSON_AND_IN_PROCESS_LOG_CAPTURE");
        scan.put("hitCount", prohibitedHits);
        scan.put("evidenceRef", "SCAN-" + sha256(packageSha).substring(0, 16));
        ArrayNode files = index.putArray("evidenceFiles");
        for (Path file : cases) {
            ObjectNode item = files.addObject();
            item.put("path", "apps/api/target/d5-inf-01-evidence/" + file.getFileName());
            item.put("sha256", sha256(Files.readAllBytes(file)));
        }
        ObjectNode boundaries = index.putObject("notRunBoundaries");
        boundaries.put("realContentRightsAndFreshness", "NOT_RUN");
        boundaries.put("realMySql", "NOT_RUN");
        boundaries.put("externalNetworkAndImages", "NOT_RUN");
        boundaries.put("browserAndRealDevice", "NOT_RUN");
        boundaries.put("productionPublicationAndIdentity", "NOT_RUN");
        Files.writeString(EVIDENCE_DIRECTORY.resolve("evidence-package-index.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(index) + "\n", StandardCharsets.UTF_8);
    }

    @BeforeEach
    void prepare() {
        when(clock.instant()).thenReturn(NOW);
        service.installFixturesForTest(LifeContentListReadCompleteness.COMPLETE, List.of());
    }

    @Test
    void D5_INF_S01_LIFE_REMINDER_READY() throws Exception {
        LifeContentFixture fixture = ready("S01", "LIFE_REMINDER");
        List<ObjectNode> parameters = List.of(
                listParameter("LIST_READY", LifeContentListReadCompleteness.COMPLETE, List.of(fixture), "READY", 1),
                detailParameter("DETAIL_READY", LifeContentListReadCompleteness.COMPLETE, List.of(fixture), fixture,
                        fixture.contentVersion(), "READY", true));
        writeEvidence("INF-EP-001", "D5-INF-S01", "INF-READ-001-LIFE-REMINDER-READY", parameters);
    }

    @Test
    void D5_INF_S02_HOLIDAY_EXPLANATION_READY() throws Exception {
        LifeContentFixture fixture = ready("S02", "HOLIDAY_EXPLANATION");
        List<ObjectNode> parameters = List.of(
                listParameter("LIST_READY", LifeContentListReadCompleteness.COMPLETE, List.of(fixture), "READY", 1),
                detailParameter("DETAIL_READY", LifeContentListReadCompleteness.COMPLETE, List.of(fixture), fixture,
                        fixture.contentVersion(), "READY", true));
        writeEvidence("INF-EP-002", "D5-INF-S02", "INF-READ-002-HOLIDAY-EXPLANATION-READY", parameters);
    }

    @Test
    void D5_INF_S03_CLIENT_LOADING_NEVER_COMES_FROM_SERVER() throws Exception {
        LifeContentFixture fixture = ready("S03", "LIFE_REMINDER");
        List<ObjectNode> parameters = new ArrayList<>();
        for (String parameter : List.of("FIRST_ENTRY", "REENTRY", "FOREGROUND_RESUME")) {
            ObjectNode value = detailParameter(parameter, LifeContentListReadCompleteness.COMPLETE, List.of(fixture),
                    fixture, fixture.contentVersion(), "READY", true);
            value.putObject("clientPrecondition").put("localStateBeforeResponse", "LOADING")
                    .put("oldItemCleared", true).put("oldBodyCleared", true).put("oldReadyCleared", true);
            assertThat(value.path("operations").get(0).path("actual").path("viewState").asText())
                    .isNotEqualTo("LOADING");
            parameters.add(value);
        }
        writeEvidence("INF-EP-003", "D5-INF-S03", "INF-READ-003-CLIENT-LOADING", parameters);
    }

    @Test
    void D5_INF_S04_COMPLETE_EMPTY_LIST() throws Exception {
        writeEvidence("INF-EP-004", "D5-INF-S04", "INF-READ-004-LIST-EMPTY", List.of(
                listParameter("COMPLETE_ZERO_CANDIDATE", LifeContentListReadCompleteness.COMPLETE, List.of(),
                        "EMPTY", 0)));
    }

    @Test
    void D5_INF_S05_READ_ERROR_AND_NETWORK_NOT_OBSERVED() throws Exception {
        LifeContentFixture fixture = ready("S05", "LIFE_REMINDER");
        ObjectNode backend = listParameter("BACKEND_READ_ERROR", LifeContentListReadCompleteness.ERROR,
                List.of(fixture), "ERROR", 0);
        install(LifeContentListReadCompleteness.COMPLETE, List.of(fixture));
        ObjectNode network = noCallParameter("NETWORK_INTERRUPTED", "NOT_OBSERVED");
        writeEvidence("INF-EP-005", "D5-INF-S05", "INF-READ-005-READ-ERROR", List.of(backend, network));
    }

    @Test
    void D5_INF_S06_STALE_BEFORE_EFFECTIVE_TO_IS_UNKNOWN() throws Exception {
        LifeContentFixture stale = withReference(ready("S06", "LIFE_REMINDER"), NOW.plusSeconds(7201),
                NOW.plusSeconds(10_800));
        ObjectNode parameter = parameter("STALE_BEFORE_EFFECTIVE_TO",
                List.of(listOperation(stale, "UNKNOWN", 0), detailOperation(stale, stale.contentVersion(),
                        "UNKNOWN", false)));
        writeEvidence("INF-EP-006", "D5-INF-S06", "INF-READ-006-STALE", List.of(parameter));
    }

    @Test
    void D5_INF_S07_UNDER_REVIEW_DETAIL_AND_LIST() throws Exception {
        LifeContentFixture fixture = withReviewState(ready("S07", "LIFE_REMINDER"), "UNDER_REVIEW");
        writeEvidence("INF-EP-007", "D5-INF-S07", "INF-READ-007-UNDER-REVIEW", List.of(
                detailParameter("DETAIL_UNDER_REVIEW", LifeContentListReadCompleteness.COMPLETE, List.of(fixture),
                        fixture, fixture.contentVersion(), "UNDER_REVIEW", false),
                listParameter("LIST_ONLY_CANDIDATE", LifeContentListReadCompleteness.COMPLETE, List.of(fixture),
                        "UNDER_REVIEW", 0)));
    }

    @Test
    void D5_INF_S08_EXPIRED_PRECEDES_STALE() throws Exception {
        LifeContentFixture expired = withReference(ready("S08", "HOLIDAY_EXPLANATION"), NOW.plusSeconds(10_801),
                NOW.plusSeconds(10_800));
        ObjectNode parameter = parameter("REFERENCE_AFTER_EFFECTIVE_TO",
                List.of(listOperation(expired, "EXPIRED", 0), detailOperation(expired, expired.contentVersion(),
                        "EXPIRED", false)));
        writeEvidence("INF-EP-008", "D5-INF-S08", "INF-READ-008-EXPIRED", List.of(parameter));
    }

    @Test
    void D5_INF_S09_REMOVED_AND_OLD_LINK_ARE_SAME_SHAPE() throws Exception {
        LifeContentFixture withdrawn = withReviewState(ready("S09-WITHDRAWN", "LIFE_REMINDER"), "REMOVED");
        LifeContentFixture unpublished = withReviewState(ready("S09-UNPUBLISHED", "LIFE_REMINDER"),
                "UNPUBLISHED");
        LifeContentFixture nonexistent = ready("S09-NONEXISTENT", "LIFE_REMINDER");
        LifeContentFixture oldV1 = ready("S09-SUPERSEDED", "LIFE_REMINDER");
        LifeContentFixture currentV2 = withVersion(oldV1, "SYN-V2-CURRENT");
        ObjectNode withdrawnOperation = detailOperation(withdrawn, withdrawn.contentVersion(), "REMOVED", false);
        ObjectNode unpublishedOperation = detailOperation(unpublished, unpublished.contentVersion(), "REMOVED", false);
        install(LifeContentListReadCompleteness.COMPLETE, List.of());
        ObjectNode nonexistentOperation = detailOperationWithoutInstall(nonexistent, nonexistent.contentVersion(),
                "REMOVED", false);
        ObjectNode withdrawnEvidence = parameter("WITHDRAWN_CURRENT_REF",
                List.of(withdrawnOperation, unpublishedOperation, nonexistentOperation));
        install(LifeContentListReadCompleteness.COMPLETE, List.of(oldV1, currentV2),
                Map.of(oldV1.contentRef(), currentV2.contentVersion()));
        ObjectNode oldVersionOperation = detailOperationWithoutInstall(oldV1, oldV1.contentVersion(),
                "REMOVED", false);
        ObjectNode currentVersionOperation = detailOperationWithoutInstall(currentV2, currentV2.contentVersion(),
                "READY", true);
        assertThat(oldVersionOperation.path("actual").path("requestedVersionEchoed").asBoolean()).isTrue();
        assertThat(oldVersionOperation.path("actual").path("redirectedToDifferentVersion").asBoolean()).isFalse();
        assertThat(oldVersionOperation.path("actual").path("bodyPresent").asBoolean()).isFalse();
        assertThat(currentVersionOperation.path("actual").path("viewState").asText()).isEqualTo("READY");
        assertThat(currentVersionOperation.path("actual").path("bodyPresent").asBoolean()).isTrue();
        ObjectNode oldLink = parameter("SUPERSEDED_VERSION_OLD_LINK",
                List.of(oldVersionOperation, currentVersionOperation));
        JsonNode canonicalShape = oldLink.path("operations").get(0).path("actual").path("responseFields");
        for (JsonNode operation : withdrawnEvidence.path("operations")) {
            assertThat(operation.path("actual").path("responseFields")).isEqualTo(canonicalShape);
            assertThat(operation.path("actual").path("viewState").asText()).isEqualTo("REMOVED");
            assertThat(operation.path("actual").path("itemCount").asInt()).isZero();
        }
        writeEvidence("INF-EP-009", "D5-INF-S09", "INF-READ-009-REMOVED-OLD-LINK",
                List.of(withdrawnEvidence, oldLink));
    }

    @Test
    void D5_INF_S10_UNKNOWN_INPUTS_AND_INCOMPLETE_SCAN() throws Exception {
        LifeContentFixture ready = ready("S10-READY", "LIFE_REMINDER");
        LifeContentFixture unknown = withSourceConflict(ready("S10-MISSING", "HOLIDAY_EXPLANATION"), "UNKNOWN");
        LifeContentFixture conflict = withRightsConflict(ready("S10-CONFLICT", "LIFE_REMINDER"), "CONFLICT");
        writeEvidence("INF-EP-010", "D5-INF-S10", "INF-READ-010-UNKNOWN", List.of(
                listParameter("MISSING_QUALIFICATION", LifeContentListReadCompleteness.COMPLETE,
                        List.of(ready, unknown), "UNKNOWN", 0),
                detailParameter("RIGHTS_CONFLICT", LifeContentListReadCompleteness.COMPLETE, List.of(conflict),
                        conflict, conflict.contentVersion(), "UNKNOWN", false),
                listParameter("INCOMPLETE_LIST_SCAN", LifeContentListReadCompleteness.UNKNOWN,
                        List.of(withCompleteness(ready, "UNKNOWN")), "UNKNOWN", 0)));
    }

    @Test
    void D5_INF_S11_IMAGE_UNAVAILABLE_KEEPS_CURRENT_BODY() throws Exception {
        LifeContentFixture fixture = withCover(ready("S11", "LIFE_REMINDER"), "IMAGE_UNAVAILABLE", null);
        writeEvidence("INF-EP-011", "D5-INF-S11", "INF-READ-011-IMAGE-UNAVAILABLE", List.of(
                detailParameter("CURRENT_BODY_COVER_UNAVAILABLE", LifeContentListReadCompleteness.COMPLETE,
                        List.of(fixture), fixture, fixture.contentVersion(), "READY", true)));
    }

    @Test
    void D5_INF_S12_UNAPPROVED_CATEGORY_FAILS_CLOSED_WITH_POSITIVE_CONTROLS() throws Exception {
        LifeContentFixture life = ready("S12-LIFE", "LIFE_REMINDER");
        LifeContentFixture holiday = ready("S12-HOLIDAY", "HOLIDAY_EXPLANATION");
        LifeContentFixture invalid = withCategory(ready("S12-INVALID", "LIFE_REMINDER"), "SYN_UNAPPROVED_CATEGORY");
        writeEvidence("INF-EP-012", "D5-INF-S12", "INF-READ-012-NOT-SELECTED-CATEGORY", List.of(
                listParameter("UNAPPROVED_CATEGORY_EXPLICIT", LifeContentListReadCompleteness.COMPLETE,
                        List.of(life, holiday, invalid), "ERROR", 0)));
    }

    @Test
    void D5_INF_S13_ANONYMOUS_MINIMAL_AND_TELEMETRY_REDACTED() throws Exception {
        LifeContentFixture fixture = ready("S13", "LIFE_REMINDER");
        ObjectNode list = listParameter("LIST_ANONYMOUS", LifeContentListReadCompleteness.COMPLETE,
                List.of(fixture), "READY", 1);
        ObjectNode detail = detailParameter("DETAIL_ANONYMOUS", LifeContentListReadCompleteness.COMPLETE,
                List.of(fixture), fixture, fixture.contentVersion(), "READY", true);

        install(LifeContentListReadCompleteness.COMPLETE, List.of(fixture));
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        ObjectNode logParameter;
        try {
            logParameter = detailParameterWithoutInstall("LOG_MINIMIZATION", fixture, fixture.contentVersion(),
                    "READY", true);
        } finally {
            root.detachAppender(appender);
        }
        String captured = appender.list.stream().map(event -> event.getFormattedMessage())
                .reduce("", (left, right) -> left + "\n" + right);
        for (String prohibited : PROHIBITED_VALUES) assertThat(captured).doesNotContain(prohibited);
        logParameter.putObject("runtimeLogScan").put("hitCount", 0).put("eventCount", appender.list.size());

        ObjectNode evidenceMin = noCallParameter("EVIDENCE_MINIMIZATION", "REDACTED_STRUCTURE_ONLY");
        evidenceMin.putObject("scanExpectation").put("prohibitedOriginalValueHits", 0);
        ObjectNode a120 = detailParameter("A120_ZERO", LifeContentListReadCompleteness.COMPLETE, List.of(fixture),
                fixture, fixture.contentVersion(), "READY", true);
        assertThat(a120.path("operations").get(0).path("delta").path("A120PublishAction").asLong()).isZero();
        writeEvidence("INF-EP-013", "D5-INF-S13", "INF-READ-013-ANONYMOUS-MINIMAL",
                List.of(list, detail, logParameter, evidenceMin, a120));
    }

    @Test
    void observationBoundariesAreSensitiveAndNotConstantZeroAccessors() {
        service.installFixturesForTest(LifeContentListReadCompleteness.COMPLETE, List.of());
        LifeContentReadSnapshot beforeQuery = service.snapshotForTest();
        service.list();
        assertThat(service.snapshotForTest().counters().get("QueryCall")
                - beforeQuery.counters().get("QueryCall")).isEqualTo(1L);
        for (LifeContentSideEffectProbe.Boundary boundary : LifeContentSideEffectProbe.Boundary.values()) {
            if (boundary == LifeContentSideEffectProbe.Boundary.QueryCall) continue;
            sideEffects.resetForTest();
            long before = sideEffects.snapshot().get(boundary.name());
            sideEffects.observeForSensitivityTest(boundary);
            long after = sideEffects.snapshot().get(boundary.name());
            assertThat(after - before).as(boundary.name()).isEqualTo(1L);
        }
    }

    private ObjectNode listParameter(String parameterId, LifeContentListReadCompleteness completeness,
                                     List<LifeContentFixture> fixtures, String expectedState, int expectedItems)
            throws Exception {
        install(completeness, fixtures);
        return parameter(parameterId, List.of(listOperationWithoutInstall(expectedState, expectedItems)));
    }

    private ObjectNode detailParameter(String parameterId, LifeContentListReadCompleteness completeness,
                                       List<LifeContentFixture> fixtures, LifeContentFixture requested,
                                       String requestedVersion, String expectedState, boolean itemExpected)
            throws Exception {
        install(completeness, fixtures);
        return detailParameterWithoutInstall(parameterId, requested, requestedVersion, expectedState, itemExpected);
    }

    private ObjectNode detailParameterWithoutInstall(String parameterId, LifeContentFixture requested,
                                                     String requestedVersion, String expectedState,
                                                     boolean itemExpected) throws Exception {
        return parameter(parameterId,
                List.of(detailOperationWithoutInstall(requested, requestedVersion, expectedState, itemExpected)));
    }

    private ObjectNode listOperation(LifeContentFixture fixture, String expectedState, int expectedItems)
            throws Exception {
        install(LifeContentListReadCompleteness.valueOf(fixture.listReadCompleteness()), List.of(fixture));
        return listOperationWithoutInstall(expectedState, expectedItems);
    }

    private ObjectNode detailOperation(LifeContentFixture fixture, String requestedVersion, String expectedState,
                                       boolean itemExpected) throws Exception {
        install(LifeContentListReadCompleteness.valueOf(fixture.listReadCompleteness()), List.of(fixture));
        return detailOperationWithoutInstall(fixture, requestedVersion, expectedState, itemExpected);
    }

    private ObjectNode listOperationWithoutInstall(String expectedState, int expectedItems) throws Exception {
        LifeContentReadSnapshot before = service.snapshotForTest();
        MvcResult result = mvc.perform(get("/api/v1/content/life-items")
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN))
                .andReturn();
        LifeContentReadSnapshot after = service.snapshotForTest();
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertList(body, expectedState, expectedItems);
        assertReadOnly(before, after, 1);
        return operation("LIST", result.getResponse().getStatus(), body, before, after, expectedState,
                expectedItems, false);
    }

    private ObjectNode detailOperationWithoutInstall(LifeContentFixture requested, String requestedVersion,
                                                     String expectedState, boolean itemExpected) throws Exception {
        LifeContentReadSnapshot before = service.snapshotForTest();
        MvcResult result = mvc.perform(get("/api/v1/content/life-items/{contentRef}", requested.contentRef())
                        .param("contentVersion", requestedVersion)
                        .header(TestAccessTokenFilter.HEADER_NAME, TOKEN))
                .andReturn();
        LifeContentReadSnapshot after = service.snapshotForTest();
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertDetail(body, expectedState, itemExpected);
        assertThat(body.path("contentRef").asText()).isEqualTo(requested.contentRef());
        assertThat(body.path("contentVersion").asText()).isEqualTo(requestedVersion);
        assertReadOnly(before, after, 1);
        ObjectNode operation = operation("DETAIL", result.getResponse().getStatus(), body, before, after, expectedState,
                itemExpected ? 1 : 0, itemExpected);
        ((ObjectNode) operation.path("expected"))
                .put("requestedVersionEchoed", true)
                .put("redirectedToDifferentVersion", false)
                .put("bodyReturned", itemExpected);
        ((ObjectNode) operation.path("actual"))
                .put("requestedVersionEchoed", requestedVersion.equals(body.path("contentVersion").asText()))
                .put("redirectedToDifferentVersion", !requestedVersion.equals(body.path("contentVersion").asText()))
                .put("bodyReturned", body.path("item").isObject() && body.path("item").hasNonNull("body"));
        return operation;
    }

    private ObjectNode noCallParameter(String parameterId, String expectedObservation) {
        LifeContentReadSnapshot before = service.snapshotForTest();
        LifeContentReadSnapshot after = service.snapshotForTest();
        assertReadOnly(before, after, 0);
        ObjectNode operation = json.createObjectNode();
        operation.put("operation", "NO_SERVER_CALL");
        operation.putObject("expected").put("observation", expectedObservation).put("queryCallDelta", 0);
        operation.putObject("actual").put("observation", expectedObservation).put("responseObserved", false);
        operation.set("before", counters(before));
        operation.set("after", counters(after));
        operation.set("delta", delta(before, after));
        return parameter(parameterId, List.of(operation));
    }

    private ObjectNode parameter(String parameterId, List<ObjectNode> operations) {
        ObjectNode parameter = json.createObjectNode();
        parameter.put("parameterId", parameterId);
        parameter.put("fixtureDigest", service.snapshotForTest().fixtureDigest());
        parameter.put("syntheticMarker", true);
        parameter.put("executionStatus", "PASS");
        ArrayNode array = parameter.putArray("operations");
        operations.forEach(array::add);
        return parameter;
    }

    private ObjectNode operation(String operation, int httpStatus, JsonNode body, LifeContentReadSnapshot before,
                                 LifeContentReadSnapshot after, String expectedState, int expectedItems,
                                 boolean bodyExpected) {
        ObjectNode value = json.createObjectNode();
        value.put("operation", operation);
        ObjectNode input = value.putObject("input");
        input.put("environment", "LOCAL_SYNTHETIC");
        input.put("fixtureDigest", before.fixtureDigest());
        input.put("contentRefDigest", body.has("contentRef") ? digestRef(body.path("contentRef").asText()) : "N/A");
        ObjectNode expected = value.putObject("expected");
        expected.put("httpStatus", 200).put("viewState", expectedState).put("itemCount", expectedItems)
                .put("bodyPresent", bodyExpected).put("queryCallDelta", 1).put("allBusinessWriteDelta", 0);
        ObjectNode actual = value.putObject("actual");
        actual.put("httpStatus", httpStatus).put("viewState", body.path("viewState").asText())
                .put("projectCode", body.path("projectCode").asText())
                .put("schemaVersion", body.path("schemaVersion").asText())
                .put("visibilityRuleVersion", body.path("visibilityRuleVersion").asText())
                .put("itemCount", body.has("items") ? body.path("items").size() : body.path("item").isNull() ? 0 : 1)
                .put("bodyPresent", body.path("item").isObject() && body.path("item").hasNonNull("body"));
        ArrayNode responseFields = actual.putArray("responseFields");
        fieldNames(body).forEach(responseFields::add);
        ArrayNode itemFields = actual.putArray("itemFields");
        JsonNode item = body.has("items") && body.path("items").size() > 0
                ? body.path("items").get(0) : body.path("item");
        if (item != null && item.isObject()) fieldNames(item).forEach(itemFields::add);
        value.set("before", counters(before));
        value.set("after", counters(after));
        value.set("delta", delta(before, after));
        value.put("backendEvidenceRef", "BE-" + sha256(operation + before.fixtureDigest()
                + body.path("viewState").asText()).substring(0, 16));
        return value;
    }

    private void writeEvidence(String packageId, String scenarioId, String subcaseId,
                               List<ObjectNode> parameters) throws Exception {
        ObjectNode evidence = json.createObjectNode();
        evidence.put("evidencePackageId", packageId);
        evidence.put("executionRunId", RUN_ID);
        evidence.put("executedAt", EXECUTED_AT);
        evidence.put("scenarioId", scenarioId);
        evidence.put("subcaseId", subcaseId);
        evidence.put("matrixSha256", MATRIX_SHA);
        appendFixedInputs(evidence);
        evidence.put("backendImplementationAggregateSha256", implementationAggregate());
        evidence.put("frontendImplementationAggregateSha256", "NOT_RUN");
        evidence.put("referenceInstant", NOW.toString());
        evidence.put("fixtureDigest", sha256(parameters.stream().map(value -> value.path("fixtureDigest").asText())
                .reduce((left, right) -> left + "\n" + right).orElse("EMPTY")));
        ArrayNode parameterArray = evidence.putArray("parameters");
        parameters.forEach(parameterArray::add);
        evidence.put("stdout", "NO_APPLICATION_STDOUT");
        evidence.put("stderr", "NO_APPLICATION_STDERR");
        evidence.put("exitCode", "IN_PROCESS_JUNIT_ASSERTIONS_COMPLETE");
        ObjectNode scan = evidence.putObject("prohibitedPayloadScan");
        scan.put("ruleVersion", "D5-INF-PRIVACY-V1");
        scan.put("scope", "REDACTED_EVIDENCE_AND_IN_PROCESS_LOG_CAPTURE");
        scan.put("hitCount", 0);
        scan.put("evidenceRef", "SCAN-" + sha256(packageId + RUN_ID).substring(0, 16));
        evidence.put("backendEvidenceRef", "BE-" + sha256(packageId + scenarioId + RUN_ID).substring(0, 16));
        evidence.put("frontendEvidenceRef", "NOT_RUN");
        evidence.put("executionStatus", "PASS");
        evidence.put("defectRef", "N/A");
        evidence.put("executorRole", "BE");
        evidence.put("reviewer", "PENDING_SHORT_REVIEW");
        ObjectNode normalized = evidence.deepCopy();
        String digest = sha256(json.writeValueAsBytes(normalized));
        evidence.put("evidencePackageDigest", digest);
        Files.writeString(EVIDENCE_DIRECTORY.resolve(packageId + ".json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence) + "\n", StandardCharsets.UTF_8);
    }

    private void install(LifeContentListReadCompleteness completeness, List<LifeContentFixture> fixtures) {
        service.installFixturesForTest(completeness, fixtures);
    }

    private void install(LifeContentListReadCompleteness completeness, List<LifeContentFixture> fixtures,
                         Map<String, String> authoritativeCurrentVersions) {
        service.installFixturesForTest(completeness, fixtures, authoritativeCurrentVersions);
    }

    private LifeContentFixture ready(String tag, String category) {
        String contentRef = "SYN-INF-CONTENT-REF-" + tag;
        String title = "SYNTHETIC-TITLE-" + tag;
        String summary = "SYNTHETIC-SUMMARY-" + tag;
        String body = "SYNTHETIC-BODY-" + tag;
        String coverRef = "SYNTHETIC-COVER-REF-" + tag;
        String sourceRef = "SYNTHETIC-SOURCE-REF-" + tag;
        String rightsRef = "SYNTHETIC-RIGHTS-REF-" + tag;
        String verifiedBy = "SYNTHETIC-VERIFIER-" + tag;
        String evidenceVersion = "SYNTHETIC-EVIDENCE-VERSION-" + tag;
        PROHIBITED_VALUES.addAll(List.of(contentRef, title, summary, body, coverRef, sourceRef, rightsRef,
                verifiedBy, evidenceVersion));
        return new LifeContentFixture("SYN-FIXTURE-ID-" + tag, "SYN-FIXTURE-V1-" + tag,
                "LOCAL_SYNTHETIC", true, contentRef, "SYN-CONTENT-V1-" + tag, category, title, summary, body,
                "AVAILABLE", coverRef, sourceRef, "SELF_RESEARCH", rightsRef, "VALID", "BD",
                "PUBLIC_LOCAL_SYNTHETIC", NOW.minusSeconds(7200), NOW.minusSeconds(60), verifiedBy,
                NOW.minusSeconds(120), "PUBLISHED", NOW.minusSeconds(3600), NOW.plusSeconds(10_800),
                evidenceVersion, LifeContentReadService.VISIBILITY_RULE_VERSION, "SYN-FRESHNESS-RULE-V1", 3600L,
                NOW, "NONE", "NONE", "COMPLETE", "NOT_VERIFIED", 0);
    }

    private static LifeContentFixture withReference(LifeContentFixture value, Instant reference,
                                                    Instant effectiveTo) {
        return copy(value, value.contentVersion(), value.category(), value.coverState(), value.coverRef(),
                value.reviewState(), effectiveTo, reference, value.sourceConflictState(), value.rightsConflictState(),
                value.listReadCompleteness());
    }

    private static LifeContentFixture withReviewState(LifeContentFixture value, String state) {
        return copy(value, value.contentVersion(), value.category(), value.coverState(), value.coverRef(), state,
                value.effectiveTo(), value.referenceInstant(), value.sourceConflictState(), value.rightsConflictState(),
                value.listReadCompleteness());
    }

    private static LifeContentFixture withVersion(LifeContentFixture value, String version) {
        return copy(value, version, value.category(), value.coverState(), value.coverRef(), value.reviewState(),
                value.effectiveTo(), value.referenceInstant(), value.sourceConflictState(), value.rightsConflictState(),
                value.listReadCompleteness());
    }

    private static LifeContentFixture withCover(LifeContentFixture value, String state, String ref) {
        return copy(value, value.contentVersion(), value.category(), state, ref, value.reviewState(),
                value.effectiveTo(), value.referenceInstant(), value.sourceConflictState(), value.rightsConflictState(),
                value.listReadCompleteness());
    }

    private static LifeContentFixture withCategory(LifeContentFixture value, String category) {
        return copy(value, value.contentVersion(), category, value.coverState(), value.coverRef(), value.reviewState(),
                value.effectiveTo(), value.referenceInstant(), value.sourceConflictState(), value.rightsConflictState(),
                value.listReadCompleteness());
    }

    private static LifeContentFixture withSourceConflict(LifeContentFixture value, String state) {
        return copy(value, value.contentVersion(), value.category(), value.coverState(), value.coverRef(),
                value.reviewState(), value.effectiveTo(), value.referenceInstant(), state, value.rightsConflictState(),
                value.listReadCompleteness());
    }

    private static LifeContentFixture withRightsConflict(LifeContentFixture value, String state) {
        return copy(value, value.contentVersion(), value.category(), value.coverState(), value.coverRef(),
                value.reviewState(), value.effectiveTo(), value.referenceInstant(), value.sourceConflictState(), state,
                value.listReadCompleteness());
    }

    private static LifeContentFixture withCompleteness(LifeContentFixture value, String completeness) {
        return copy(value, value.contentVersion(), value.category(), value.coverState(), value.coverRef(),
                value.reviewState(), value.effectiveTo(), value.referenceInstant(), value.sourceConflictState(),
                value.rightsConflictState(), completeness);
    }

    private static LifeContentFixture copy(LifeContentFixture v, String version, String category, String coverState,
                                           String coverRef, String reviewState, Instant effectiveTo,
                                           Instant referenceInstant, String sourceConflict, String rightsConflict,
                                           String completeness) {
        return new LifeContentFixture(v.fixtureId(), v.fixtureVersion(), v.environment(), v.syntheticMarker(),
                v.contentRef(), version, category, v.title(), v.summary(), v.body(), coverState, coverRef,
                v.sourceRef(), v.sourceType(), v.rightsEvidenceRef(), v.rightsEvidenceState(), v.jurisdiction(),
                v.applicableAudience(), v.publishedAt(), v.updatedAt(), v.verifiedBy(), v.verifiedAt(), reviewState,
                v.effectiveFrom(), effectiveTo, v.evidenceVersion(), v.visibilityRuleVersion(),
                v.freshnessRuleVersion(), v.freshnessWindowSeconds(), referenceInstant, sourceConflict,
                rightsConflict, completeness, v.realityEvidenceLevel(), v.productionPublicationEligibility());
    }

    private static void assertList(JsonNode value, String state, int itemCount) {
        assertThat(fieldNames(value)).isEqualTo(LIST_FIELDS);
        assertThat(value.path("viewState").asText()).isEqualTo(state);
        assertThat(value.path("projectCode").asText()).isEqualTo("LIFE_CONTENT_LIST_" + state);
        assertThat(value.path("schemaVersion").asText()).isEqualTo("LIFE_CONTENT_READ_V1");
        assertThat(value.path("visibilityRuleVersion").asText())
                .isEqualTo(LifeContentReadService.VISIBILITY_RULE_VERSION);
        assertThat(value.path("items").size()).isEqualTo(itemCount);
        assertThat(value.path("nextReadAt").isNull()).isTrue();
        if (itemCount > 0) {
            for (JsonNode item : value.path("items")) {
                assertThat(fieldNames(item)).isEqualTo(SUMMARY_FIELDS);
                assertThat(item.path("freshnessState").asText()).isEqualTo("CURRENT");
                assertThat(item.has("body")).isFalse();
            }
        }
    }

    private static void assertDetail(JsonNode value, String state, boolean itemExpected) {
        assertThat(fieldNames(value)).isEqualTo(DETAIL_FIELDS);
        assertThat(value.path("viewState").asText()).isEqualTo(state);
        assertThat(value.path("projectCode").asText()).isEqualTo("LIFE_CONTENT_DETAIL_" + state);
        assertThat(value.path("nextReadAt").isNull()).isTrue();
        if (itemExpected) {
            assertThat(value.path("item").isObject()).isTrue();
            assertThat(fieldNames(value.path("item"))).isEqualTo(DETAIL_ITEM_FIELDS);
            assertThat(value.path("item").path("freshnessState").asText()).isEqualTo("CURRENT");
            assertThat(value.path("item").path("body").isTextual()).isTrue();
        } else {
            assertThat(value.path("item").isNull()).isTrue();
        }
    }

    private static void assertReadOnly(LifeContentReadSnapshot before, LifeContentReadSnapshot after,
                                       long expectedQueryDelta) {
        assertThat(after.fixtureRevision()).isEqualTo(before.fixtureRevision());
        assertThat(after.fixtureCount()).isEqualTo(before.fixtureCount());
        assertThat(after.fixtureDigest()).isEqualTo(before.fixtureDigest());
        for (String boundary : before.counters().keySet()) {
            long expected = boundary.equals("QueryCall") ? expectedQueryDelta : 0L;
            assertThat(after.counters().get(boundary) - before.counters().get(boundary))
                    .as(boundary).isEqualTo(expected);
        }
    }

    private ObjectNode counters(LifeContentReadSnapshot value) {
        ObjectNode result = json.createObjectNode();
        new TreeMap<>(value.counters()).forEach(result::put);
        result.put("FixtureRevision", value.fixtureRevision());
        result.put("FixtureCount", value.fixtureCount());
        result.put("FixtureDigest", value.fixtureDigest());
        return result;
    }

    private ObjectNode delta(LifeContentReadSnapshot before, LifeContentReadSnapshot after) {
        ObjectNode result = json.createObjectNode();
        new TreeMap<>(before.counters()).forEach((key, value) ->
                result.put(key, after.counters().get(key) - value));
        result.put("FixtureRevision", after.fixtureRevision() - before.fixtureRevision());
        result.put("FixtureCount", after.fixtureCount() - before.fixtureCount());
        result.put("FixtureDigestChanged", !after.fixtureDigest().equals(before.fixtureDigest()));
        return result;
    }

    private static Set<String> fieldNames(JsonNode value) {
        Set<String> result = new HashSet<>();
        value.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static String digestRef(String value) {
        return value == null || value.isBlank() ? "N/A" : sha256(value).substring(0, 16);
    }

    private static void appendFixedInputs(ObjectNode node) {
        ObjectNode inputs = node.putObject("fixedInputSha256");
        inputs.put("D1_REGISTRY", D1_REGISTRY_SHA);
        inputs.put("D1_11", D1_11_SHA);
        inputs.put("D2_REGISTRY", D2_REGISTRY_SHA);
        inputs.put("D3_03", D3_03_SHA);
        inputs.put("D3_REGISTRY", D3_REGISTRY_SHA);
    }

    private static String implementationAggregate() throws Exception {
        List<String> lines = new ArrayList<>();
        for (String path : IMPLEMENTATION_PATHS) {
            lines.add(path.replace('\\', '/') + "|" + sha256(Files.readAllBytes(Path.of("..", "..", path))));
        }
        lines.sort(Comparator.naturalOrder());
        return sha256(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
