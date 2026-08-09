package com.huarenzaimeng.api.adminauth;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
@Profile("release-mysql")
class JdbcAdminAuthStore implements AdminAuthStore {
    private final JdbcTemplate jdbc;
    JdbcAdminAuthStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public long userCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_user", Long.class);
        return count == null ? 0 : count;
    }

    @Override @Transactional public void createInitialAdmin(User user, Audit audit) {
        Integer lock = jdbc.queryForObject("SELECT GET_LOCK('hz_admin_bootstrap_v1', 5)", Integer.class);
        if (lock == null || lock != 1) throw new IllegalStateException("ADMIN_BOOTSTRAP_LOCK_UNAVAILABLE");
        try {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_user", Long.class);
            if (count == null || count != 0) throw new IllegalStateException("ADMIN_ALREADY_INITIALIZED");
            jdbc.update("INSERT INTO admin_user (user_id,username,display_name,password_hash,role_code,status_code,failed_login_count,locked_until,password_changed_at,created_at,updated_at) VALUES (?,?,?,?,?,?,0,NULL,?,?,?)",
                    user.userId(), user.username(), user.displayName(), user.passwordHash(), user.roleCode(), user.statusCode(),
                    ts(user.now()), ts(user.now()), ts(user.now()));
            appendAudit(audit);
        } finally {
            jdbc.queryForObject("SELECT RELEASE_LOCK('hz_admin_bootstrap_v1')", Integer.class);
        }
    }

    @Override public Optional<User> findActiveUser(String username) {
        return jdbc.query("SELECT user_id,username,display_name,password_hash,role_code,status_code,failed_login_count,locked_until,updated_at FROM admin_user WHERE username=? AND status_code='ACTIVE'",
                (rs, row) -> new User(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7),
                        rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(), rs.getTimestamp(9).toInstant()), username).stream().findFirst();
    }

    @Override @Transactional public void recordLoginFailure(String userId, int failedCount, Instant lockedUntil, Audit audit) {
        jdbc.update("UPDATE admin_user SET failed_login_count=?,locked_until=?,updated_at=? WHERE user_id=?", failedCount,
                lockedUntil == null ? null : ts(lockedUntil), ts(audit.occurredAt()), userId);
        appendAudit(audit);
    }

    @Override @Transactional public void recordLoginSuccess(String userId, Audit audit) {
        jdbc.update("UPDATE admin_user SET failed_login_count=0,locked_until=NULL,updated_at=? WHERE user_id=?", ts(audit.occurredAt()), userId);
        appendAudit(audit);
    }

    @Override @Transactional public void createSession(Session session, Audit audit) {
        jdbc.update("INSERT INTO admin_session (session_id,user_id,token_digest,created_at,last_seen_at,expires_at,revoked_at) VALUES (?,?,?,?,?,?,NULL)",
                session.sessionId(), session.userId(), session.tokenDigest(), ts(session.createdAt()), ts(session.createdAt()), ts(session.expiresAt()));
        appendAudit(audit);
    }

    @Override public Optional<AuthenticatedUser> findSession(String digest, Instant now) {
        return jdbc.query("SELECT u.user_id,u.username,u.display_name,u.role_code FROM admin_session s JOIN admin_user u ON u.user_id=s.user_id WHERE s.token_digest=? AND s.revoked_at IS NULL AND s.expires_at>? AND u.status_code='ACTIVE'",
                (rs, row) -> new AuthenticatedUser(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), digest, ts(now)).stream().findFirst();
    }

    @Override @Transactional public void revokeSession(String digest, Instant now, Audit audit) {
        jdbc.update("UPDATE admin_session SET revoked_at=? WHERE token_digest=? AND revoked_at IS NULL", ts(now), digest);
        appendAudit(audit);
    }

    @Override public void appendAudit(Audit audit) {
        jdbc.update("INSERT INTO admin_auth_audit (audit_id,event_code,user_id,username_fingerprint,result_code,request_id,occurred_at) VALUES (?,?,?,?,?,?,?)",
                audit.auditId(), audit.eventCode(), audit.userId(), audit.usernameFingerprint(), audit.resultCode(), audit.requestId(), ts(audit.occurredAt()));
    }

    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
}
