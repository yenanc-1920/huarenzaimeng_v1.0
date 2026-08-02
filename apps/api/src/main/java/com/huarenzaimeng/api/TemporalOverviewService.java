package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile({"mock", "test"})
@ConditionalOnProperty(name = "hz.temporal-overview.mode", havingValue = "local-synthetic")
class TemporalOverviewService {
    static final String SCHEMA_VERSION = "TEMPORAL_OVERVIEW_V1";
    static final ZoneId DHAKA_ZONE = ZoneId.of("Asia/Dhaka");
    static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L;
    private static final String FAIL_CLOSED_RULE_VERSION = "UNKNOWN_FAIL_CLOSED";
    private static final long FAIL_CLOSED_STALE_AFTER_SECONDS = 1L;
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> AVAILABLE_STATES = List.of("AVAILABLE", "UNAVAILABLE");
    private static final List<String> SERVER_HOLIDAY_STATES = List.of(
            "NO_HOLIDAY_CONFIRMED", "CONFIRMED_HOLIDAY", "PENDING_CONFIRMATION",
            "READ_ERROR", "STALE_OR_EXPIRED", "UNPUBLISHED");

    private final Clock clock;
    private final TemporalOverviewSideEffectProbe sideEffects;
    private final AtomicLong requestSequence = new AtomicLong();
    private TemporalOverviewFixture fixture;
    private long fixtureRevision;

    TemporalOverviewService(Clock clock, TemporalOverviewSideEffectProbe sideEffects) {
        this.clock = clock;
        this.sideEffects = sideEffects;
    }

    synchronized TemporalOverviewResponse read() {
        sideEffects.observeQuery();
        Instant capturedReferenceInstant = clock.instant();
        TemporalOverviewFixture selected = fixture == null ? normalFixture(capturedReferenceInstant) : fixture;
        return project(capturedReferenceInstant, selected);
    }

    synchronized TemporalOverviewResponse invalidRequest() {
        sideEffects.observeQuery();
        Instant capturedReferenceInstant = clock.instant();
        TemporalOverviewFixture selected = fixture == null ? normalFixture(capturedReferenceInstant) : fixture;
        return failedClosed(capturedReferenceInstant, selected, "TEMPORAL_OVERVIEW_UNKNOWN");
    }

    synchronized void installFixtureForTest(TemporalOverviewFixture installed) {
        fixture = installed;
        fixtureRevision++;
        requestSequence.set(0L);
        sideEffects.resetForTest();
    }

    synchronized TemporalOverviewSnapshot snapshotForTest() {
        return new TemporalOverviewSnapshot(fixtureRevision,
                fixture == null ? "RUNTIME_PER_REQUEST_FIXTURE" : fixtureDigest(fixture), sideEffects.snapshot());
    }

    private TemporalOverviewResponse project(Instant referenceInstant, TemporalOverviewFixture value) {
        if (!criticalShapeValid(referenceInstant, value)) {
            return failedClosed(referenceInstant, value, "TEMPORAL_OVERVIEW_UNKNOWN");
        }
        long generationLag;
        try {
            generationLag = Duration.between(referenceInstant, value.generatedAt()).getSeconds();
        } catch (DateTimeException | ArithmeticException error) {
            return failedClosed(referenceInstant, value, "TEMPORAL_OVERVIEW_UNKNOWN");
        }
        boolean stale = generationLag > value.clockStaleAfterSeconds();
        Map<String, TemporalClockView> clocks = clockViews(referenceInstant, value, stale);
        String clockState = clockState(value, stale);
        Map<String, TemporalHolidayView> holidays = new LinkedHashMap<>();
        holidays.put("china", holidayView("CN", BEIJING_ZONE, referenceInstant, value, value.china()));
        holidays.put("bangladesh", holidayView("BD", DHAKA_ZONE, referenceInstant, value, value.bangladesh()));
        boolean retriable = stale || holidays.values().stream().anyMatch(item -> "READ_ERROR".equals(item.state()));
        String projectCode = stale ? "TEMPORAL_OVERVIEW_STALE" : retriable
                ? "TEMPORAL_OVERVIEW_READ_ERROR" : "TEMPORAL_OVERVIEW_READY";
        return response(projectCode, referenceInstant, value.generatedAt(), value.timeZoneRuleVersion(),
                value.clockStaleAfterSeconds(), clockState, clocks, value.holidayRuleVersion(), holidays,
                retriable ? "USER_INITIATED_READ_ONLY" : "NONE");
    }

    private boolean criticalShapeValid(Instant captured, TemporalOverviewFixture value) {
        return value != null && Boolean.TRUE.equals(value.syntheticMarker())
                && value.referenceInstant() != null && value.referenceInstant().equals(captured)
                && value.generatedAt() != null && !value.generatedAt().isBefore(captured)
                && present(value.timeZoneRuleVersion())
                && value.clockStaleAfterSeconds() != null && value.clockStaleAfterSeconds() > 0
                && value.clockStaleAfterSeconds() <= MAX_SAFE_JSON_INTEGER
                && AVAILABLE_STATES.contains(value.dhakaAvailability())
                && AVAILABLE_STATES.contains(value.beijingAvailability())
                && present(value.holidayRuleVersion())
                && present(value.allowedHolidaySourceType())
                && value.holidayEvidenceMaxAgeSeconds() != null && value.holidayEvidenceMaxAgeSeconds() > 0;
    }

    private Map<String, TemporalClockView> clockViews(Instant referenceInstant, TemporalOverviewFixture value,
                                                       boolean stale) {
        Map<String, TemporalClockView> result = new LinkedHashMap<>();
        result.put("dhaka", clockView("DHAKA", "达卡", DHAKA_ZONE, referenceInstant,
                !stale && "AVAILABLE".equals(value.dhakaAvailability())));
        result.put("beijing", clockView("BEIJING", "北京", BEIJING_ZONE, referenceInstant,
                !stale && "AVAILABLE".equals(value.beijingAvailability())));
        return Map.copyOf(result);
    }

    private static TemporalClockView clockView(String cityCode, String displayName, ZoneId zone,
                                                Instant referenceInstant, boolean available) {
        if (!available) {
            return new TemporalClockView(cityCode, displayName, zone.getId(), null, null,
                    TemporalAvailabilityState.UNAVAILABLE.name());
        }
        var local = referenceInstant.atZone(zone);
        return new TemporalClockView(cityCode, displayName, zone.getId(), local.toLocalDate(),
                LOCAL_TIME.format(local), TemporalAvailabilityState.AVAILABLE.name());
    }

    private static String clockState(TemporalOverviewFixture value, boolean stale) {
        if (stale) return TemporalClockState.STALE.name();
        boolean dhaka = "AVAILABLE".equals(value.dhakaAvailability());
        boolean beijing = "AVAILABLE".equals(value.beijingAvailability());
        if (dhaka && beijing) return TemporalClockState.BOTH_AVAILABLE.name();
        if (!dhaka && beijing) return TemporalClockState.DHAKA_UNAVAILABLE.name();
        if (dhaka) return TemporalClockState.BEIJING_UNAVAILABLE.name();
        return TemporalClockState.BOTH_UNAVAILABLE.name();
    }

    private TemporalHolidayView holidayView(String countryCode, ZoneId zone, Instant referenceInstant,
                                             TemporalOverviewFixture root, TemporalHolidayDecision decision) {
        LocalDate localDate = referenceInstant.atZone(zone).toLocalDate();
        if (decision == null || !SERVER_HOLIDAY_STATES.contains(decision.state())
                || decision.localDate() == null || !localDate.equals(decision.localDate())) {
            return holidayReadError(countryCode, localDate, decision);
        }
        if (!present(decision.sourceType()) || !root.allowedHolidaySourceType().equals(decision.sourceType())
                || !present(decision.version()) || decision.sourceCoverageDate() == null
                || !localDate.equals(decision.sourceCoverageDate())
                || decision.effectiveFrom() == null || decision.effectiveTo() == null
                || decision.effectiveTo().isBefore(decision.effectiveFrom())
                || decision.evidenceObservedAt() == null
                || decision.evidenceObservedAt().isAfter(referenceInstant)
                || !"NONE".equals(decision.sourceConflictState())) {
            return holidayReadError(countryCode, localDate, decision);
        }
        if ("READ_ERROR".equals(decision.state())) return holidayReadError(countryCode, localDate, decision);
        if ("UNPUBLISHED".equals(decision.state())) {
            return holidayWithoutConclusion(countryCode, localDate, "UNPUBLISHED", decision);
        }
        if ("STALE_OR_EXPIRED".equals(decision.state())) {
            return holidayWithoutConclusion(countryCode, localDate, "STALE_OR_EXPIRED", decision);
        }
        if (referenceInstant.isBefore(decision.effectiveFrom()) || referenceInstant.isAfter(decision.effectiveTo())
                || evidenceAgeExceeded(referenceInstant, decision.evidenceObservedAt(), root.holidayEvidenceMaxAgeSeconds())) {
            return holidayWithoutConclusion(countryCode, localDate, "STALE_OR_EXPIRED", decision);
        }
        if ("NO_HOLIDAY_CONFIRMED".equals(decision.state())) {
            if (decision.holidayId() != null || decision.name() != null || decision.note() != null) {
                return holidayReadError(countryCode, localDate, decision);
            }
            return holiday(countryCode, localDate, decision.state(), decision);
        }
        if ("CONFIRMED_HOLIDAY".equals(decision.state())) {
            if (!present(decision.holidayId()) || !present(decision.name())) {
                return holidayReadError(countryCode, localDate, decision);
            }
            return holiday(countryCode, localDate, decision.state(), decision);
        }
        if ("PENDING_CONFIRMATION".equals(decision.state())) {
            boolean pendingSemantics = present(decision.holidayId()) && present(decision.name())
                    && present(decision.note()) && (decision.note().contains("待官方确认")
                    || decision.note().contains("预计"));
            return pendingSemantics ? holiday(countryCode, localDate, decision.state(), decision)
                    : holidayReadError(countryCode, localDate, decision);
        }
        return holidayReadError(countryCode, localDate, decision);
    }

    private static boolean evidenceAgeExceeded(Instant reference, Instant observed, long maxAgeSeconds) {
        if (observed.isAfter(reference)) return true;
        try {
            return Duration.between(observed, reference).getSeconds() > maxAgeSeconds;
        } catch (DateTimeException | ArithmeticException error) {
            return true;
        }
    }

    private static TemporalHolidayView holiday(String countryCode, LocalDate localDate, String state,
                                                TemporalHolidayDecision decision) {
        return new TemporalHolidayView(countryCode, localDate, state, decision.holidayId(), decision.name(),
                decision.note(), decision.sourceType(), decision.sourceCoverageDate(), decision.effectiveFrom(),
                decision.effectiveTo(), decision.version());
    }

    private static TemporalHolidayView holidayWithoutConclusion(String countryCode, LocalDate localDate, String state,
                                                                 TemporalHolidayDecision decision) {
        return new TemporalHolidayView(countryCode, localDate, state, null, null, null, decision.sourceType(),
                decision.sourceCoverageDate(), decision.effectiveFrom(), decision.effectiveTo(), decision.version());
    }

    private static TemporalHolidayView holidayReadError(String countryCode, LocalDate localDate,
                                                         TemporalHolidayDecision decision) {
        return new TemporalHolidayView(countryCode, localDate, TemporalHolidayState.READ_ERROR.name(),
                null, null, null, null, null, null, null, null);
    }

    private TemporalOverviewResponse failedClosed(Instant captured, TemporalOverviewFixture value, String code) {
        Map<String, TemporalClockView> clocks = new LinkedHashMap<>();
        clocks.put("dhaka", clockView("DHAKA", "达卡", DHAKA_ZONE, captured, false));
        clocks.put("beijing", clockView("BEIJING", "北京", BEIJING_ZONE, captured, false));
        Map<String, TemporalHolidayView> holidays = new LinkedHashMap<>();
        holidays.put("china", holidayReadError("CN", captured.atZone(BEIJING_ZONE).toLocalDate(),
                value == null ? null : value.china()));
        holidays.put("bangladesh", holidayReadError("BD", captured.atZone(DHAKA_ZONE).toLocalDate(),
                value == null ? null : value.bangladesh()));
        Instant safeGeneratedAt = value != null && value.generatedAt() != null
                && !value.generatedAt().isBefore(captured) ? value.generatedAt() : captured;
        String safeTimeZoneRuleVersion = value != null && present(value.timeZoneRuleVersion())
                ? value.timeZoneRuleVersion() : FAIL_CLOSED_RULE_VERSION;
        Long safeStaleAfterSeconds = value != null && value.clockStaleAfterSeconds() != null
                && value.clockStaleAfterSeconds() > 0 && value.clockStaleAfterSeconds() <= MAX_SAFE_JSON_INTEGER
                ? value.clockStaleAfterSeconds() : FAIL_CLOSED_STALE_AFTER_SECONDS;
        String safeHolidayRuleVersion = value != null && present(value.holidayRuleVersion())
                ? value.holidayRuleVersion() : FAIL_CLOSED_RULE_VERSION;
        return response(code, captured, safeGeneratedAt, safeTimeZoneRuleVersion, safeStaleAfterSeconds,
                TemporalClockState.STALE.name(), clocks, safeHolidayRuleVersion, holidays,
                "USER_INITIATED_READ_ONLY");
    }

    private TemporalOverviewResponse response(String projectCode, Instant referenceInstant, Instant generatedAt,
                                              String timeZoneRuleVersion, Long clockStaleAfterSeconds,
                                              String clockState, Map<String, TemporalClockView> clocks,
                                              String holidayRuleVersion, Map<String, TemporalHolidayView> holidays,
                                              String retryClass) {
        return new TemporalOverviewResponse("SYN-TEMP-" + requestSequence.incrementAndGet(), projectCode,
                SCHEMA_VERSION, referenceInstant, generatedAt, timeZoneRuleVersion, clockStaleAfterSeconds,
                clockState, Map.copyOf(clocks), holidayRuleVersion, Map.copyOf(holidays), retryClass);
    }

    static TemporalOverviewFixture normalFixture(Instant referenceInstant) {
        Instant generatedAt = referenceInstant.plusMillis(1);
        LocalDate cnDate = referenceInstant.atZone(BEIJING_ZONE).toLocalDate();
        LocalDate bdDate = referenceInstant.atZone(DHAKA_ZONE).toLocalDate();
        return new TemporalOverviewFixture(true, referenceInstant, generatedAt, "JDK17-IANA-2025A", 30L,
                "AVAILABLE", "AVAILABLE", "SYN-HOLIDAY-RULE-V1", "LOCAL_SYNTHETIC_CALENDAR", 86_400L,
                noHoliday(cnDate, referenceInstant), noHoliday(bdDate, referenceInstant));
    }

    static TemporalHolidayDecision noHoliday(LocalDate localDate, Instant referenceInstant) {
        return new TemporalHolidayDecision(localDate, "NO_HOLIDAY_CONFIRMED", null, null, null,
                "LOCAL_SYNTHETIC_CALENDAR", localDate, referenceInstant.minusSeconds(3600),
                referenceInstant.plusSeconds(86_400), "SYN-CALENDAR-V1", referenceInstant.minusSeconds(60), "NONE");
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }

    private static String fixtureDigest(TemporalOverviewFixture value) {
        try {
            byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
