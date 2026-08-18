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
class JdbcBuyerAuthStore implements BuyerAuthStore {
    private final JdbcTemplate jdbc;
    JdbcBuyerAuthStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public boolean admitLoginWindow(String key,Instant now,Instant ends,int maxAttempts,int maxFailures){
        jdbc.update("INSERT INTO buyer_login_rate_window (window_key_digest,window_started_at,window_ends_at,attempt_count,failure_count,updated_at) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE attempt_count=IF(window_ends_at<=VALUES(updated_at),1,attempt_count+1),failure_count=IF(window_ends_at<=VALUES(updated_at),0,failure_count),window_started_at=IF(window_ends_at<=VALUES(updated_at),VALUES(window_started_at),window_started_at),window_ends_at=IF(window_ends_at<=VALUES(updated_at),VALUES(window_ends_at),window_ends_at),updated_at=VALUES(updated_at)",key,ts(now),ts(ends),1,0,ts(now));
        return jdbc.queryForObject("SELECT attempt_count<=? AND failure_count<? FROM buyer_login_rate_window WHERE window_key_digest=?",Boolean.class,maxAttempts,maxFailures,key);
    }
    @Override public void recordLoginWindowOutcome(String key,boolean succeeded,Instant at){if(!succeeded)jdbc.update("UPDATE buyer_login_rate_window SET failure_count=failure_count+1,updated_at=? WHERE window_key_digest=?",ts(at),key);}

    @Override public boolean beginLoginAttempt(String environment,String appIdRef,String codeDigest,String attemptRef,String requestRef,Instant createdAt){
        try{jdbc.update("INSERT INTO buyer_login_attempt (attempt_ref,request_ref,environment_code,app_id_ref,code_digest,status_code,created_at) VALUES (?,?,?,?,?,'PENDING',?)",attemptRef,requestRef,environment,appIdRef,codeDigest,ts(createdAt));return true;}catch(DuplicateKeyException duplicate){return false;}
    }
    @Override public void finishLoginAttempt(String attemptRef,String resultCode,String evidenceRef,Instant completedAt){
        int changed=jdbc.update("UPDATE buyer_login_attempt SET status_code='TERMINAL',result_code=?,evidence_ref=?,completed_at=?,aggregate_version=aggregate_version+1 WHERE attempt_ref=? AND status_code='PENDING'",resultCode,evidenceRef,ts(completedAt),attemptRef);
        if(changed!=1)throw new IllegalStateException("LOGIN_ATTEMPT_STATE_CONFLICT");
    }
    @Override @Transactional
    public Identity establishIdentityAndSession(String attemptRef,String identityEvidenceRef,String appidDigest, String subjectDigest, String subjectRef,
                                                String sessionId, String tokenDigest, Instant issuedAt,
                                                Instant expiresAt, Instant idleExpiresAt, Audit audit) {
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
        jdbc.update("INSERT INTO buyer_session (session_id,token_digest,buyer_id,status_code,issued_at,expires_at,idle_expires_at,last_seen_at,revoked_at) VALUES (?,?,?,'ACTIVE',?,?,?,NULL,NULL)",
                sessionId, tokenDigest, identity.buyerId(), ts(issuedAt), ts(expiresAt), ts(idleExpiresAt));
        appendAudit(audit);
        finishLoginAttempt(attemptRef,"SUCCEEDED",identityEvidenceRef,issuedAt);
        return identity;
    }

    @Override @Transactional
    public Identity establishIdentityConsentAndSession(String attemptRef,String identityEvidenceRef,String appidDigest,String subjectDigest,String subjectRef,
                                                        String sessionId,String tokenDigest,Instant issuedAt,Instant expiresAt,Instant idleExpiresAt,
                                                        Consent consent,Audit audit){
        Identity identity=findIdentityAnyState(appidDigest,subjectDigest).orElseGet(()->createIdentity(appidDigest,subjectDigest,subjectRef,issuedAt));
        if(!"ACTIVE".equals(identityStatus(identity.buyerId())))throw new IllegalStateException("BUYER_ACCOUNT_NOT_ACTIVE");
        String existing=jdbc.query("SELECT request_digest FROM buyer_consent_acceptance WHERE buyer_id=? AND request_ref=? LIMIT 1",
                (rs,n)->rs.getString(1),identity.buyerId(),consent.requestRef()).stream().findFirst().orElse(null);
        if(existing!=null&&!existing.equals(consent.requestDigest()))throw new IllegalStateException("CONSENT_IDEMPOTENCY_CONFLICT");
        if(existing==null){
            appendAcceptance(identity,consent,"USER_AGREEMENT",consent.userAgreementVersion());
            appendAcceptance(identity,consent,"PRIVACY_POLICY",consent.privacyPolicyVersion());
        }
        jdbc.update("INSERT INTO buyer_consent_state(buyer_id,guest_ref,consent_state,user_agreement_version,privacy_policy_version,accepted_at,aggregate_version,updated_at) VALUES(?,?,'VALID',?,?,?,1,?) ON DUPLICATE KEY UPDATE guest_ref=VALUES(guest_ref),consent_state='VALID',user_agreement_version=VALUES(user_agreement_version),privacy_policy_version=VALUES(privacy_policy_version),accepted_at=VALUES(accepted_at),aggregate_version=aggregate_version+1,updated_at=VALUES(updated_at)",
                identity.buyerId(),consent.guestRef(),consent.userAgreementVersion(),consent.privacyPolicyVersion(),ts(consent.acceptedAt()),ts(consent.acceptedAt()));
        jdbc.update("INSERT INTO buyer_session (session_id,token_digest,buyer_id,status_code,issued_at,expires_at,idle_expires_at,last_seen_at,revoked_at) VALUES (?,?,?,'ACTIVE',?,?,?,NULL,NULL)",
                sessionId,tokenDigest,identity.buyerId(),ts(issuedAt),ts(expiresAt),ts(idleExpiresAt));
        appendAudit(audit);finishLoginAttempt(attemptRef,"SUCCEEDED",identityEvidenceRef,issuedAt);return identity;
    }

    @Override public boolean accountMayLogin(String appIdRef,String subjectDigest){
        return jdbc.query("SELECT status_code FROM buyer_identity WHERE provider_code='WECHAT' AND provider_appid_digest=? AND provider_subject_digest=?",
                (rs,n)->rs.getString(1),appIdRef,subjectDigest).stream().findFirst().map("ACTIVE"::equals).orElse(true);
    }
    @Override public boolean guestMayLogin(String guestRef){
        Integer blocked=jdbc.queryForObject("SELECT COUNT(*) FROM buyer_consent_state c JOIN buyer_identity i ON i.buyer_id=c.buyer_id WHERE c.guest_ref=? AND i.status_code<>'ACTIVE'",Integer.class,guestRef);
        return blocked!=null&&blocked==0;
    }
    @Override public Optional<ConsentState> consentState(String subjectRef){
        return jdbc.query("SELECT i.subject_ref,c.guest_ref,c.consent_state,c.user_agreement_version,c.privacy_policy_version,c.accepted_at,c.aggregate_version FROM buyer_consent_state c JOIN buyer_identity i ON i.buyer_id=c.buyer_id WHERE i.subject_ref=? AND i.status_code='ACTIVE'",
                (rs,n)->new ConsentState(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant(),rs.getLong(7)),subjectRef).stream().findFirst();
    }

    @Override @Transactional public Optional<BuyerPrincipal> authenticateAndAdvanceIdle(String tokenDigest,Instant now,Instant requestedIdle,
                                                                                         String userAgreementVersion,String privacyPolicyVersion){
        var rows=jdbc.query("SELECT i.buyer_id,i.subject_ref,s.session_id,s.expires_at,s.idle_expires_at FROM buyer_session s JOIN buyer_identity i ON i.buyer_id=s.buyer_id JOIN buyer_consent_state c ON c.buyer_id=i.buyer_id WHERE s.token_digest=? AND s.status_code='ACTIVE' AND s.revoked_at IS NULL AND s.expires_at>? AND s.idle_expires_at>? AND i.status_code='ACTIVE' AND c.consent_state='VALID' AND c.user_agreement_version=? AND c.privacy_policy_version=? FOR UPDATE",
                (rs,row)->new SessionAuthorizationRow(rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant()),tokenDigest,ts(now),ts(now),userAgreementVersion,privacyPolicyVersion);
        if(rows.isEmpty())return Optional.empty();SessionAuthorizationRow current=rows.get(0);Instant next=requestedIdle.isBefore(current.absoluteExpiresAt())?requestedIdle:current.absoluteExpiresAt();
        int changed=jdbc.update("UPDATE buyer_session SET idle_expires_at=?,aggregate_version=aggregate_version+1 WHERE session_id=? AND status_code='ACTIVE' AND revoked_at IS NULL",ts(next),current.sessionRef());
        if(changed!=1)throw new IllegalStateException("BUYER_SESSION_AUTHORIZATION_UNKNOWN");
        return Optional.of(new BuyerPrincipal(Eligibility.ELIGIBLE,current.subjectRef(),current.sessionRef()));
    }
    @Override public LogoutResult revokeCurrentSession(String tokenDigest,Instant now){
        try{return jdbc.update("UPDATE buyer_session SET status_code='REVOKED',revoked_at=?,aggregate_version=aggregate_version+1 WHERE token_digest=? AND status_code='ACTIVE' AND revoked_at IS NULL",ts(now),tokenDigest)==1?LogoutResult.SUCCEEDED:LogoutResult.UNAVAILABLE;}catch(RuntimeException unknown){return LogoutResult.UNKNOWN;}
    }

    private Optional<Identity> findIdentity(String appidDigest, String subjectDigest) {
        return jdbc.query("SELECT buyer_id,subject_ref FROM buyer_identity WHERE provider_code='WECHAT' AND provider_appid_digest=? AND provider_subject_digest=? AND status_code='ACTIVE'",
                (rs, row) -> new Identity(rs.getString(1), rs.getString(2)), appidDigest, subjectDigest).stream().findFirst();
    }
    private Optional<Identity> findIdentityAnyState(String appidDigest,String subjectDigest){
        return jdbc.query("SELECT buyer_id,subject_ref FROM buyer_identity WHERE provider_code='WECHAT' AND provider_appid_digest=? AND provider_subject_digest=?",
                (rs,n)->new Identity(rs.getString(1),rs.getString(2)),appidDigest,subjectDigest).stream().findFirst();
    }
    private Identity createIdentity(String appidDigest,String subjectDigest,String subjectRef,Instant at){
        String buyerId=UUID.randomUUID().toString();
        try{jdbc.update("INSERT INTO buyer_identity (buyer_id,subject_ref,provider_code,provider_appid_digest,provider_subject_digest,status_code,created_at,updated_at) VALUES (?,?, 'WECHAT', ?,?, 'ACTIVE', ?,?)",buyerId,subjectRef,appidDigest,subjectDigest,ts(at),ts(at));return new Identity(buyerId,subjectRef);}
        catch(DuplicateKeyException race){return findIdentityAnyState(appidDigest,subjectDigest).orElseThrow(()->race);}
    }
    private String identityStatus(String buyerId){return jdbc.queryForObject("SELECT status_code FROM buyer_identity WHERE buyer_id=? FOR UPDATE",String.class,buyerId);}
    private void appendAcceptance(Identity identity,Consent consent,String type,String version){
        jdbc.update("INSERT INTO buyer_consent_acceptance(acceptance_ref,buyer_id,subject_ref,guest_ref,policy_type,policy_version,request_ref,request_digest,accepted_at) VALUES(?,?,?,?,?,?,?,?,?)",
                "CONSENT-"+UUID.randomUUID(),identity.buyerId(),identity.subjectRef(),consent.guestRef(),type,version,consent.requestRef(),consent.requestDigest(),ts(consent.acceptedAt()));
    }
    private void appendAudit(Audit a) {
        jdbc.update("INSERT INTO buyer_auth_audit (event_code,result_code,subject_fingerprint,session_fingerprint,request_id,occurred_at) VALUES (?,?,?,?,?,?)",
                a.eventCode(), a.resultCode(), a.subjectFingerprint(), a.sessionFingerprint(), a.requestId(), ts(a.occurredAt()));
    }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    private record SessionAuthorizationRow(String subjectRef,String sessionRef,Instant absoluteExpiresAt){}
}
