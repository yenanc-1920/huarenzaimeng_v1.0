package com.huarenzaimeng.api.buyerauth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("release-mysql")
final class BuyerAuthService {
    private final BuyerAuthStore store; private final WeChatIdentityPort provider; private final boolean enabled;
    private final byte[] pepper; private final Duration absoluteTtl; private final Duration idleTtl;
    private final SecureRandom random; private final Clock clock;
    private final String expectedAppIdRef;
    private final String currentUserAgreementVersion; private final String currentPrivacyPolicyVersion;
    @Autowired
    BuyerAuthService(BuyerAuthStore store, @Value("${hz.buyer-auth.enabled:false}") boolean enabled,
                     WeChatIdentityPort provider,
                     @Value("${hz.buyer-auth.expected-app-id-ref:}") String expectedAppIdRef,
                     @Value("${hz.buyer-auth.identity-pepper:}") String pepper,
                     @Value("${hz.buyer-auth.code-pepper:}") String codePepper,
                     @Value("${hz.buyer-consent.user-agreement-version:}") String userAgreementVersion,
                     @Value("${hz.buyer-consent.privacy-policy-version:}") String privacyPolicyVersion) {
        this(store, provider, enabled, expectedAppIdRef, pepper, codePepper,userAgreementVersion,privacyPolicyVersion, Clock.systemUTC(), new SecureRandom());
    }
    BuyerAuthService(BuyerAuthStore store, WeChatIdentityPort provider, boolean enabled,
                     String expectedAppIdRef, String identityPepper, String codePepper, Clock clock, SecureRandom random) {
        this(store,provider,enabled,expectedAppIdRef,identityPepper,codePepper,"UA-V1","PP-V1",clock,random);
    }
    BuyerAuthService(BuyerAuthStore store, WeChatIdentityPort provider, boolean enabled,
                     String expectedAppIdRef, String identityPepper, String codePepper,String userAgreementVersion,
                     String privacyPolicyVersion,Clock clock, SecureRandom random) {
        this.store=store;this.provider=provider;this.enabled=enabled;this.clock=clock;this.random=random;
        this.expectedAppIdRef=expectedAppIdRef;
        this.currentUserAgreementVersion=userAgreementVersion;this.currentPrivacyPolicyVersion=privacyPolicyVersion;
        this.pepper=identityPepper.getBytes(StandardCharsets.UTF_8);
        if (enabled && (!validProviderValue(expectedAppIdRef) || identityPepper.length()<32 || codePepper.length()<32 || identityPepper.equals(codePepper))) throw new Rejected("BUYER_AUTH_CONFIGURATION_UNAVAILABLE");
        this.codePepper=codePepper.getBytes(StandardCharsets.UTF_8);this.absoluteTtl=Duration.ofHours(24);this.idleTtl=Duration.ofHours(2);
    }
    private final byte[] codePepper;
    SessionResult establish(LoginCommand command){
        validateConsent(command);
        if(!store.guestMayLogin(command.guestRef()))throw new Rejected("BUYER_ACCOUNT_CLOSURE_PENDING");
        return establishInternal(command.code(),command.requestRef(),command);
    }
    SessionResult establish(String code, String requestRef) {
        return establishInternal(code,requestRef,null);
    }
    private SessionResult establishInternal(String code,String requestRef,LoginCommand command) {
        if (!enabled) throw new Rejected("BUYER_AUTH_DISABLED");
        if (!validCode(code) || !validRequestRef(requestRef)) throw new Rejected("LOGIN_REQUEST_INVALID");
        Instant attemptStartedAt=clock.instant();String attemptRef="LOGIN-"+UUID.randomUUID();String providerCallRef="CALL-"+UUID.randomUUID();
        String windowKeyDigest=sha256(requestRef);
        if(!store.admitLoginWindow(windowKeyDigest,attemptStartedAt,attemptStartedAt.plus(Duration.ofMinutes(10)),10,5)) throw new Rejected("LOGIN_RATE_LIMITED");
        String codeDigest=hmac(codePepper,code);
        if(!store.beginLoginAttempt("RELEASE","WECHAT_PRIMARY",codeDigest,attemptRef,requestRef,attemptStartedAt)) throw new Rejected("LOGIN_CODE_ALREADY_SUBMITTED");
        WeChatIdentityPort.Result exchange;
        try { exchange=provider.exchange(new WeChatIdentityPort.Command(code,providerCallRef)); }
        catch(RuntimeException failure){exchange=new WeChatIdentityPort.Unknown("CONTROLLED_PROVIDER_FAILURE");}
        if(exchange instanceof WeChatIdentityPort.Rejected){store.finishLoginAttempt(attemptRef,"REJECTED",providerCallRef,clock.instant());store.recordLoginWindowOutcome(windowKeyDigest,false,clock.instant());throw new Rejected("WECHAT_LOGIN_REJECTED");}
        if(exchange instanceof WeChatIdentityPort.Unknown unknown){store.finishLoginAttempt(attemptRef,"UNKNOWN",providerCallRef,clock.instant());store.recordLoginWindowOutcome(windowKeyDigest,false,clock.instant());throw new Rejected(safeUnknownProjectCode(unknown.reasonClass()));}
        WeChatIdentityPort.Success success=(WeChatIdentityPort.Success)exchange;
        if(!expectedAppIdRef.equals(success.appIdRef())||!validProviderValue(success.providerSubject())||!validRequestRef(success.evidenceRef())){
            store.finishLoginAttempt(attemptRef,"UNKNOWN",providerCallRef,clock.instant());store.recordLoginWindowOutcome(windowKeyDigest,false,clock.instant());throw new Rejected("WECHAT_LOGIN_APPID_MISMATCH");
        }
        Instant sessionIssuedAt=clock.instant();
        String subjectDigest=hmac(pepper,success.providerSubject());byte[] bytes=new byte[32];random.nextBytes(bytes);
        if(!store.accountMayLogin(success.appIdRef(),subjectDigest))throw new Rejected("BUYER_ACCOUNT_CLOSURE_PENDING");
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); String tokenDigest=sha256(token);
        Instant absolute=sessionIssuedAt.plus(absoluteTtl),idle=sessionIssuedAt.plus(idleTtl);
        try {
            BuyerAuthStore.Identity identity;
            if(command==null) identity=store.establishIdentityAndSession(attemptRef,success.evidenceRef(),success.appIdRef(),subjectDigest,"BUYER-"+UUID.randomUUID(),UUID.randomUUID().toString(),tokenDigest,sessionIssuedAt,absolute,idle,
                    new BuyerAuthStore.Audit("WECHAT_SESSION_ESTABLISHED","SUCCEEDED",subjectDigest,tokenDigest,requestRef,sessionIssuedAt));
            else identity=store.establishIdentityConsentAndSession(attemptRef,success.evidenceRef(),success.appIdRef(),subjectDigest,success.providerSubject(),"BUYER-"+UUID.randomUUID(),UUID.randomUUID().toString(),tokenDigest,sessionIssuedAt,absolute,idle,
                    new BuyerAuthStore.Consent(command.guestRef(),requestRef,consentDigest(command),command.userAgreementVersion(),command.privacyPolicyVersion(),sessionIssuedAt),
                    new BuyerAuthStore.Audit("WECHAT_CONSENT_SESSION_ESTABLISHED","SUCCEEDED",subjectDigest,tokenDigest,requestRef,sessionIssuedAt));
            store.recordLoginWindowOutcome(windowKeyDigest,true,sessionIssuedAt);
            return new SessionResult(token,identity.subjectRef(),absolute);
        } catch(RuntimeException uncertain) { throw new Rejected("BUYER_SESSION_RESULT_UNKNOWN"); }
    }
    Optional<BuyerAuthStore.BuyerPrincipal> authenticate(String token) {
        if(!enabled||token==null||token.isBlank())return Optional.empty();
        if(currentUserAgreementVersion.isBlank()||currentPrivacyPolicyVersion.isBlank())return Optional.empty();
        Instant now=clock.instant();return store.authenticateAndAdvanceIdle(sha256(token),now,now.plus(idleTtl),
                currentUserAgreementVersion,currentPrivacyPolicyVersion);
    }
    BuyerAuthStore.LogoutResult logout(String token){if(!enabled||token==null||token.isBlank())return BuyerAuthStore.LogoutResult.UNAVAILABLE;try{return store.revokeCurrentSession(sha256(token),clock.instant());}catch(RuntimeException unknown){return BuyerAuthStore.LogoutResult.UNKNOWN;}}
    Optional<BuyerAuthStore.ConsentState> consentState(String subjectRef){return store.consentState(subjectRef);}
    private void validateConsent(LoginCommand c){
        if(c==null||!validRequestRef(c.guestRef())||!currentUserAgreementVersion.equals(c.userAgreementVersion())||!currentPrivacyPolicyVersion.equals(c.privacyPolicyVersion())
                ||!c.userAgreementAccepted()||!c.privacyPolicyAccepted()||currentUserAgreementVersion.isBlank()||currentPrivacyPolicyVersion.isBlank())
            throw new Rejected("BUYER_CONSENT_REQUIRED");
    }
    private static String consentDigest(LoginCommand c){return sha256(c.guestRef()+"\n"+c.requestRef()+"\n"+c.userAgreementVersion()+"\n"+c.privacyPolicyVersion()+"\ntrue\ntrue");}
    private static String hmac(byte[] key,String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("BUYER_IDENTITY_DIGEST_UNAVAILABLE");}}
    private static String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String safeUnknownProjectCode(String reason){return switch(reason==null?"":reason){
        case "WECHAT_PROVIDER_TIMEOUT"->"WECHAT_PROVIDER_TIMEOUT";
        case "WECHAT_PROVIDER_UNAVAILABLE"->"WECHAT_PROVIDER_UNAVAILABLE";
        case "WECHAT_HTTP_STATUS_UNKNOWN"->"WECHAT_PROVIDER_HTTP_UNKNOWN";
        case "WECHAT_RESPONSE_INVALID"->"WECHAT_PROVIDER_RESPONSE_INVALID";
        case "WECHAT_IDENTITY_INVALID"->"WECHAT_PROVIDER_IDENTITY_INVALID";
        case "WECHAT_PROVIDER_BUSY"->"WECHAT_PROVIDER_BUSY";
        case "REAL_PROVIDER_ADAPTER_DISABLED"->"BUYER_AUTH_CONFIGURATION_UNAVAILABLE";
        default->"WECHAT_LOGIN_RESULT_UNKNOWN";
    };}
    private static boolean validProviderValue(String v){return v!=null&&v.matches("[A-Za-z0-9._:-]{8,128}");}
    private static boolean validCode(String v){return v!=null&&!v.isBlank()&&v.length()<=256&&v.indexOf('\n')<0&&v.indexOf('\r')<0;}
    private static boolean validRequestRef(String v){return v!=null&&v.matches("[A-Za-z0-9._:-]{8,128}");}
    record SessionResult(String token,String subjectRef,Instant absoluteExpiresAt){}
    record LoginCommand(String code,String requestRef,String guestRef,String userAgreementVersion,String privacyPolicyVersion,
                        boolean userAgreementAccepted,boolean privacyPolicyAccepted){}
    static final class Rejected extends RuntimeException {final String projectCode;Rejected(){this("LOGIN_REQUEST_INVALID");}Rejected(String projectCode){super(projectCode);this.projectCode=projectCode;}}
}
