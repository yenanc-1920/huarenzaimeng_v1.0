package com.huarenzaimeng.api.adminauth;

import java.time.Instant;
import java.util.Optional;

interface AdminAuthStore {
    long userCount();
    void createInitialAdmin(User user, Audit audit);
    Optional<User> findActiveUser(String normalizedUsername);
    void recordLoginFailure(String userId, int failedCount, Instant lockedUntil, Audit audit);
    void recordLoginSuccess(String userId, Audit audit);
    void resetPassword(String userId, String passwordHash, Instant changedAt, Audit audit);
    void createSession(Session session, Audit audit);
    Optional<AuthenticatedUser> findSession(String tokenDigest, Instant now);
    void revokeSession(String tokenDigest, Instant now, Audit audit);
    void appendAudit(Audit audit);

    record User(String userId, String username, String displayName, String passwordHash,
                String roleCode, String statusCode, int failedLoginCount, Instant lockedUntil, Instant now) {}
    record Session(String sessionId, String userId, String tokenDigest, Instant createdAt, Instant expiresAt) {}
    record AuthenticatedUser(String userId, String username, String displayName, String roleCode) {}
    record Audit(String auditId, String eventCode, String userId, String usernameFingerprint,
                 String resultCode, String requestId, Instant occurredAt) {}
}
