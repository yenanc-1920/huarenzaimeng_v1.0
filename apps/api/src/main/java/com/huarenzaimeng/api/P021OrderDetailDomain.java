package com.huarenzaimeng.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

final class P021OrderDetailDomain {
    static final String ENVIRONMENT = "LOCAL_SYNTHETIC";
    static final String REALITY_LEVEL = "NOT_VERIFIED";
    static final String PROJECT_CODE_READ = "ORDER_DETAIL_READ";
    static final String PROJECT_CODE_UNAVAILABLE = "ORDER_DETAIL_NOT_AVAILABLE";
    static final String PROJECT_CODE_ERROR = "ORDER_DETAIL_READ_ERROR";

    private P021OrderDetailDomain() {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    record Response(String requestRef, String outcome, String projectCode, String resourceRef,
                    Long aggregateVersion, Projection currentProjection, String retryClass,
                    Instant nextPollAt) {
        @JsonAnySetter void rejectUnknown(String name, Object value) { throw new IllegalArgumentException(name); }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record Projection(String orderRef, long aggregateVersion, long projectionVersion,
                      UserOrderStateCode stateCode, PriceSnapshotSummary priceSnapshotSummary,
                      List<String> confirmedItems, List<String> unknownItems, String responsibilityCode,
                      Instant updatedAt, Instant nextReviewPoint, List<TimelineItem> timeline,
                      List<AllowedAction> allowedActions, String supportRef) {
        @JsonAnySetter void rejectUnknown(String name, Object value) { throw new IllegalArgumentException(name); }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record PriceSnapshotSummary(String priceSnapshotRef, long totalMinor, String currency,
                                String displayVersion, String maskedTarget, String brandDisplayName,
                                String productDisplayName, String targetValueDisplay,
                                String targetCurrency, Instant validUntil) {
        @JsonAnySetter void rejectUnknown(String name, Object value) { throw new IllegalArgumentException(name); }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record TimelineItem(String timelineItemRef, long sequence, long projectionVersion,
                        UserOrderStateCode stateCode, Instant occurredAt, String userMessageCode) {
        @JsonAnySetter void rejectUnknown(String name, Object value) { throw new IllegalArgumentException(name); }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record AllowedAction(String actionCode, boolean enabled, long actionBindingVersion,
                         String supportRef) {
        @JsonAnySetter void rejectUnknown(String name, Object value) { throw new IllegalArgumentException(name); }
    }

    record Fixture(boolean syntheticMarker, String environment, String realityEvidenceLevel,
                   String projectSubjectRef, ProjectSessionRole sessionRole, long sessionVersion,
                   String authorizationSetRef, String authorizationEvidenceVersion,
                   List<String> authorizedOrderRefs, Projection projection,
                   String priceSnapshotDigest, String fixtureSchemaVersion, String fixtureDigest) {}
}
