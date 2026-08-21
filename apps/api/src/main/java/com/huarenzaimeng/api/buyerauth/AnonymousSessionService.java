package com.huarenzaimeng.api.buyerauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("release-mysql")
final class AnonymousSessionService {
    private static final Duration TTL=Duration.ofHours(24);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final byte[] key;
    private final String userAgreementVersion;
    private final String privacyPolicyVersion;

    @Autowired AnonymousSessionService(JdbcTemplate jdbc, TransactionTemplate transactions,
                            @Value("${hz.buyer-auth.identity-pepper:}") String key,
                            @Value("${hz.buyer-consent.user-agreement-version:}") String userAgreementVersion,
                            @Value("${hz.buyer-consent.privacy-policy-version:}") String privacyPolicyVersion) {
        this(jdbc,transactions,Clock.systemUTC(),key,userAgreementVersion,privacyPolicyVersion);
    }

    AnonymousSessionService(JdbcTemplate jdbc,TransactionTemplate transactions,Clock clock,String key,
                            String userAgreementVersion,String privacyPolicyVersion) {
        this.jdbc=jdbc;this.transactions=transactions;this.clock=clock;
        this.key=key==null?new byte[0]:key.getBytes(StandardCharsets.UTF_8);
        this.userAgreementVersion=userAgreementVersion;this.privacyPolicyVersion=privacyPolicyVersion;
    }

    SessionResult establish(Command command) {
        validate(command);
        String requestDigest=sha256("ANONYMOUS_SESSION",command.requestRef(),sha256(command.guestRef()),
                command.userAgreementVersion(),command.privacyPolicyVersion(),"true","true");
        try {
            SessionRow result=transactions.execute(status->{
                SessionRow existing=findByRequest(command.requestRef(),true);
                if(existing!=null)return requireDigest(existing,requestDigest);
                Instant issuedAt=clock.instant();
                String subjectRef="ANON-"+UUID.randomUUID();
                String sessionRef="AS-"+UUID.randomUUID();
                String token=token(subjectRef);
                jdbc.update("INSERT INTO buyer_anonymous_session(anonymous_session_ref,anonymous_subject_ref,request_ref,request_digest,guest_ref_digest,token_digest,status_code,user_agreement_version,privacy_policy_version,issued_at,absolute_expires_at,created_at) VALUES(?,?,?,?,?,?,'ACTIVE',?,?,?,?,?)",
                        sessionRef,subjectRef,command.requestRef(),requestDigest,sha256(command.guestRef()),sha256(token),
                        command.userAgreementVersion(),command.privacyPolicyVersion(),ts(issuedAt),ts(issuedAt.plus(TTL)),ts(issuedAt));
                return findByRequest(command.requestRef(),false);
            });
            if(result==null)throw new Rejected("ANONYMOUS_SESSION_RESULT_UNKNOWN");
            return view(result);
        } catch (DuplicateKeyException race) {
            SessionRow existing=findByRequest(command.requestRef(),false);
            if(existing==null)throw new Rejected("ANONYMOUS_SESSION_RESULT_UNKNOWN");
            return view(requireDigest(existing,requestDigest));
        } catch (Rejected rejected) { throw rejected; }
        catch (RuntimeException unknown) { throw new Rejected("ANONYMOUS_SESSION_RESULT_UNKNOWN"); }
    }

    Optional<AnonymousTransactionPrincipal> authenticate(String bearer) {
        if(key.length<32||bearer==null||bearer.isBlank()||userAgreementVersion.isBlank()||privacyPolicyVersion.isBlank())return Optional.empty();
        Instant now=clock.instant();
        return jdbc.query("SELECT anonymous_subject_ref,anonymous_session_ref FROM buyer_anonymous_session WHERE token_digest=? AND status_code='ACTIVE' AND absolute_expires_at>? AND user_agreement_version=? AND privacy_policy_version=?",
                (rs,n)->new AnonymousTransactionPrincipal(rs.getString(1),rs.getString(2)),sha256(bearer),ts(now),userAgreementVersion,privacyPolicyVersion).stream().findFirst();
    }

    private void validate(Command c) {
        if(key.length<32)throw new Rejected("ANONYMOUS_SESSION_CONFIGURATION_UNAVAILABLE");
        if(c==null||!validRef(c.requestRef())||!validRef(c.guestRef())||userAgreementVersion.isBlank()||privacyPolicyVersion.isBlank()
                ||!userAgreementVersion.equals(c.userAgreementVersion())||!privacyPolicyVersion.equals(c.privacyPolicyVersion())
                ||!c.userAgreementAccepted()||!c.privacyPolicyAccepted())throw new Rejected("ANONYMOUS_SESSION_REQUEST_INVALID");
    }
    private SessionResult view(SessionRow row) {
        String token=token(row.subjectRef());
        if(!MessageDigest.isEqual(sha256(token).getBytes(StandardCharsets.US_ASCII),row.tokenDigest().getBytes(StandardCharsets.US_ASCII)))
            throw new Rejected("ANONYMOUS_SESSION_RESULT_UNKNOWN");
        return new SessionResult(row.requestRef(),row.subjectRef(),token,row.absoluteExpiresAt());
    }
    private SessionRow findByRequest(String requestRef,boolean lock) {
        return jdbc.query("SELECT anonymous_session_ref,anonymous_subject_ref,request_ref,request_digest,token_digest,absolute_expires_at FROM buyer_anonymous_session WHERE request_ref=?"+(lock?" FOR UPDATE":""),
                (rs,n)->new SessionRow(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant()),requestRef).stream().findFirst().orElse(null);
    }
    private static SessionRow requireDigest(SessionRow row,String digest){if(!row.requestDigest().equals(digest))throw new Rejected("ANONYMOUS_SESSION_IDEMPOTENCY_CONFLICT");return row;}
    private String token(String subjectRef){return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key,"ANONYMOUS_BEARER\0"+subjectRef));}
    private static byte[] hmac(byte[] key,String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new Rejected("ANONYMOUS_SESSION_RESULT_UNKNOWN");}}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new Rejected("ANONYMOUS_SESSION_RESULT_UNKNOWN");}}
    private static String sha256(String... values){return sha256(String.join("\0",values));}
    private static boolean validRef(String value){return value!=null&&value.matches("[A-Za-z0-9._:-]{8,128}");}
    private static java.sql.Timestamp ts(Instant value){return java.sql.Timestamp.from(value);}

    record Command(String requestRef,String guestRef,String userAgreementVersion,String privacyPolicyVersion,
                   boolean userAgreementAccepted,boolean privacyPolicyAccepted){}
    record SessionResult(String requestRef,String subjectRef,String token,Instant absoluteExpiresAt){}
    private record SessionRow(String sessionRef,String subjectRef,String requestRef,String requestDigest,String tokenDigest,Instant absoluteExpiresAt){}
    static final class Rejected extends RuntimeException { final String projectCode; Rejected(String projectCode){super(projectCode);this.projectCode=projectCode;} }
}
