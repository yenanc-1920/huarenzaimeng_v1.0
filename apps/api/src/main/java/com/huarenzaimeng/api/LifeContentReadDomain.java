package com.huarenzaimeng.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

enum LifeContentViewState { READY, EMPTY, UNDER_REVIEW, EXPIRED, REMOVED, UNKNOWN, ERROR }

enum LifeContentListReadCompleteness { COMPLETE, UNKNOWN, ERROR }

record LifeContentFixture(
        String fixtureId,
        String fixtureVersion,
        String environment,
        boolean syntheticMarker,
        String contentRef,
        String contentVersion,
        String category,
        String title,
        String summary,
        String body,
        String coverState,
        String coverRef,
        String sourceRef,
        String sourceType,
        String rightsEvidenceRef,
        String rightsEvidenceState,
        String jurisdiction,
        String applicableAudience,
        Instant publishedAt,
        Instant updatedAt,
        String verifiedBy,
        Instant verifiedAt,
        String reviewState,
        Instant effectiveFrom,
        Instant effectiveTo,
        String evidenceVersion,
        String visibilityRuleVersion,
        String freshnessRuleVersion,
        Long freshnessWindowSeconds,
        Instant referenceInstant,
        String sourceConflictState,
        String rightsConflictState,
        String listReadCompleteness,
        String realityEvidenceLevel,
        Integer productionPublicationEligibility
) {}

record LifeContentSummary(
        String contentRef,
        String contentVersion,
        String category,
        String title,
        String summary,
        String sourceType,
        String jurisdiction,
        String applicableAudience,
        Instant publishedAt,
        Instant updatedAt,
        Instant effectiveFrom,
        Instant effectiveTo,
        String freshnessState,
        String coverState,
        String coverRef
) {}

record LifeContentDetail(
        String contentRef,
        String contentVersion,
        String category,
        String title,
        String summary,
        String sourceType,
        String jurisdiction,
        String applicableAudience,
        Instant publishedAt,
        Instant updatedAt,
        Instant effectiveFrom,
        Instant effectiveTo,
        String freshnessState,
        String coverState,
        String coverRef,
        String body
) {}

record LifeContentListResponse(
        String requestRef,
        String viewState,
        String projectCode,
        String schemaVersion,
        String visibilityRuleVersion,
        List<LifeContentSummary> items,
        String retryClass,
        Instant nextReadAt
) {}

record LifeContentDetailResponse(
        String requestRef,
        String viewState,
        String projectCode,
        String schemaVersion,
        String visibilityRuleVersion,
        String contentRef,
        String contentVersion,
        LifeContentDetail item,
        String retryClass,
        Instant nextReadAt
) {}

record LifeContentReadSnapshot(
        long fixtureRevision,
        int fixtureCount,
        String fixtureDigest,
        Map<String, Long> counters
) {}
