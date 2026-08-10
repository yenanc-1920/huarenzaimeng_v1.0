package com.huarenzaimeng.api.buyerauth;

import java.time.Instant;
import java.util.Optional;

interface BuyerAuthStore {
    Identity establishIdentityAndSession(String appidDigest, String subjectDigest, String subjectRef,
                                         String sessionId, String tokenDigest, Instant issuedAt, Instant expiresAt,
                                         Audit audit);
    Optional<AuthenticatedBuyer> findActiveSession(String tokenDigest, Instant now);
    record Identity(String buyerId, String subjectRef) {}
    record AuthenticatedBuyer(String buyerId, String subjectRef) {}
    record Audit(String eventCode, String resultCode, String subjectFingerprint,
                 String sessionFingerprint, String requestId, Instant occurredAt) {}
}
