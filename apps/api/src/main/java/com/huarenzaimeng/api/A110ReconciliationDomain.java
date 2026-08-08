package com.huarenzaimeng.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record A110FactSummary(
        String factState,
        Long amountMinor,
        String currency,
        Instant occurredAt,
        Instant observedAt
) {}

record A110TimelineEntry(
        String factCode,
        String factState,
        Instant occurredAt,
        Instant observedAt
) {}

record A110FinItem(
        String reconciliationRef,
        String orderRef,
        String supportRef,
        Map<String, A110FactSummary> factSummaries,
        List<String> differenceCategories,
        String ageState,
        String responsibilityCode,
        List<A110TimelineEntry> timeline,
        Instant nextReviewPoint,
        Instant updatedAt,
        Long projectionVersion,
        String displayVersion
) {}

record A110CsItem(
        String reconciliationRef,
        String supportRef,
        String orderRef,
        String maskedSubjectSummary,
        String userFacingSummary,
        List<String> confirmedItems,
        List<String> unconfirmedItems,
        String responsibilityCode,
        Instant nextReviewPoint,
        Instant updatedAt,
        Long projectionVersion
) {}

record A110ReconciliationFixture(
        Boolean syntheticMarker,
        String environment,
        String accountSubjectRef,
        String role,
        String roleBindingVersion,
        String scope,
        String authorizationDecisionVersion,
        String authorizationState,
        Long projectionVersion,
        String readOutcome,
        List<?> items
) {}

record A110ReconciliationResponse(
        String requestRef,
        String viewState,
        String projectCode,
        String schemaVersion,
        String roleProjection,
        String roleBindingVersion,
        String authorizationDecisionVersion,
        Long projectionVersion,
        List<?> items,
        List<String> allowedActions,
        String retryClass
) {}

record A110ReconciliationSnapshot(long fixtureRevision, Map<String, Long> counters) {}
