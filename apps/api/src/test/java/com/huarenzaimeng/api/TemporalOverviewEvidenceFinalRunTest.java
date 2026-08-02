package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory",
        "hz.temporal-overview.mode=local-synthetic",
        "hz.test-access-token=p001-formal-evidence-token"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "p001.evidence.finalRun", matches = "true")
class TemporalOverviewEvidenceFinalRunTest {
    private static final String EVIDENCE_PACKAGE_ID = "P001-BE-LOCAL-SYNTHETIC";
    private static final String REQUIRED_TEST_SELECTOR = "TemporalOverviewEvidenceFinalRunTest";
    private static final String GENERATOR_PATH =
            "apps/api/src/test/java/com/huarenzaimeng/api/TemporalOverviewEvidenceFinalRunTest.java";
    private static final String D1_SHA = "F2CA50A6C8EAD6463753E33B50A819EE51DA69117D7EDDCE9A54EC4A8F785538";
    private static final String D2_SHA = "A87E8D7BFE6D8AF421BF87421849D6065DE96EB160A37F2DCCD2AF2EA59A8E82";
    private static final String D3_REGISTRY_SHA = "492D687679228FFA2107C4BC407C156820EED05DF3A6E4950C7599E2AC0BAF87";
    private static final String D3_03_SHA = "4976103492A1E0B906BEDEB44B86C15BA8B5ED5586735389D237CC630CCD5339";
    private static final String D3_04_SHA = "1590FA192D2FA2B71B40AAF80794F5D6BED7E5CBE1A6B0497C816EBAFA384015";
    private static final String D3_05_SHA = "328F45D54215DA54C601CAA0F8FD1A24B3B1D63E16BA60D19A571FFF08DF850E";
    private static final String FRONTEND_IMPLEMENTATION_AGGREGATE_SHA =
            "FAD7562EFAA9A44BC634FA3778A1C4D384DFDB9837F54DB7737CCEBA5AF840DC";
    private static final Instant NORMAL = Instant.parse("2026-08-02T10:00:00Z");
    private static final List<String> FIXED_SCENARIO_IDS = List.of(
            "P001-TEMP-001-NORMAL-DAY", "P001-TEMP-002-CROSS-DATE",
            "P001-TEMP-003-DEVICE-TZ-NON-IMPACT", "P001-TEMP-004-DHAKA-UNAVAILABLE",
            "P001-TEMP-005-BEIJING-UNAVAILABLE", "P001-TEMP-006-BOTH-UNAVAILABLE",
            "P001-TEMP-007-STALE", "P001-TEMP-008-RECOVERED-CLIENT-ONCE",
            "P001-TEMP-009-CN-NO-HOLIDAY", "P001-TEMP-010-BD-NO-HOLIDAY",
            "P001-TEMP-011-CN-CONFIRMED-HOLIDAY", "P001-TEMP-012-BD-CONFIRMED-HOLIDAY",
            "P001-TEMP-013-PENDING-CONFIRMATION", "P001-TEMP-014-READ-ERROR",
            "P001-TEMP-015-STALE-EXPIRED", "P001-TEMP-016-UNPUBLISHED",
            "P001-TEMP-017-CRITICAL-UNKNOWN-FAIL-CLOSED",
            "P001-TEMP-018-ANONYMOUS-MINIMAL-ZERO-SIDE-EFFECT");
    private static final Map<String, String> FIXED_INPUT_FILES = Map.of(
            "项目管理/正式交付/D1-产品规划与需求/D1产品基线版本清单.md", D1_SHA,
            "项目管理/正式交付/D2-体验与UI设计/D2体验与UI设计版本清单.md", D2_SHA,
            "项目管理/正式交付/D3-技术实现基线/D3技术基线版本清单.md", D3_REGISTRY_SHA,
            "项目管理/正式交付/D3-技术实现基线/D3-03-领域状态与项目API协议.md", D3_03_SHA,
            "项目管理/正式交付/D3-技术实现基线/D3-04-数据账务与外部适配方案.md", D3_04_SHA,
            "项目管理/正式交付/D3-技术实现基线/D3-05-安全可靠性与开发门禁.md", D3_05_SHA);
    private static final List<String> IMPLEMENTATION_PATHS = List.of(
            "apps/api/src/main/java/com/huarenzaimeng/api/TemporalOverviewController.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/TemporalOverviewDomain.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/TemporalOverviewService.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/TemporalOverviewSideEffectProbe.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/config/TestAccessTokenFilter.java",
            "apps/api/src/main/resources/application.yml",
            "apps/api/src/main/resources/application-mock.yml",
            "apps/api/src/main/resources/application-release-mysql.yml",
            "apps/miniapp/scripts/assert-frontend-contracts.mjs",
            "apps/miniapp/scripts/verify-mp-weixin-output.mjs",
            "apps/miniapp/src/api/client.ts",
            "apps/miniapp/src/api/mock.ts",
            "apps/miniapp/src/api/temporal-overview-contract.ts",
            "apps/miniapp/src/domain/temporal-overview-flow.ts",
            "apps/miniapp/src/pages/index/index.vue");
    private static final List<String> STATUS_DENOMINATOR =
            List.of("PASS", "FAIL", "BLOCKED", "SKIPPED", "NA", "NOT_RUN");
    private static final List<String> PROHIBITED_PAYLOAD_MARKERS = List.of(
            "p001-formal-evidence-token", "SourceRef", "VerifiedBy", "AuthorizationRef", "EvidenceRef",
            "client_secret", "access_token", "refresh_token", "\"openid\"", "\"phoneNumber\"");
    private static final Set<String> TOP_FIELDS = Set.of("requestRef", "projectCode", "schemaVersion",
            "referenceInstant", "generatedAt", "timeZoneRuleVersion", "clockStaleAfterSeconds", "clockState",
            "clocks", "holidayRuleVersion", "holidays", "retryClass");
    private static final Set<String> CLOCK_FIELDS = Set.of("cityCode", "displayName", "zoneId", "localDate",
            "localTime", "availabilityState");
    private static final Set<String> HOLIDAY_FIELDS = Set.of("countryCode", "localDate", "state", "holidayId",
            "name", "note", "sourceType", "sourceCoverageDate", "effectiveFrom", "effectiveTo", "version");

    private static String executionRunId;
    private static String executedAt;
    private static String generatorSha;
    private static String implementationAggregateSha;
    private static Path evidenceDirectory;
    private static boolean finalRunEnabled;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TemporalOverviewService service;
    @MockBean Clock clock;

    @BeforeAll
    static void prepareEvidenceRun() throws Exception {
        finalRunEnabled = "true".equals(System.getProperty("p001.evidence.finalRun"));
        Assumptions.assumeTrue(finalRunEnabled, "P001 formal generator requires the approved one-shot wrapper");
        Path root = repositoryRoot();
        executionRunId = requireProperty("p001.evidence.executionRunId");
        assertThat(executionRunId).matches("^P001-BE-[A-Za-z0-9][A-Za-z0-9._-]{7,80}$");
        assertThat(requireProperty("p001.evidence.testSelector")).isEqualTo(REQUIRED_TEST_SELECTOR);
        assertThat(requireProperty("p001.evidence.expectedFrontendAggregateSha256"))
                .isEqualTo(FRONTEND_IMPLEMENTATION_AGGREGATE_SHA);
        generatorSha = sha256(Files.readAllBytes(root.resolve(GENERATOR_PATH)));
        assertThat(generatorSha).isEqualTo(requireProperty("p001.evidence.expectedGeneratorSha256"));
        implementationAggregateSha = aggregate(root, IMPLEMENTATION_PATHS);
        assertThat(implementationAggregateSha)
                .isEqualTo(requireProperty("p001.evidence.expectedImplementationAggregateSha256"));
        verifyFixedInputs(root);
        verifyExactScenarioIdentity();

        Path finalDirectory = root.resolve("apps/api/target/p001-temporal-evidence/runs")
                .resolve(executionRunId).toAbsolutePath().normalize();
        assertThat(finalDirectory).doesNotExist();
        Path stagingDirectory = Path.of(requireProperty("p001.evidence.stagingDirectory"))
                .toAbsolutePath().normalize();
        Path allowedRoot = root.resolve("apps/api/target/p001-temporal-evidence/staging")
                .toAbsolutePath().normalize();
        assertThat(stagingDirectory.startsWith(allowedRoot)).isTrue();
        evidenceDirectory = stagingDirectory.resolve("cases");
        assertThat(evidenceDirectory).doesNotExist();
        Path authorizationFile = Path.of(requireProperty("p001.evidence.authorizationFile"))
                .toAbsolutePath().normalize();
        assertThat(authorizationFile).isRegularFile();
        assertThat(sha256(Files.readAllBytes(authorizationFile)))
                .isEqualTo(requireProperty("p001.evidence.authorizationFileSha256"));
        verifyAuthorization(authorizationFile);
        Files.createDirectories(evidenceDirectory);
        executedAt = Instant.now().toString();
    }

    @AfterAll
    static void finalizeGeneratorValidation() throws Exception {
        if (!finalRunEnabled || evidenceDirectory == null || !Files.isDirectory(evidenceDirectory)) return;
        ObjectMapper mapper = new ObjectMapper();
        List<Path> caseFiles;
        try (var stream = Files.list(evidenceDirectory)) {
            caseFiles = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        Set<String> expectedNames = new HashSet<>();
        Map<String, String> statuses = new LinkedHashMap<>();
        boolean schemaValid = true;
        for (String scenarioId : FIXED_SCENARIO_IDS) {
            String fileName = evidenceFileName(scenarioId);
            expectedNames.add(fileName);
            Path file = evidenceDirectory.resolve(fileName);
            JsonNode evidence = Files.exists(file) ? mapper.readTree(Files.readString(file)) : null;
            String status = evidence == null ? "NOT_RUN" : evidence.path("executionStatus").asText("FAIL");
            if (evidence != null && !caseEvidenceValid(evidence, scenarioId)) {
                status = "FAIL";
                schemaValid = false;
            }
            statuses.put(scenarioId, status);
        }
        boolean exact18 = caseFiles.size() == 18
                && caseFiles.stream().map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet())
                .equals(expectedNames);
        long prohibitedHits = caseFiles.stream().mapToLong(file -> {
            try { return prohibitedHits(Files.readString(file), PROHIBITED_PAYLOAD_MARKERS); }
            catch (Exception error) { throw new IllegalStateException(error); }
        }).sum();
        String canary = "P001-EVIDENCE-SCAN-CANARY";
        boolean canarySensitive = prohibitedHits("prefix|" + canary + "|suffix", List.of(canary)) == 1;

        ObjectNode validation = mapper.createObjectNode();
        appendFixedIdentity(validation);
        validation.put("candidateConsumable", false);
        validation.put("readyWritten", false);
        validation.put("expectedStableEvidenceCount", 18);
        validation.put("actualEvidenceFileCount", caseFiles.size());
        validation.put("exact18NoExtras", exact18);
        validation.put("caseSchemaAndAssertionBindingValid", schemaValid);
        validation.put("allCasesPass", statuses.values().stream().allMatch("PASS"::equals));
        ObjectNode counts = validation.putObject("statusCounts");
        int total = 0;
        for (String status : STATUS_DENOMINATOR) {
            int count = (int) statuses.values().stream().filter(status::equals).count();
            counts.put(status, count);
            total += count;
        }
        validation.put("statusTotal", total);
        validation.put("sixStateTotalEquals18", total == 18);
        validation.put("prohibitedPayloadHitCount", prohibitedHits);
        validation.put("prohibitedScanCanarySensitive", canarySensitive);
        validation.put("prohibitedScanCanarySha256", sha256(canary));
        ArrayNode manifest = validation.putArray("implementationManifest");
        for (String path : IMPLEMENTATION_PATHS) {
            ObjectNode item = manifest.addObject();
            item.put("path", path);
            item.put("sha256", sha256(Files.readAllBytes(repositoryRoot().resolve(path))));
        }
        ArrayNode files = validation.putArray("evidenceFiles");
        for (Path file : caseFiles) {
            ObjectNode item = files.addObject();
            item.put("path", file.getFileName().toString());
            item.put("sha256", sha256(Files.readAllBytes(file)));
        }
        boolean pass = exact18 && schemaValid && total == 18 && statuses.values().stream().allMatch("PASS"::equals)
                && prohibitedHits == 0 && canarySensitive;
        validation.put("executionStatus", pass ? "PASS" : "FAIL");
        writeJsonAtomically(mapper, evidenceDirectory.getParent().resolve("generator-validation.json"), validation);
        assertThat(pass).isTrue();
    }

    @Test void P001_TEMP_001_NORMAL_DAY() throws Exception {
        Captured op = capture(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        assertStates(op, "BOTH_AVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED");
        writeEvidence(FIXED_SCENARIO_IDS.get(0), "normal same-instant projection",
                expected("BOTH_AVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_002_CROSS_DATE() throws Exception {
        Instant reference = Instant.parse("2026-08-02T17:00:00Z");
        Captured op = capture(reference, TemporalOverviewService.normalFixture(reference));
        assertThat(op.response().path("clocks").path("dhaka").path("localDate").asText()).isEqualTo("2026-08-02");
        assertThat(op.response().path("clocks").path("beijing").path("localDate").asText()).isEqualTo("2026-08-03");
        writeEvidence(FIXED_SCENARIO_IDS.get(1), "Dhaka and Beijing resolve to different local dates",
                expected("BOTH_AVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_003_DEVICE_TZ_NON_IMPACT() throws Exception {
        TimeZone prior = TimeZone.getDefault();
        Captured first;
        Captured second;
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            first = capture(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            second = capture(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        } finally { TimeZone.setDefault(prior); }
        assertThat(second.response().path("clocks")).isEqualTo(first.response().path("clocks"));
        assertThat(second.response().path("holidays")).isEqualTo(first.response().path("holidays"));
        writeEvidence(FIXED_SCENARIO_IDS.get(2), "system default timezone cannot affect fixed ZoneId projection",
                expected("BOTH_AVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED"), List.of(first, second));
    }

    @Test void P001_TEMP_004_DHAKA_UNAVAILABLE() throws Exception {
        Captured op = capture(NORMAL, availability("UNAVAILABLE", "AVAILABLE"));
        assertStates(op, "DHAKA_UNAVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED");
        assertUnavailable(op.response().path("clocks").path("dhaka"));
        writeEvidence(FIXED_SCENARIO_IDS.get(3), "Dhaka unavailable preserves Beijing current value",
                expected("DHAKA_UNAVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_005_BEIJING_UNAVAILABLE() throws Exception {
        Captured op = capture(NORMAL, availability("AVAILABLE", "UNAVAILABLE"));
        assertStates(op, "BEIJING_UNAVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED");
        assertUnavailable(op.response().path("clocks").path("beijing"));
        writeEvidence(FIXED_SCENARIO_IDS.get(4), "Beijing unavailable preserves Dhaka current value",
                expected("BEIJING_UNAVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_006_BOTH_UNAVAILABLE() throws Exception {
        Captured op = capture(NORMAL, availability("UNAVAILABLE", "UNAVAILABLE"));
        assertStates(op, "BOTH_UNAVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED");
        assertUnavailable(op.response().path("clocks").path("dhaka"));
        assertUnavailable(op.response().path("clocks").path("beijing"));
        writeEvidence(FIXED_SCENARIO_IDS.get(5), "both clocks unavailable withdraw current values",
                expected("BOTH_UNAVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_007_STALE() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalOverviewFixture stale = copy(base, NORMAL.plusSeconds(31), 30L, base.dhakaAvailability(),
                base.beijingAvailability(), base.china(), base.bangladesh());
        Captured op = capture(NORMAL, stale);
        assertStates(op, "STALE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED");
        assertUnavailable(op.response().path("clocks").path("dhaka"));
        assertUnavailable(op.response().path("clocks").path("beijing"));
        writeEvidence(FIXED_SCENARIO_IDS.get(6), "stale threshold withdraws both current clock values",
                expected("STALE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_008_RECOVERED_CLIENT_ONCE() throws Exception {
        Captured prior = capture(NORMAL, availability("UNAVAILABLE", "AVAILABLE"));
        Instant currentReference = NORMAL.plusSeconds(1);
        Captured current = capture(currentReference, TemporalOverviewService.normalFixture(currentReference));
        assertThat(prior.response().path("clockState").asText()).isEqualTo("DHAKA_UNAVAILABLE");
        assertThat(current.response().path("clockState").asText()).isEqualTo("BOTH_AVAILABLE");
        assertThat(current.response().path("clockState").asText()).isNotEqualTo("RECOVERED");
        ObjectNode expected = expected("BOTH_AVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED");
        expected.put("clientRecoveredEligibleOnce", true).put("serverReturnsRecovered", false);
        writeEvidence(FIXED_SCENARIO_IDS.get(7), "new reference recovers; RECOVERED remains one client render",
                expected, List.of(prior, current));
    }

    @Test void P001_TEMP_009_CN_NO_HOLIDAY() throws Exception {
        Captured op = capture(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        assertNoHoliday(op.response().path("holidays").path("china"));
        writeEvidence(FIXED_SCENARIO_IDS.get(8), "CN no-holiday requires explicit same-date coverage",
                expected("BOTH_AVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_010_BD_NO_HOLIDAY() throws Exception {
        Captured op = capture(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        assertNoHoliday(op.response().path("holidays").path("bangladesh"));
        writeEvidence(FIXED_SCENARIO_IDS.get(9), "BD no-holiday requires explicit same-date coverage",
                expected("BOTH_AVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_011_CN_CONFIRMED_HOLIDAY() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision cn = holiday("CONFIRMED_HOLIDAY", "SYN-CN-H-001", "Synthetic CN Holiday", null);
        Captured op = capture(NORMAL, withHolidays(base, cn, base.bangladesh()));
        assertStates(op, "BOTH_AVAILABLE", "CONFIRMED_HOLIDAY", "NO_HOLIDAY_CONFIRMED");
        writeEvidence(FIXED_SCENARIO_IDS.get(10), "CN confirmed holiday is independent from BD",
                expected("BOTH_AVAILABLE", "CONFIRMED_HOLIDAY", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_012_BD_CONFIRMED_HOLIDAY() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision bd = holiday("CONFIRMED_HOLIDAY", "SYN-BD-H-001", "Synthetic BD Holiday", null);
        Captured op = capture(NORMAL, withHolidays(base, base.china(), bd));
        assertStates(op, "BOTH_AVAILABLE", "NO_HOLIDAY_CONFIRMED", "CONFIRMED_HOLIDAY");
        writeEvidence(FIXED_SCENARIO_IDS.get(11), "BD confirmed holiday is independent from CN",
                expected("BOTH_AVAILABLE", "NO_HOLIDAY_CONFIRMED", "CONFIRMED_HOLIDAY"), List.of(op));
    }

    @Test void P001_TEMP_013_PENDING_CONFIRMATION() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision cn = holiday("PENDING_CONFIRMATION", "SYN-CN-PENDING", "Synthetic Expected Day",
                "预计安排，待官方确认");
        Captured op = capture(NORMAL, withHolidays(base, cn, base.bangladesh()));
        assertStates(op, "BOTH_AVAILABLE", "PENDING_CONFIRMATION", "NO_HOLIDAY_CONFIRMED");
        assertThat(op.response().path("holidays").path("china").path("note").asText()).contains("待官方确认");
        writeEvidence(FIXED_SCENARIO_IDS.get(12), "pending state retains expected and official-confirmation semantics",
                expected("BOTH_AVAILABLE", "PENDING_CONFIRMATION", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_014_READ_ERROR() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision error = holiday("READ_ERROR", "REVOKED", "Revoked", "Revoked note");
        Captured op = capture(NORMAL, withHolidays(base, error, base.bangladesh()));
        assertStates(op, "BOTH_AVAILABLE", "READ_ERROR", "NO_HOLIDAY_CONFIRMED");
        assertReadErrorEmpty(op.response().path("holidays").path("china"));
        writeEvidence(FIXED_SCENARIO_IDS.get(13), "read error withdraws conclusion and invalid metadata",
                expected("BOTH_AVAILABLE", "READ_ERROR", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_015_STALE_EXPIRED() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision expired = new TemporalHolidayDecision(LocalDate.parse("2026-08-02"),
                "CONFIRMED_HOLIDAY", "REVOKED-EXPIRED", "Revoked Expired", "Revoked note",
                "LOCAL_SYNTHETIC_CALENDAR", LocalDate.parse("2026-08-02"), NORMAL.minusSeconds(7200),
                NORMAL.minusSeconds(1), "SYN-CALENDAR-V1", NORMAL.minusSeconds(60), "NONE");
        Captured op = capture(NORMAL, withHolidays(base, expired, base.bangladesh()));
        assertStates(op, "BOTH_AVAILABLE", "STALE_OR_EXPIRED", "NO_HOLIDAY_CONFIRMED");
        assertNonConclusion(op.response().path("holidays").path("china"));
        writeEvidence(FIXED_SCENARIO_IDS.get(14), "expired holiday withdraws prior conclusion",
                expected("BOTH_AVAILABLE", "STALE_OR_EXPIRED", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_016_UNPUBLISHED() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision unpublished = holiday("UNPUBLISHED", "REVOKED-UNPUBLISHED",
                "Revoked Unpublished", "Revoked note");
        Captured op = capture(NORMAL, withHolidays(base, unpublished, base.bangladesh()));
        assertStates(op, "BOTH_AVAILABLE", "UNPUBLISHED", "NO_HOLIDAY_CONFIRMED");
        assertNonConclusion(op.response().path("holidays").path("china"));
        writeEvidence(FIXED_SCENARIO_IDS.get(15), "unpublished holiday withdraws prior conclusion",
                expected("BOTH_AVAILABLE", "UNPUBLISHED", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    @Test void P001_TEMP_017_CRITICAL_UNKNOWN_FAIL_CLOSED() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalOverviewFixture invalid = new TemporalOverviewFixture(true, null, null, null, null,
                "AVAILABLE", "AVAILABLE", null, null, null, base.china(), base.bangladesh());
        Captured op = capture(NORMAL, invalid);
        assertStates(op, "STALE", "READ_ERROR", "READ_ERROR");
        assertUnavailable(op.response().path("clocks").path("dhaka"));
        assertUnavailable(op.response().path("clocks").path("beijing"));
        assertReadErrorEmpty(op.response().path("holidays").path("china"));
        assertReadErrorEmpty(op.response().path("holidays").path("bangladesh"));
        writeEvidence(FIXED_SCENARIO_IDS.get(16), "critical unknown produces mapper-safe fail-closed envelope",
                expected("STALE", "READ_ERROR", "READ_ERROR"), List.of(op));
    }

    @Test void P001_TEMP_018_ANONYMOUS_MINIMAL_ZERO_SIDE_EFFECT() throws Exception {
        Captured op = capture(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        assertThat(op.response().toString()).doesNotContain("SourceRef", "VerifiedBy", "AuthorizationRef",
                "EvidenceRef", "openid", "phone", "device");
        assertReadOnly(op.before(), op.after());
        writeEvidence(FIXED_SCENARIO_IDS.get(17), "anonymous minimal response and complete zero-side-effect vector",
                expected("BOTH_AVAILABLE", "NO_HOLIDAY_CONFIRMED", "NO_HOLIDAY_CONFIRMED"), List.of(op));
    }

    private Captured capture(Instant reference, TemporalOverviewFixture fixture) throws Exception {
        reset(clock);
        when(clock.instant()).thenReturn(reference);
        service.installFixtureForTest(fixture);
        TemporalOverviewSnapshot before = service.snapshotForTest();
        MvcResult result = mvc.perform(get("/api/v1/home/temporal-overview")).andReturn();
        TemporalOverviewSnapshot after = service.snapshotForTest();
        JsonNode response = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertStrict(response);
        assertThat(response.path("referenceInstant").asText()).isEqualTo(reference.toString());
        assertReadOnly(before, after);
        return new Captured(before, after, response, result.getResponse().getStatus(),
                result.getResponse().getHeader("Cache-Control"));
    }

    private void writeEvidence(String scenarioId, String inputDescription, ObjectNode expected,
                               List<Captured> operations) throws Exception {
        assertThat(FIXED_SCENARIO_IDS).contains(scenarioId);
        ObjectNode evidence = json.createObjectNode();
        appendFixedIdentity(evidence);
        evidence.put("scenarioId", scenarioId);
        evidence.put("subcaseId", scenarioId);
        evidence.put("environment", "LOCAL_SYNTHETIC_ONLY");
        evidence.put("networkPolicy", "DENY_EXTERNAL");
        evidence.put("implementationManifestRef", "evidence-package-index.json#/implementationManifest");
        evidence.putObject("input").put("description", inputDescription)
                .put("fixtureDigest", operations.get(0).before().fixtureDigest());
        evidence.set("expected", expected);
        ObjectNode actual = evidence.putObject("actual");
        actual.put("operationCount", operations.size());
        actual.put("allStrictDto", true).put("allNoStore", true).put("allReadOnly", true);
        ArrayNode states = actual.putArray("clockStates");
        operations.forEach(op -> states.add(op.response().path("clockState").asText()));
        ArrayNode opArray = evidence.putArray("operations");
        ArrayNode before = evidence.putArray("before");
        ArrayNode after = evidence.putArray("after");
        ArrayNode delta = evidence.putArray("delta");
        for (int index = 0; index < operations.size(); index++) {
            Captured op = operations.get(index);
            ObjectNode item = opArray.addObject();
            item.put("operationIndex", index + 1).put("httpStatus", op.httpStatus())
                    .put("cacheControl", op.cacheControl());
            item.set("response", op.response());
            ObjectNode beforeNode = counters(op.before());
            ObjectNode afterNode = counters(op.after());
            ObjectNode deltaNode = delta(op.before(), op.after());
            item.set("before", beforeNode);
            item.set("after", afterNode);
            item.set("delta", deltaNode);
            before.add(beforeNode); after.add(afterNode); delta.add(deltaNode);
        }
        evidence.put("executionStatus", "PASS");
        evidence.put("defectRef", "N/A");
        String serialized = json.writeValueAsString(evidence);
        for (String marker : PROHIBITED_PAYLOAD_MARKERS) assertThat(serialized).doesNotContain(marker);
        writeJsonAtomically(json, evidenceDirectory.resolve(evidenceFileName(scenarioId)), evidence);
        System.out.println("P001_TEMPORAL_EVIDENCE|" + serialized);
    }

    private static ObjectNode expected(String clockState, String china, String bangladesh) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode value = mapper.createObjectNode();
        value.put("httpStatus", 200).put("cacheControl", "no-store").put("strictTopFields", 12)
                .put("strictClockFields", 6).put("strictHolidayFields", 11)
                .put("clockState", clockState).put("chinaState", china).put("bangladeshState", bangladesh)
                .put("queryCallDeltaPerOperation", 1).put("allOtherSideEffectDelta", 0);
        return value;
    }

    private static void assertStates(Captured op, String clock, String china, String bangladesh) {
        assertThat(op.response().path("clockState").asText()).isEqualTo(clock);
        assertThat(op.response().path("holidays").path("china").path("state").asText()).isEqualTo(china);
        assertThat(op.response().path("holidays").path("bangladesh").path("state").asText()).isEqualTo(bangladesh);
    }

    private static void assertStrict(JsonNode body) {
        assertThat(fields(body)).isEqualTo(TOP_FIELDS);
        assertThat(body.path("schemaVersion").asText()).isEqualTo("TEMPORAL_OVERVIEW_V1");
        assertThat(Instant.parse(body.path("generatedAt").asText()))
                .isAfterOrEqualTo(Instant.parse(body.path("referenceInstant").asText()));
        assertThat(body.path("timeZoneRuleVersion").asText()).isNotBlank();
        assertThat(body.path("clockStaleAfterSeconds").isIntegralNumber()).isTrue();
        assertThat(body.path("clockStaleAfterSeconds").asLong()).isBetween(1L, 9_007_199_254_740_991L);
        assertThat(body.path("holidayRuleVersion").asText()).isNotBlank();
        assertThat(fields(body.path("clocks"))).containsExactlyInAnyOrder("dhaka", "beijing");
        assertThat(fields(body.path("holidays"))).containsExactlyInAnyOrder("china", "bangladesh");
        body.path("clocks").forEach(value -> assertThat(fields(value)).isEqualTo(CLOCK_FIELDS));
        body.path("holidays").forEach(value -> assertThat(fields(value)).isEqualTo(HOLIDAY_FIELDS));
        assertThat(body.path("clockState").asText()).isNotIn("LOADING", "RECOVERED");
        body.path("holidays").forEach(value -> {
            assertThat(value.path("state").asText()).isNotEqualTo("LOADING");
            if (Set.of("READ_ERROR", "STALE_OR_EXPIRED", "UNPUBLISHED").contains(value.path("state").asText())) {
                assertNonConclusion(value);
            }
        });
    }

    private static void assertReadOnly(TemporalOverviewSnapshot before, TemporalOverviewSnapshot after) {
        assertThat(after.fixtureRevision()).isEqualTo(before.fixtureRevision());
        assertThat(after.fixtureDigest()).isEqualTo(before.fixtureDigest());
        before.counters().forEach((boundary, count) -> assertThat(after.counters().get(boundary) - count)
                .as(boundary).isEqualTo(boundary.equals("QueryCall") ? 1L : 0L));
    }

    private static void assertUnavailable(JsonNode value) {
        assertThat(value.path("availabilityState").asText()).isEqualTo("UNAVAILABLE");
        assertThat(value.path("localDate").isNull()).isTrue();
        assertThat(value.path("localTime").isNull()).isTrue();
    }

    private static void assertNoHoliday(JsonNode value) {
        assertThat(value.path("state").asText()).isEqualTo("NO_HOLIDAY_CONFIRMED");
        assertNonConclusion(value);
        assertThat(value.path("sourceCoverageDate").asText()).isEqualTo(value.path("localDate").asText());
    }

    private static void assertNonConclusion(JsonNode value) {
        assertThat(value.path("holidayId").isNull()).isTrue();
        assertThat(value.path("name").isNull()).isTrue();
        assertThat(value.path("note").isNull()).isTrue();
    }

    private static void assertReadErrorEmpty(JsonNode value) {
        assertNonConclusion(value);
        for (String field : List.of("sourceType", "sourceCoverageDate", "effectiveFrom", "effectiveTo", "version")) {
            assertThat(value.path(field).isNull()).as(field).isTrue();
        }
    }

    private ObjectNode counters(TemporalOverviewSnapshot snapshot) {
        ObjectNode value = json.createObjectNode();
        snapshot.counters().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> value.put(entry.getKey(), entry.getValue()));
        value.put("FixtureRevision", snapshot.fixtureRevision());
        value.put("FixtureDigest", snapshot.fixtureDigest());
        return value;
    }

    private ObjectNode delta(TemporalOverviewSnapshot before, TemporalOverviewSnapshot after) {
        ObjectNode value = json.createObjectNode();
        before.counters().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                value.put(entry.getKey(), after.counters().get(entry.getKey()) - entry.getValue()));
        value.put("FixtureRevision", after.fixtureRevision() - before.fixtureRevision());
        value.put("FixtureDigestChanged", !after.fixtureDigest().equals(before.fixtureDigest()));
        return value;
    }

    private static TemporalOverviewFixture availability(String dhaka, String beijing) {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        return copy(base, base.generatedAt(), base.clockStaleAfterSeconds(), dhaka, beijing,
                base.china(), base.bangladesh());
    }

    private static TemporalOverviewFixture withHolidays(TemporalOverviewFixture base, TemporalHolidayDecision china,
                                                        TemporalHolidayDecision bangladesh) {
        return copy(base, base.generatedAt(), base.clockStaleAfterSeconds(), base.dhakaAvailability(),
                base.beijingAvailability(), china, bangladesh);
    }

    private static TemporalOverviewFixture copy(TemporalOverviewFixture base, Instant generatedAt, Long staleAfter,
                                                String dhaka, String beijing, TemporalHolidayDecision china,
                                                TemporalHolidayDecision bangladesh) {
        return new TemporalOverviewFixture(base.syntheticMarker(), base.referenceInstant(), generatedAt,
                base.timeZoneRuleVersion(), staleAfter, dhaka, beijing, base.holidayRuleVersion(),
                base.allowedHolidaySourceType(), base.holidayEvidenceMaxAgeSeconds(), china, bangladesh);
    }

    private static TemporalHolidayDecision holiday(String state, String id, String name, String note) {
        LocalDate date = LocalDate.parse("2026-08-02");
        return new TemporalHolidayDecision(date, state, id, name, note, "LOCAL_SYNTHETIC_CALENDAR", date,
                NORMAL.minusSeconds(3600), NORMAL.plusSeconds(3600), "SYN-CALENDAR-V1",
                NORMAL.minusSeconds(60), "NONE");
    }

    private static void appendFixedIdentity(ObjectNode value) {
        value.put("evidencePackageId", EVIDENCE_PACKAGE_ID);
        value.put("executionRunId", executionRunId);
        value.put("executedAt", executedAt);
        value.put("d1Sha256", D1_SHA);
        value.put("d2Sha256", D2_SHA);
        value.put("d3RegistrySha256", D3_REGISTRY_SHA);
        value.put("d3_03Sha256", D3_03_SHA);
        value.put("d3_04Sha256", D3_04_SHA);
        value.put("d3_05Sha256", D3_05_SHA);
        value.put("frontendImplementationAggregateSha256", FRONTEND_IMPLEMENTATION_AGGREGATE_SHA);
        value.put("crossStackImplementationAggregateSha256", implementationAggregateSha);
        value.put("evidenceGeneratorSha256", generatorSha);
    }

    private static void verifyFixedInputs(Path root) throws Exception {
        for (Map.Entry<String, String> entry : FIXED_INPUT_FILES.entrySet()) {
            assertThat(sha256(Files.readAllBytes(root.resolve(entry.getKey())))).as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    private static void verifyExactScenarioIdentity() {
        List<String> methods = java.util.Arrays.stream(TemporalOverviewEvidenceFinalRunTest.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .map(java.lang.reflect.Method::getName).sorted().toList();
        assertThat(methods).hasSize(18);
        assertThat(new HashSet<>(FIXED_SCENARIO_IDS)).hasSize(18);
        for (int index = 1; index <= 18; index++) {
            String prefix = String.format("P001_TEMP_%03d_", index);
            assertThat(methods.stream().filter(name -> name.startsWith(prefix)).count()).isEqualTo(1L);
        }
    }

    private static void verifyAuthorization(Path file) throws Exception {
        JsonNode authorization = new ObjectMapper().readTree(Files.readString(file));
        assertThat(authorization.path("runId").asText()).isEqualTo(executionRunId);
        assertThat(authorization.path("finalRunAuthorized").asBoolean()).isTrue();
        assertThat(authorization.path("authorizationConsumed").asBoolean()).isTrue();
        assertThat(authorization.path("testSelector").asText()).isEqualTo(REQUIRED_TEST_SELECTOR);
        assertThat(authorization.path("expectedGeneratorSha256").asText()).isEqualTo(generatorSha);
        assertThat(authorization.path("stagingDirectory").asText()).isEqualTo(evidenceDirectory.getParent().toString());
    }

    private static boolean caseEvidenceValid(JsonNode value, String scenarioId) {
        List<String> required = List.of("evidencePackageId", "executionRunId", "executedAt", "scenarioId",
                "subcaseId", "environment", "networkPolicy", "d1Sha256", "d2Sha256", "d3RegistrySha256",
                "d3_03Sha256", "d3_04Sha256", "d3_05Sha256", "frontendImplementationAggregateSha256",
                "crossStackImplementationAggregateSha256", "evidenceGeneratorSha256", "input", "expected",
                "actual", "operations", "before", "after", "delta", "executionStatus", "defectRef");
        return required.stream().allMatch(value::has)
                && EVIDENCE_PACKAGE_ID.equals(value.path("evidencePackageId").asText())
                && executionRunId.equals(value.path("executionRunId").asText())
                && scenarioId.equals(value.path("scenarioId").asText())
                && scenarioId.equals(value.path("subcaseId").asText())
                && "PASS".equals(value.path("executionStatus").asText())
                && value.path("operations").isArray() && !value.path("operations").isEmpty()
                && value.path("before").size() == value.path("operations").size()
                && value.path("after").size() == value.path("operations").size()
                && value.path("delta").size() == value.path("operations").size();
    }

    private static String aggregate(Path root, List<String> paths) throws Exception {
        List<String> lines = new ArrayList<>();
        for (String path : paths.stream().sorted().toList()) {
            lines.add(path.replace('\\', '/') + "|" + sha256(Files.readAllBytes(root.resolve(path))));
        }
        return sha256(String.join("\n", lines));
    }

    private static long prohibitedHits(String text, List<String> markers) {
        long total = 0;
        for (String marker : markers) {
            int from = 0;
            while ((from = text.indexOf(marker, from)) >= 0) { total++; from += marker.length(); }
        }
        return total;
    }

    private static void writeJsonAtomically(ObjectMapper mapper, Path path, JsonNode value) throws Exception {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n",
                StandardCharsets.UTF_8);
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Path repositoryRoot() { return Path.of("..", "..").toAbsolutePath().normalize(); }
    private static String requireProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("MISSING_REQUIRED_PROPERTY|" + name);
        return value;
    }
    private static String evidenceFileName(String scenarioId) { return scenarioId + ".json"; }
    private static Set<String> fields(JsonNode value) {
        Set<String> names = new HashSet<>(); value.fieldNames().forEachRemaining(names::add); return names;
    }
    private static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private record Captured(TemporalOverviewSnapshot before, TemporalOverviewSnapshot after, JsonNode response,
                            int httpStatus, String cacheControl) {}
}
