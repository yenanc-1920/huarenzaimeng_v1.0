package com.huarenzaimeng.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

enum TemporalClockState {
    LOADING, BOTH_AVAILABLE, DHAKA_UNAVAILABLE, BEIJING_UNAVAILABLE, BOTH_UNAVAILABLE, STALE, RECOVERED
}

enum TemporalAvailabilityState { AVAILABLE, UNAVAILABLE }

enum TemporalHolidayState {
    LOADING, NO_HOLIDAY_CONFIRMED, CONFIRMED_HOLIDAY, PENDING_CONFIRMATION,
    READ_ERROR, STALE_OR_EXPIRED, UNPUBLISHED
}

record TemporalHolidayDecision(
        LocalDate localDate,
        String state,
        String holidayId,
        String name,
        String note,
        String sourceType,
        LocalDate sourceCoverageDate,
        Instant effectiveFrom,
        Instant effectiveTo,
        String version,
        Instant evidenceObservedAt,
        String sourceConflictState
) {}

record TemporalOverviewFixture(
        Boolean syntheticMarker,
        Instant referenceInstant,
        Instant generatedAt,
        String timeZoneRuleVersion,
        Long clockStaleAfterSeconds,
        String dhakaAvailability,
        String beijingAvailability,
        String holidayRuleVersion,
        String allowedHolidaySourceType,
        Long holidayEvidenceMaxAgeSeconds,
        TemporalHolidayDecision china,
        TemporalHolidayDecision bangladesh
) {}

record TemporalClockView(
        String cityCode,
        String displayName,
        String zoneId,
        LocalDate localDate,
        String localTime,
        String availabilityState
) {}

record TemporalHolidayView(
        String countryCode,
        LocalDate localDate,
        String state,
        String holidayId,
        String name,
        String note,
        String sourceType,
        LocalDate sourceCoverageDate,
        Instant effectiveFrom,
        Instant effectiveTo,
        String version
) {}

record TemporalOverviewResponse(
        String requestRef,
        String projectCode,
        String schemaVersion,
        Instant referenceInstant,
        Instant generatedAt,
        String timeZoneRuleVersion,
        Long clockStaleAfterSeconds,
        String clockState,
        Map<String, TemporalClockView> clocks,
        String holidayRuleVersion,
        Map<String, TemporalHolidayView> holidays,
        String retryClass
) {}

record TemporalOverviewSnapshot(
        long fixtureRevision,
        String fixtureDigest,
        Map<String, Long> counters
) {}
