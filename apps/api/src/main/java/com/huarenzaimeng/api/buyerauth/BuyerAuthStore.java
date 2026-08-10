package com.huarenzaimeng.api.buyerauth;

import java.time.Instant;
import java.util.Optional;

interface BuyerAuthStore {
    boolean beginLoginAttempt(String environment, String appIdRef, String codeDigest, String attemptRef,
                              String requestRef, Instant createdAt);
    void finishLoginAttempt(String attemptRef, String resultCode, String evidenceRef, Instant completedAt);
    Identity establishIdentityAndSession(String attemptRef, String identityEvidenceRef,
                                         String appIdRef, String subjectDigest, String subjectRef,
                                         String sessionId, String tokenDigest, Instant issuedAt,
                                         Instant absoluteExpiresAt, Instant idleExpiresAt, Audit audit);
    Optional<BuyerPrincipal> authenticateAndAdvanceIdle(String tokenDigest, Instant now, Instant nextIdleExpiresAt);
    LogoutResult revokeCurrentSession(String tokenDigest, Instant now);
    record Identity(String buyerId, String subjectRef) {}
    enum Eligibility { ELIGIBLE }
    record BuyerPrincipal(Eligibility eligibility, String subjectRef, String sessionRef) {}
    enum LogoutResult { SUCCEEDED, UNAVAILABLE, UNKNOWN }
    record Audit(String eventCode, String resultCode, String subjectFingerprint,
                 String sessionFingerprint, String requestId, Instant occurredAt) {}
}
