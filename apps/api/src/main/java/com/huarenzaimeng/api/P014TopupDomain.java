package com.huarenzaimeng.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

final class P014TopupDomain {
    static final String ENVIRONMENT = "LOCAL_SYNTHETIC";
    static final String REALITY_LEVEL = "NOT_VERIFIED";
    static final String PRECONDITION = "TOPUP_INTENT_MUST_NOT_EXIST";
    static final String CREATE_ACTION = "CREATE_LOCAL_SYNTHETIC_TOPUP";
    static final String SCHEMA = "P014_TOPUP_PROGRESS_V1";
    static final String ORDER_STATE = "PAID_AWAITING_TOPUP";
    private P014TopupDomain() {}

    enum UnknownAgeDecision { WITHIN_LOCAL_WINDOW, LONG_RUNNING, UNKNOWN }
    enum ResultState { FOUND, REJECTED, UNKNOWN }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record CreateRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                         @NotBlank String topupCreationPrecondition, @Min(1) long sessionVersion,
                         @NotBlank String authorizationSetRef, @Min(1) long expectedProjectionVersion,
                         @Min(1) long expectedAggregateVersion) {}

    record Response(String requestRef, String outcome, String projectCode, String resourceRef,
                    Long aggregateVersion, Projection currentProjection, String retryClass, Instant nextPollAt) {}

    record Projection(String orderRef, String stateCode, String schemaVersion, long projectionVersion,
                      long aggregateVersion, PriceSnapshotSummary priceSnapshotSummary, String topupIntentRef,
                      String dispatchIntentRef, String paymentConfirmationState, String upstreamDebitState,
                      String deliveryState, String accountingClosureState, ProgressSummary progressSummary,
                      List<Fact> factTimeline, List<AllowedAction> allowedActions) {}

    record PriceSnapshotSummary(String priceSnapshotRef, long totalMinor, String currency, String displayVersion,
                                String maskedRecipientNumber, String operatorDisplayName, String productDisplayName,
                                long targetFaceValueMinor, String targetCurrency, Instant expiresAt) {}
    record ProgressSummary(String userMessageCode, List<String> confirmedItems, List<String> unknownItems,
                           String responsibilityCode, String supportRef, Instant updatedAt, Instant nextReviewPoint) {}
    record Fact(String factCode, String state, Instant occurredAt, Instant observedAt) {}
    record AllowedAction(String actionCode, boolean enabled, long expectedProjectionVersion,
                         String actionBindingVersion) {}

    /** Explicit LOCAL_SYNTHETIC/NO_DEFAULT input. It never represents production identity or reality. */
    record Fixture(String environment, String realityEvidenceLevel, String orderRef, String projectSubjectRef,
                   String sessionRef, String sessionRole, long sessionVersion, String authorizationSetRef,
                   String authorizationEvidenceVersion, List<String> authorizedOrderRefs, String orderState,
                   long projectionVersion, long aggregateVersion, PriceSnapshotSummary priceSnapshot,
                   String priceSnapshotDigest, String paymentDecisionRef, String paymentDecisionVersion,
                   String paymentState, String mnpDecisionRef, String mnpDecisionVersion, String mnpState,
                   String catalogVersion, String supportedOperatorSetVersion, boolean catalogCurrent,
                   boolean supportSetCurrent, boolean allowed, String upstreamDebitState, String deliveryState,
                   String accountingClosureState, UnknownAgeDecision unknownAgeDecision, String supportRef,
                   boolean duplicateCanonicalFactConflict, ResultState createResultState, Instant nextPollAt,
                   Instant now) {}
}
