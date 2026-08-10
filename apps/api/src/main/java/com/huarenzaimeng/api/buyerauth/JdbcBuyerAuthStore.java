package com.huarenzaimeng.api.buyerauth;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("release-mysql")
final class JdbcBuyerAuthStore implements BuyerAuthStore {
    private final JdbcTemplate jdbc;
    JdbcBuyerAuthStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public Identity establishIdentityAndSession(String appidDigest, String subjectDigest, String subjectRef,
                                                String sessionId, String tokenDigest, Instant issuedAt,
                                                Instant expiresAt, Audit audit) {
        Identity identity = findIdentity(appidDigest, subjectDigest).orElseGet(() -> {
            String buyerId = UUID.randomUUID().toString();
            try {
                jdbc.update("INSERT INTO buyer_identity (buyer_id,subject_ref,provider_code,provider_appid_digest,provider_subject_digest,status_code,created_at,updated_at) VALUES (?,?, 'WECHAT', ?,?, 'ACTIVE', ?,?)",
                        buyerId, subjectRef, appidDigest, subjectDigest, ts(issuedAt), ts(issuedAt));
                return new Identity(buyerId, subjectRef);
            } catch (DuplicateKeyException race) {
                return findIdentity(appidDigest, subjectDigest).orElseThrow(() -> race);
            }
        });
        jdbc.update("INSERT INTO buyer_session (session_id,token_digest,buyer_id,status_code,issued_at,expires_at,last_seen_at,revoked_at) VALUES (?,?,?,'ACTIVE',?,?,?,NULL)",
                sessionId, tokenDigest, identity.buyerId(), ts(issuedAt), ts(expiresAt), ts(issuedAt));
        appendAudit(audit);
        return identity;
    }

    @Override public Optional<AuthenticatedBuyer> findActiveSession(String tokenDigest, Instant now) {
        return jdbc.query("SELECT i.buyer_id,i.subject_ref FROM buyer_session s JOIN buyer_identity i ON i.buyer_id=s.buyer_id WHERE s.token_digest=? AND s.status_code='ACTIVE' AND s.revoked_at IS NULL AND s.expires_at>? AND i.status_code='ACTIVE'",
                (rs, row) -> new AuthenticatedBuyer(rs.getString(1), rs.getString(2)), tokenDigest, ts(now)).stream().findFirst();
    }

    private Optional<Identity> findIdentity(String appidDigest, String subjectDigest) {
        return jdbc.query("SELECT buyer_id,subject_ref FROM buyer_identity WHERE provider_code='WECHAT' AND provider_appid_digest=? AND provider_subject_digest=? AND status_code='ACTIVE'",
                (rs, row) -> new Identity(rs.getString(1), rs.getString(2)), appidDigest, subjectDigest).stream().findFirst();
    }
    private void appendAudit(Audit a) {
        jdbc.update("INSERT INTO buyer_auth_audit (event_code,result_code,subject_fingerprint,session_fingerprint,request_id,occurred_at) VALUES (?,?,?,?,?,?)",
                a.eventCode(), a.resultCode(), a.subjectFingerprint(), a.sessionFingerprint(), a.requestId(), ts(a.occurredAt()));
    }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
}
