package com.huarenzaimeng.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

enum UserOrderStateCode {
    AWAITING_PAYMENT,
    PAYMENT_PROCESSING,
    PAID_AWAITING_TOPUP,
    TOPUP_PROCESSING,
    TOPUP_RESULT_UNKNOWN,
    DELIVERED,
    CONFIRMED_NOT_DELIVERED,
    REFUND_PROCESSING,
    REFUNDED,
    DELIVERY_REFUND_CONFLICT_REVIEW,
    SUPPORT_REVIEW
}

enum RecoveryOutcome { RECOVERED, REJECTED, UNKNOWN }

enum ProjectSessionRole { GUEST, BUYER }

record AuthorizationEnvelope(
        String projectSubjectRef,
        ProjectSessionRole sessionRole,
        long sessionVersion,
        String authorizationSetRef,
        String authorizationEvidenceVersion,
        List<String> authorizedOrderRefs,
        Instant issuedAt,
        Instant expiresAt
) {}

record UserOrderListItem(
        String orderRef,
        UserOrderStateCode stateCode,
        long projectionVersion,
        Instant generatedAt
) {}

record OrderListResponse(
        String projectSubjectRef,
        ProjectSessionRole sessionRole,
        long sessionVersion,
        String authorizationSetRef,
        String authorizationEvidenceVersion,
        List<String> authorizedOrderRefs,
        Instant issuedAt,
        Instant expiresAt,
        List<UserOrderListItem> orders
) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record RecoveryCaseResponse(
        String recoveryCaseRef,
        RecoveryOutcome outcome,
        String retryClass,
        String safeQueryPath,
        AuthorizationEnvelope authorization
) {}

record SyntheticOrderRecord(
        String orderRef,
        String projectSubjectRef,
        String authorizationEvidenceVersion,
        String environment,
        String stateCode,
        long projectionVersion,
        Instant generatedAt,
        boolean synthetic
) {}

record SyntheticRecoveryEvidence(
        RecoveryOutcome outcome,
        String authorizationEvidenceVersion,
        List<String> authorizedOrderRefs
) {}

final class LocalSyntheticRecoveryUnavailableException extends RuntimeException {
    LocalSyntheticRecoveryUnavailableException() { super("local synthetic recovery unavailable"); }
}
