package com.huarenzaimeng.api;

import com.huarenzaimeng.core.AllowedAction;
import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.PriceSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

enum PaymentEligibilityDecisionStatus {
    ELIGIBLE,
    UNKNOWN,
    REJECTED
}

record PaymentEligibilityDecision(
        String decisionRef,
        String a1EvidenceRef,
        String a1EvidenceVersion,
        String environment,
        String projectSubjectRef,
        String orderRef,
        String priceSnapshotDigest,
        long supportedOperatorSetVersion,
        long catalogVersion,
        long parentProjectionVersion,
        long parentAggregateVersion,
        String authorizationEvidenceVersion,
        String allowedActionCode,
        String ruleVersion,
        PaymentEligibilityDecisionStatus status,
        Instant validUntil
) {}

record PaymentIntentDraft(
        String paymentIntentRef,
        String environment,
        String projectSubjectRef,
        String orderRef,
        String businessKey,
        String semanticActionKey,
        String requestFingerprint,
        PriceSnapshot priceSnapshot,
        String priceSnapshotDigest,
        String paymentEligibilityDecisionRef,
        String scope,
        Instant createdAt
) {}

record PaymentIntentRecord(
        String paymentIntentRef,
        String environment,
        String projectSubjectRef,
        String orderRef,
        String businessKey,
        String semanticActionKey,
        String requestFingerprint,
        PriceSnapshot priceSnapshot,
        String priceSnapshotDigest,
        String paymentEligibilityDecisionRef,
        String scope,
        Instant createdAt
) {}

record PaymentIntentCreateResult(
        PaymentIntentRecord paymentIntent,
        OrderProjection order,
        boolean created
) {}

record PaymentIntentCurrentProjection(
        String orderRef,
        UserOrderStateCode stateCode,
        PriceSnapshot priceSnapshot,
        String intentScope,
        boolean paymentInitiated,
        boolean paymentConfirmed,
        long projectionVersion,
        long aggregateVersion,
        List<AllowedAction> allowedActions
) {}

record PaymentIntentResponse(
        String requestRef,
        String outcome,
        String projectCode,
        String resourceRef,
        long aggregateVersion,
        PaymentIntentCurrentProjection currentProjection,
        String retryClass
) {}

enum PaymentIntentResultState {
    FOUND,
    REJECTED,
    UNKNOWN
}

record PaymentIntentResultRecord(
        String environment,
        String projectSubjectRef,
        String orderRef,
        String originalCommandId,
        String originalIdempotencyKey,
        String businessKey,
        String semanticActionKey,
        String requestFingerprint,
        String paymentIntentRef,
        PaymentIntentResultState state,
        Instant nextPollAt
) {}

record PaymentIntentResultResponse(
        String requestRef,
        String outcome,
        String projectCode,
        String resourceRef,
        Long aggregateVersion,
        PaymentIntentCurrentProjection currentProjection,
        String retryClass,
        Instant nextPollAt
) {}

record PaymentIntentEvidenceSnapshot(
        long orders,
        long commands,
        long commandAliases,
        long orderVersions,
        long projectionVersions,
        long semanticActions,
        long paymentIntents,
        long paymentIntentBusinessKeys,
        long reviewSignals,
        OrderCreationSideEffectSnapshot downstream
) {}

enum PaymentIntentReviewReason {
    ORIGINAL_COMMAND_BINDING_MISMATCH,
    PAYMENT_INTENT_RESULT_BINDING_CONFLICT,
    AUTHORITATIVE_RESULT_CONFLICT,
    CONTROLLED_RUNTIME_EXCEPTION
}

record PaymentIntentReviewSignalInput(
        String environment,
        String projectSubjectRef,
        String orderRef,
        String originalCommandId,
        String originalIdempotencyKey,
        String paymentIntentRef,
        PaymentIntentReviewReason reason
) {}

record PaymentIntentReviewSignal(
        String reviewSignalRef,
        String evidenceRef,
        String caseRef,
        String relatedIntentRef,
        PaymentIntentReviewReason reason
) {
    static PaymentIntentReviewSignal from(PaymentIntentReviewSignalInput input) {
        String base = CanonicalFingerprint.sha256("PaymentIntentResultReview",
                safe(input.environment()), safe(input.projectSubjectRef()), safe(input.orderRef()),
                safe(input.originalCommandId()), safe(input.originalIdempotencyKey()), input.reason().name());
        String related = CanonicalFingerprint.sha256("PaymentIntentResultReviewIntent",
                safe(input.projectSubjectRef()), safe(input.paymentIntentRef()));
        return new PaymentIntentReviewSignal("RS-" + base, "EV-" + base,
                "CASE-" + CanonicalFingerprint.sha256("PaymentIntentResultReviewCase", base),
                "PIH-" + related, input.reason());
    }

    private static String safe(String value) {
        return Objects.toString(value, "ABSENT");
    }
}

final class PaymentIntentReviewRequiredException extends RuntimeException {
    private final PaymentIntentReviewReason reason;
    private final String paymentIntentRef;

    PaymentIntentReviewRequiredException(PaymentIntentReviewReason reason, String paymentIntentRef) {
        super(reason.name());
        this.reason = reason;
        this.paymentIntentRef = paymentIntentRef;
    }

    PaymentIntentReviewReason reason() {
        return reason;
    }

    String paymentIntentRef() {
        return paymentIntentRef;
    }
}
