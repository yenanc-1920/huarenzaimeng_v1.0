package com.huarenzaimeng.api.adminauth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Profile("!release-mysql")
class InMemoryAdminAuthStore implements AdminAuthStore {
    private final Map<String, User> users = new HashMap<>();
    private final Map<String, Session> sessions = new HashMap<>();
    private final List<Audit> audits = new ArrayList<>();

    @Override public synchronized long userCount() { return users.size(); }
    @Override public synchronized void createInitialAdmin(User user, Audit audit) {
        if (!users.isEmpty()) throw new IllegalStateException("ADMIN_ALREADY_INITIALIZED");
        users.put(user.username(), user); audits.add(audit);
    }
    @Override public synchronized Optional<User> findActiveUser(String username) {
        return Optional.ofNullable(users.get(username)).filter(user -> "ACTIVE".equals(user.statusCode()));
    }
    @Override public synchronized void recordLoginFailure(String userId, int failedCount, Instant lockedUntil, Audit audit) {
        users.replaceAll((username, user) -> user.userId().equals(userId)
                ? new User(user.userId(), user.username(), user.displayName(), user.passwordHash(), user.roleCode(), user.statusCode(), failedCount, lockedUntil, audit.occurredAt()) : user);
        audits.add(audit);
    }
    @Override public synchronized void recordLoginSuccess(String userId, Audit audit) {
        users.replaceAll((username, user) -> user.userId().equals(userId)
                ? new User(user.userId(), user.username(), user.displayName(), user.passwordHash(), user.roleCode(), user.statusCode(), 0, null, audit.occurredAt()) : user);
        audits.add(audit);
    }
    @Override public synchronized void createSession(Session session, Audit audit) {
        sessions.put(session.tokenDigest(), session); audits.add(audit);
    }
    @Override public synchronized Optional<AuthenticatedUser> findSession(String digest, Instant now) {
        Session session = sessions.get(digest);
        if (session == null || !session.expiresAt().isAfter(now)) return Optional.empty();
        return users.values().stream().filter(user -> user.userId().equals(session.userId()) && "ACTIVE".equals(user.statusCode()))
                .map(user -> new AuthenticatedUser(user.userId(), user.username(), user.displayName(), user.roleCode())).findFirst();
    }
    @Override public synchronized void revokeSession(String digest, Instant now, Audit audit) {
        sessions.remove(digest); audits.add(audit);
    }
    @Override public synchronized void appendAudit(Audit audit) { audits.add(audit); }
}
