package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
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
        "hz.test-access-token=synthetic-token-that-must-not-be-required"
})
@AutoConfigureMockMvc
class TemporalOverviewApiContractTest {
    private static final Instant NORMAL = Instant.parse("2026-08-02T10:00:00Z");
    private static final Set<String> TOP_FIELDS = Set.of("requestRef", "projectCode", "schemaVersion",
            "referenceInstant", "generatedAt", "timeZoneRuleVersion", "clockStaleAfterSeconds", "clockState",
            "clocks", "holidayRuleVersion", "holidays", "retryClass");
    private static final Set<String> CLOCK_FIELDS = Set.of("cityCode", "displayName", "zoneId", "localDate",
            "localTime", "availabilityState");
    private static final Set<String> HOLIDAY_FIELDS = Set.of("countryCode", "localDate", "state", "holidayId",
            "name", "note", "sourceType", "sourceCoverageDate", "effectiveFrom", "effectiveTo", "version");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TemporalOverviewService service;
    @Autowired TemporalOverviewSideEffectProbe sideEffects;
    @MockBean Clock clock;

    @BeforeEach
    void prepare() {
        reset(clock);
        install(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
    }

    @Test
    void P001_TEMP_001_NORMAL_DAY() throws Exception {
        JsonNode body = readOnce(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        assertThat(body.path("clockState").asText()).isEqualTo("BOTH_AVAILABLE");
        assertThat(body.path("holidays").path("china").path("state").asText())
                .isEqualTo("NO_HOLIDAY_CONFIRMED");
        assertThat(body.path("holidays").path("bangladesh").path("state").asText())
                .isEqualTo("NO_HOLIDAY_CONFIRMED");
    }

    @Test
    void P001_TEMP_002_CROSS_DATE() throws Exception {
        Instant reference = Instant.parse("2026-08-02T17:00:00Z");
        JsonNode body = readOnce(reference, TemporalOverviewService.normalFixture(reference));
        assertThat(body.path("clocks").path("dhaka").path("localDate").asText()).isEqualTo("2026-08-02");
        assertThat(body.path("clocks").path("beijing").path("localDate").asText()).isEqualTo("2026-08-03");
        assertThat(body.path("holidays").path("bangladesh").path("localDate").asText()).isEqualTo("2026-08-02");
        assertThat(body.path("holidays").path("china").path("localDate").asText()).isEqualTo("2026-08-03");
    }

    @Test
    void P001_TEMP_003_DEVICE_TZ_NON_IMPACT() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            JsonNode first = readOnce(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            JsonNode second = readOnce(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
            assertThat(second.path("clocks")).isEqualTo(first.path("clocks"));
            assertThat(second.path("holidays")).isEqualTo(first.path("holidays"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void P001_TEMP_004_DHAKA_UNAVAILABLE() throws Exception {
        JsonNode body = readOnce(NORMAL, availability("UNAVAILABLE", "AVAILABLE"));
        assertThat(body.path("clockState").asText()).isEqualTo("DHAKA_UNAVAILABLE");
        assertUnavailable(body.path("clocks").path("dhaka"));
        assertThat(body.path("clocks").path("beijing").path("availabilityState").asText()).isEqualTo("AVAILABLE");
    }

    @Test
    void P001_TEMP_005_BEIJING_UNAVAILABLE() throws Exception {
        JsonNode body = readOnce(NORMAL, availability("AVAILABLE", "UNAVAILABLE"));
        assertThat(body.path("clockState").asText()).isEqualTo("BEIJING_UNAVAILABLE");
        assertUnavailable(body.path("clocks").path("beijing"));
        assertThat(body.path("clocks").path("dhaka").path("availabilityState").asText()).isEqualTo("AVAILABLE");
    }

    @Test
    void P001_TEMP_006_BOTH_UNAVAILABLE() throws Exception {
        JsonNode body = readOnce(NORMAL, availability("UNAVAILABLE", "UNAVAILABLE"));
        assertThat(body.path("clockState").asText()).isEqualTo("BOTH_UNAVAILABLE");
        assertUnavailable(body.path("clocks").path("dhaka"));
        assertUnavailable(body.path("clocks").path("beijing"));
    }

    @Test
    void P001_TEMP_007_STALE() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalOverviewFixture stale = copy(base, base.referenceInstant(), NORMAL.plusSeconds(31), 30L,
                base.dhakaAvailability(), base.beijingAvailability(), base.allowedHolidaySourceType(),
                base.holidayEvidenceMaxAgeSeconds(), base.china(), base.bangladesh());
        JsonNode body = readOnce(NORMAL, stale);
        assertThat(body.path("clockState").asText()).isEqualTo("STALE");
        assertUnavailable(body.path("clocks").path("dhaka"));
        assertUnavailable(body.path("clocks").path("beijing"));
        assertThat(body.path("retryClass").asText()).isEqualTo("USER_INITIATED_READ_ONLY");
    }

    @Test
    void P001_TEMP_008_RECOVERED_CLIENT_ONCE() throws Exception {
        JsonNode prior = readOnce(NORMAL, availability("UNAVAILABLE", "AVAILABLE"));
        JsonNode current = readOnce(NORMAL.plusSeconds(1), TemporalOverviewService.normalFixture(NORMAL.plusSeconds(1)));
        assertThat(prior.path("clockState").asText()).isEqualTo("DHAKA_UNAVAILABLE");
        assertThat(current.path("clockState").asText()).isEqualTo("BOTH_AVAILABLE");
        assertThat(current.path("clockState").asText()).isNotEqualTo("RECOVERED");
        assertThat(current.path("referenceInstant").asText()).isNotEqualTo(prior.path("referenceInstant").asText());
    }

    @Test
    void P001_TEMP_009_CN_NO_HOLIDAY() throws Exception {
        JsonNode body = readOnce(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        assertNoHoliday(body.path("holidays").path("china"));
    }

    @Test
    void P001_TEMP_010_BD_NO_HOLIDAY() throws Exception {
        JsonNode body = readOnce(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        assertNoHoliday(body.path("holidays").path("bangladesh"));
    }

    @Test
    void P001_TEMP_011_CN_CONFIRMED_HOLIDAY() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision cn = holiday("CONFIRMED_HOLIDAY", "SYN-CN-H-001", "Synthetic CN Holiday", null,
                LocalDate.parse("2026-08-02"), NORMAL);
        JsonNode body = readOnce(NORMAL, withHolidays(base, cn, base.bangladesh()));
        assertThat(body.path("holidays").path("china").path("state").asText()).isEqualTo("CONFIRMED_HOLIDAY");
        assertThat(body.path("holidays").path("china").path("holidayId").asText()).isEqualTo("SYN-CN-H-001");
    }

    @Test
    void P001_TEMP_012_BD_CONFIRMED_HOLIDAY() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision bd = holiday("CONFIRMED_HOLIDAY", "SYN-BD-H-001", "Synthetic BD Holiday", null,
                LocalDate.parse("2026-08-02"), NORMAL);
        JsonNode body = readOnce(NORMAL, withHolidays(base, base.china(), bd));
        assertThat(body.path("holidays").path("bangladesh").path("state").asText())
                .isEqualTo("CONFIRMED_HOLIDAY");
    }

    @Test
    void P001_TEMP_013_PENDING_CONFIRMATION() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision cn = holiday("PENDING_CONFIRMATION", "SYN-CN-PENDING", "Synthetic Expected Day",
                "预计安排，待官方确认", LocalDate.parse("2026-08-02"), NORMAL);
        JsonNode body = readOnce(NORMAL, withHolidays(base, cn, base.bangladesh()));
        assertThat(body.path("holidays").path("china").path("state").asText()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(body.path("holidays").path("china").path("note").asText()).contains("待官方确认");
    }

    @Test
    void P001_TEMP_014_READ_ERROR() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision error = holiday("READ_ERROR", "REVOKED-READ-ERROR", "Revoked Read Error",
                "Revoked note",
                LocalDate.parse("2026-08-02"), NORMAL);
        JsonNode body = readOnce(NORMAL, withHolidays(base, error, base.bangladesh()));
        JsonNode china = body.path("holidays").path("china");
        assertThat(china.path("state").asText()).isEqualTo("READ_ERROR");
        assertNonConclusion(china);
        assertReadErrorMetadataRevoked(china);
        assertThat(body.path("holidays").path("bangladesh").path("state").asText())
                .isEqualTo("NO_HOLIDAY_CONFIRMED");
    }

    @Test
    void P001_TEMP_015_STALE_EXPIRED() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision expired = new TemporalHolidayDecision(LocalDate.parse("2026-08-02"),
                "CONFIRMED_HOLIDAY", "SYN-EXPIRED", "Synthetic Expired", null,
                "LOCAL_SYNTHETIC_CALENDAR", LocalDate.parse("2026-08-02"), NORMAL.minusSeconds(7200),
                NORMAL.minusSeconds(1), "SYN-CALENDAR-V1", NORMAL.minusSeconds(60), "NONE");
        JsonNode body = readOnce(NORMAL, withHolidays(base, expired, base.bangladesh()));
        JsonNode china = body.path("holidays").path("china");
        assertThat(china.path("state").asText()).isEqualTo("STALE_OR_EXPIRED");
        assertNonConclusion(china);
        assertSourceAndVersionRetained(china);
    }

    @Test
    void P001_TEMP_016_UNPUBLISHED() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision unpublished = holiday("UNPUBLISHED", "REVOKED-UNPUBLISHED", "Revoked Unpublished",
                "Revoked note",
                LocalDate.parse("2026-08-02"), NORMAL);
        JsonNode body = readOnce(NORMAL, withHolidays(base, unpublished, base.bangladesh()));
        JsonNode china = body.path("holidays").path("china");
        assertThat(china.path("state").asText()).isEqualTo("UNPUBLISHED");
        assertNonConclusion(china);
        assertSourceAndVersionRetained(china);
    }

    @Test
    void P001_TEMP_017_CRITICAL_UNKNOWN_FAIL_CLOSED() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalOverviewFixture unknown = new TemporalOverviewFixture(true, null, null, null, null,
                base.dhakaAvailability(), base.beijingAvailability(), null, null, null,
                base.china(), base.bangladesh());
        JsonNode body = readOnce(NORMAL, unknown);
        assertThat(body.path("clockState").asText()).isEqualTo("STALE");
        assertThat(body.path("holidays").path("china").path("state").asText()).isEqualTo("READ_ERROR");
        assertThat(body.path("holidays").path("bangladesh").path("state").asText()).isEqualTo("READ_ERROR");
        assertThat(body.path("projectCode").asText()).isEqualTo("TEMPORAL_OVERVIEW_UNKNOWN");
        assertThat(body.path("generatedAt").asText()).isEqualTo(NORMAL.toString());
        assertThat(body.path("timeZoneRuleVersion").asText()).isEqualTo("UNKNOWN_FAIL_CLOSED");
        assertThat(body.path("clockStaleAfterSeconds").asLong()).isEqualTo(1L);
        assertThat(body.path("holidayRuleVersion").asText()).isEqualTo("UNKNOWN_FAIL_CLOSED");
        assertUnavailable(body.path("clocks").path("dhaka"));
        assertUnavailable(body.path("clocks").path("beijing"));
        assertNonConclusion(body.path("holidays").path("china"));
        assertNonConclusion(body.path("holidays").path("bangladesh"));
    }

    @Test
    void invalid_holiday_metadata_fails_closed_without_repeating_invalid_values() throws Exception {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        TemporalHolidayDecision valid = base.china();
        TemporalHolidayDecision coverageMismatch = new TemporalHolidayDecision(valid.localDate(), valid.state(),
                valid.holidayId(), valid.name(), valid.note(), valid.sourceType(), valid.localDate().minusDays(1),
                valid.effectiveFrom(), valid.effectiveTo(), valid.version(), valid.evidenceObservedAt(),
                valid.sourceConflictState());
        TemporalHolidayDecision invertedPeriod = new TemporalHolidayDecision(valid.localDate(), valid.state(),
                valid.holidayId(), valid.name(), valid.note(), valid.sourceType(), valid.sourceCoverageDate(),
                NORMAL.plusSeconds(60), NORMAL.minusSeconds(60), valid.version(), valid.evidenceObservedAt(),
                valid.sourceConflictState());
        TemporalHolidayDecision futureEvidence = new TemporalHolidayDecision(valid.localDate(), valid.state(),
                valid.holidayId(), valid.name(), valid.note(), valid.sourceType(), valid.sourceCoverageDate(),
                valid.effectiveFrom(), valid.effectiveTo(), valid.version(), NORMAL.plusSeconds(1),
                valid.sourceConflictState());
        for (TemporalHolidayDecision invalid : Set.of(coverageMismatch, invertedPeriod, futureEvidence)) {
            JsonNode body = readOnce(NORMAL, withHolidays(base, invalid, base.bangladesh()));
            JsonNode china = body.path("holidays").path("china");
            assertThat(china.path("state").asText()).isEqualTo("READ_ERROR");
            assertNonConclusion(china);
            assertReadErrorMetadataRevoked(china);
        }
    }

    @Test
    void P001_TEMP_018_ANONYMOUS_MINIMAL_ZERO_SIDE_EFFECT() throws Exception {
        install(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        TemporalOverviewSnapshot before = service.snapshotForTest();
        MvcResult result = mvc.perform(get("/api/v1/home/temporal-overview")).andReturn();
        TemporalOverviewSnapshot after = service.snapshotForTest();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertReadOnly(before, after, 1);
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertStrict(body);
        assertThat(body.toString()).doesNotContain("SourceRef", "VerifiedBy", "AuthorizationRef", "EvidenceRef",
                "openid", "phone", "device");
    }

    @Test
    void unknown_query_and_body_are_not_ignored() throws Exception {
        install(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        JsonNode query = json.readTree(mvc.perform(get("/api/v1/home/temporal-overview").param("deviceTimeZone", "UTC"))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(query.path("projectCode").asText()).isEqualTo("TEMPORAL_OVERVIEW_UNKNOWN");
        install(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        JsonNode body = json.readTree(mvc.perform(get("/api/v1/home/temporal-overview")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clientTime\":\"2026-08-02T00:00:00Z\"}"))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(body.path("projectCode").asText()).isEqualTo("TEMPORAL_OVERVIEW_UNKNOWN");
    }

    @Test
    void prohibited_identity_device_and_client_time_headers_fail_closed_without_writes() throws Exception {
        for (String header : Set.of("X-Project-Subject-Ref", "X-Actor-Ref", "X-HZM-Test-Access-Token",
                "X-Device-TimeZone", "X-Client-Time")) {
            install(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
            TemporalOverviewSnapshot before = service.snapshotForTest();
            MvcResult result = mvc.perform(get("/api/v1/home/temporal-overview").header(header, "synthetic-value"))
                    .andReturn();
            TemporalOverviewSnapshot after = service.snapshotForTest();
            JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
            assertStrict(body);
            assertThat(body.path("projectCode").asText()).isEqualTo("TEMPORAL_OVERVIEW_UNKNOWN");
            assertThat(body.path("clockState").asText()).isEqualTo("STALE");
            assertUnavailable(body.path("clocks").path("dhaka"));
            assertUnavailable(body.path("clocks").path("beijing"));
            assertNonConclusion(body.path("holidays").path("china"));
            assertNonConclusion(body.path("holidays").path("bangladesh"));
            assertReadOnly(before, after, 1);
        }
    }

    @Test
    void standard_host_and_user_agent_headers_remain_allowed() throws Exception {
        install(NORMAL, TemporalOverviewService.normalFixture(NORMAL));
        TemporalOverviewSnapshot before = service.snapshotForTest();
        MvcResult result = mvc.perform(get("/api/v1/home/temporal-overview")
                        .header("Host", "localhost").header("User-Agent", "MockMvc-Contract-Test"))
                .andReturn();
        TemporalOverviewSnapshot after = service.snapshotForTest();
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("projectCode").asText()).isEqualTo("TEMPORAL_OVERVIEW_READY");
        assertThat(body.path("clockState").asText()).isEqualTo("BOTH_AVAILABLE");
        assertReadOnly(before, after, 1);
    }

    @Test
    void observer_detects_each_sensitive_boundary() {
        for (TemporalOverviewSideEffectProbe.Boundary boundary : TemporalOverviewSideEffectProbe.Boundary.values()) {
            sideEffects.resetForTest();
            Map<String, Long> before = sideEffects.snapshot();
            sideEffects.observeForSensitivityTest(boundary);
            Map<String, Long> after = sideEffects.snapshot();
            assertThat(after.get(boundary.name()) - before.get(boundary.name())).isEqualTo(1L);
        }
    }

    private JsonNode readOnce(Instant reference, TemporalOverviewFixture fixture) throws Exception {
        install(reference, fixture);
        TemporalOverviewSnapshot before = service.snapshotForTest();
        MvcResult result = mvc.perform(get("/api/v1/home/temporal-overview")).andReturn();
        TemporalOverviewSnapshot after = service.snapshotForTest();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertReadOnly(before, after, 1);
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertStrict(body);
        assertThat(body.path("referenceInstant").asText()).isEqualTo(reference.toString());
        return body;
    }

    private void install(Instant reference, TemporalOverviewFixture fixture) {
        when(clock.instant()).thenReturn(reference);
        service.installFixtureForTest(fixture);
    }

    private static void assertStrict(JsonNode body) {
        assertThat(fields(body)).isEqualTo(TOP_FIELDS);
        assertThat(body.path("schemaVersion").asText()).isEqualTo("TEMPORAL_OVERVIEW_V1");
        assertThat(body.path("requestRef").asText()).isNotBlank();
        assertThat(fields(body.path("clocks"))).containsExactlyInAnyOrder("dhaka", "beijing");
        assertThat(fields(body.path("holidays"))).containsExactlyInAnyOrder("china", "bangladesh");
        body.path("clocks").forEach(value -> assertThat(fields(value)).isEqualTo(CLOCK_FIELDS));
        body.path("holidays").forEach(value -> assertThat(fields(value)).isEqualTo(HOLIDAY_FIELDS));
        assertThat(body.path("clockState").asText()).isNotIn("LOADING", "RECOVERED");
        body.path("holidays").forEach(value -> assertThat(value.path("state").asText()).isNotEqualTo("LOADING"));
        assertFrontStrictMapperShape(body);
    }

    private static void assertFrontStrictMapperShape(JsonNode body) {
        assertThat(body.path("requestRef").isTextual()).isTrue();
        assertThat(body.path("requestRef").asText()).isNotBlank();
        assertThat(body.path("projectCode").isTextual()).isTrue();
        assertThat(body.path("projectCode").asText()).isNotBlank();
        assertThat(body.path("referenceInstant").isTextual()).isTrue();
        assertThat(body.path("generatedAt").isTextual()).isTrue();
        Instant reference = Instant.parse(body.path("referenceInstant").asText());
        Instant generated = Instant.parse(body.path("generatedAt").asText());
        assertThat(generated).isAfterOrEqualTo(reference);
        assertThat(body.path("timeZoneRuleVersion").isTextual()).isTrue();
        assertThat(body.path("timeZoneRuleVersion").asText()).isNotBlank();
        assertThat(body.path("clockStaleAfterSeconds").isIntegralNumber()).isTrue();
        assertThat(body.path("clockStaleAfterSeconds").asLong()).isBetween(1L, 9_007_199_254_740_991L);
        assertThat(body.path("holidayRuleVersion").isTextual()).isTrue();
        assertThat(body.path("holidayRuleVersion").asText()).isNotBlank();
        assertThat(body.path("retryClass").asText()).isIn("NONE", "USER_INITIATED_READ_ONLY");
        for (JsonNode holiday : body.path("holidays")) {
            if (!Set.of("NO_HOLIDAY_CONFIRMED", "CONFIRMED_HOLIDAY", "PENDING_CONFIRMATION")
                    .contains(holiday.path("state").asText())) {
                assertNonConclusion(holiday);
            }
        }
    }

    private static void assertReadOnly(TemporalOverviewSnapshot before, TemporalOverviewSnapshot after,
                                       long queryDelta) {
        assertThat(after.fixtureRevision()).isEqualTo(before.fixtureRevision());
        assertThat(after.fixtureDigest()).isEqualTo(before.fixtureDigest());
        before.counters().forEach((boundary, count) -> assertThat(after.counters().get(boundary) - count)
                .as(boundary).isEqualTo(boundary.equals("QueryCall") ? queryDelta : 0L));
    }

    private static void assertUnavailable(JsonNode clock) {
        assertThat(clock.path("availabilityState").asText()).isEqualTo("UNAVAILABLE");
        assertThat(clock.path("localDate").isNull()).isTrue();
        assertThat(clock.path("localTime").isNull()).isTrue();
    }

    private static void assertNoHoliday(JsonNode holiday) {
        assertThat(holiday.path("state").asText()).isEqualTo("NO_HOLIDAY_CONFIRMED");
        assertThat(holiday.path("holidayId").isNull()).isTrue();
        assertThat(holiday.path("name").isNull()).isTrue();
        assertThat(holiday.path("note").isNull()).isTrue();
        assertThat(holiday.path("sourceCoverageDate").asText()).isEqualTo(holiday.path("localDate").asText());
    }

    private static void assertNonConclusion(JsonNode holiday) {
        assertThat(holiday.path("holidayId").isNull()).isTrue();
        assertThat(holiday.path("name").isNull()).isTrue();
        assertThat(holiday.path("note").isNull()).isTrue();
    }

    private static void assertSourceAndVersionRetained(JsonNode holiday) {
        assertThat(holiday.path("sourceType").asText()).isEqualTo("LOCAL_SYNTHETIC_CALENDAR");
        assertThat(holiday.path("sourceCoverageDate").asText()).isEqualTo(holiday.path("localDate").asText());
        assertThat(holiday.path("version").asText()).isEqualTo("SYN-CALENDAR-V1");
    }

    private static void assertReadErrorMetadataRevoked(JsonNode holiday) {
        assertThat(holiday.path("sourceType").isNull()).isTrue();
        assertThat(holiday.path("sourceCoverageDate").isNull()).isTrue();
        assertThat(holiday.path("effectiveFrom").isNull()).isTrue();
        assertThat(holiday.path("effectiveTo").isNull()).isTrue();
        assertThat(holiday.path("version").isNull()).isTrue();
    }

    private static TemporalOverviewFixture availability(String dhaka, String beijing) {
        TemporalOverviewFixture base = TemporalOverviewService.normalFixture(NORMAL);
        return copy(base, base.referenceInstant(), base.generatedAt(), base.clockStaleAfterSeconds(), dhaka, beijing,
                base.allowedHolidaySourceType(), base.holidayEvidenceMaxAgeSeconds(), base.china(), base.bangladesh());
    }

    private static TemporalOverviewFixture withHolidays(TemporalOverviewFixture base, TemporalHolidayDecision china,
                                                        TemporalHolidayDecision bangladesh) {
        return copy(base, base.referenceInstant(), base.generatedAt(), base.clockStaleAfterSeconds(),
                base.dhakaAvailability(), base.beijingAvailability(), base.allowedHolidaySourceType(),
                base.holidayEvidenceMaxAgeSeconds(), china, bangladesh);
    }

    private static TemporalOverviewFixture copy(TemporalOverviewFixture base, Instant reference, Instant generated,
                                                Long staleAfter, String dhaka, String beijing, String sourceType,
                                                Long evidenceMaxAge, TemporalHolidayDecision china,
                                                TemporalHolidayDecision bangladesh) {
        return new TemporalOverviewFixture(base.syntheticMarker(), reference, generated, base.timeZoneRuleVersion(),
                staleAfter, dhaka, beijing, base.holidayRuleVersion(), sourceType, evidenceMaxAge, china, bangladesh);
    }

    private static TemporalHolidayDecision holiday(String state, String id, String name, String note,
                                                    LocalDate localDate, Instant reference) {
        return new TemporalHolidayDecision(localDate, state, id, name, note, "LOCAL_SYNTHETIC_CALENDAR", localDate,
                reference.minusSeconds(3600), reference.plusSeconds(3600), "SYN-CALENDAR-V1",
                reference.minusSeconds(60), "NONE");
    }

    private static Set<String> fields(JsonNode value) {
        Set<String> fields = new HashSet<>();
        value.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
