package com.huarenzaimeng.api.buyerauth;

import java.time.Instant;
import java.util.Optional;

interface BuyerAuthStore {
    boolean admitLoginWindow(String windowKeyDigest, Instant now, Instant windowEndsAt,
                             int maximumAttempts, int maximumFailures);
    void recordLoginWindowOutcome(String windowKeyDigest, boolean succeeded, Instant occurredAt);
    boolean beginLoginAttempt(String environment, String appIdRef, String codeDigest, String attemptRef,
                              String requestRef, Instant createdAt);
    void finishLoginAttempt(String attemptRef, String resultCode, String evidenceRef, Instant completedAt);
    Identity establishIdentityAndSession(String attemptRef, String identityEvidenceRef,
                                         String appIdRef, String subjectDigest, String subjectRef,
                                         String sessionId, String tokenDigest, Instant issuedAt,
                                         Instant absoluteExpiresAt, Instant idleExpiresAt, Audit audit);
    default Identity establishIdentityConsentAndSession(String attemptRef, String identityEvidenceRef,
                                         String appIdRef, String subjectDigest, String subjectRef,
                                         String sessionId, String tokenDigest, Instant issuedAt,
                                         Instant absoluteExpiresAt, Instant idleExpiresAt, Consent consent, Audit audit) {
        return establishIdentityAndSession(attemptRef,identityEvidenceRef,appIdRef,subjectDigest,subjectRef,
                sessionId,tokenDigest,issuedAt,absoluteExpiresAt,idleExpiresAt,audit);
    }
    default boolean accountMayLogin(String appIdRef,String subjectDigest){ return true; }
    default boolean guestMayLogin(String guestRef){ return true; }
    default Optional<ConsentState> consentState(String subjectRef){ return Optional.empty(); }
    Optional<BuyerPrincipal> authenticateAndAdvanceIdle(String tokenDigest, Instant now, Instant nextIdleExpiresAt,
                                                        String currentUserAgreementVersion,
                                                        String currentPrivacyPolicyVersion);
    LogoutResult revokeCurrentSession(String tokenDigest, Instant now);
    record Identity(String buyerId, String subjectRef) {}
    enum Eligibility { ELIGIBLE }
    record BuyerPrincipal(Eligibility eligibility, String subjectRef, String sessionRef) {}
    enum LogoutResult { SUCCEEDED, UNAVAILABLE, UNKNOWN }
    record Audit(String eventCode, String resultCode, String subjectFingerprint,
                 String sessionFingerprint, String requestId, Instant occurredAt) {}
    record Consent(String guestRef,String requestRef,String requestDigest,String userAgreementVersion,
                   String privacyPolicyVersion,Instant acceptedAt) {}
    record ConsentState(String subjectRef,String guestRef,String state,String userAgreementVersion,
                        String privacyPolicyVersion,Instant acceptedAt,long version) {}
}
