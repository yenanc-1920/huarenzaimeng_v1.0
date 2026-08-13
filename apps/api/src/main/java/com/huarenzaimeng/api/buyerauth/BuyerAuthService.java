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
    private final BuyerAuthStore store; private final WechatCode2SessionPort provider; private final boolean enabled;
    private final byte[] pepper; private final Duration absoluteTtl; private final Duration idleTtl;
    private final SecureRandom random; private final Clock clock;
    @Autowired
    BuyerAuthService(BuyerAuthStore store, @Value("${hz.buyer-auth.enabled:false}") boolean enabled,
                     WechatCode2SessionPort provider,
                     @Value("${hz.buyer-auth.identity-pepper:}") String pepper,
                     @Value("${hz.buyer-auth.code-pepper:}") String codePepper) {
        this(store, provider, enabled, pepper, codePepper, Clock.systemUTC(), new SecureRandom());
    }
    BuyerAuthService(BuyerAuthStore store, WechatCode2SessionPort provider, boolean enabled,
                     String identityPepper, String codePepper, Clock clock, SecureRandom random) {
        this.store=store;this.provider=provider;this.enabled=enabled;this.clock=clock;this.random=random;
        this.pepper=identityPepper.getBytes(StandardCharsets.UTF_8);
        if (enabled && (identityPepper.length()<32 || codePepper.length()<32 || identityPepper.equals(codePepper))) throw new Rejected("BUYER_AUTH_CONFIGURATION_UNAVAILABLE");
        this.codePepper=codePepper.getBytes(StandardCharsets.UTF_8);this.absoluteTtl=Duration.ofHours(24);this.idleTtl=Duration.ofHours(2);
    }
    private final byte[] codePepper;
    SessionResult establish(String code, String requestRef) {
        if (!enabled) throw new Rejected("BUYER_AUTH_DISABLED");
        if (!validCode(code) || !validRequestRef(requestRef)) throw new Rejected("LOGIN_REQUEST_INVALID");
        Instant attemptStartedAt=clock.instant();String attemptRef="LOGIN-"+UUID.randomUUID();String providerCallRef="CALL-"+UUID.randomUUID();
        String codeDigest=hmac(codePepper,code);
        if(!store.beginLoginAttempt("RELEASE","WECHAT_PRIMARY",codeDigest,attemptRef,requestRef,attemptStartedAt)) throw new Rejected("LOGIN_CODE_ALREADY_SUBMITTED");
        WechatCode2SessionPort.Result exchange;
        try { exchange=provider.exchange(new WechatCode2SessionPort.Command(code,providerCallRef)); }
        catch(RuntimeException failure){exchange=new WechatCode2SessionPort.Unknown("CONTROLLED_PROVIDER_FAILURE");}
        if(exchange instanceof WechatCode2SessionPort.Rejected){store.finishLoginAttempt(attemptRef,"REJECTED",providerCallRef,clock.instant());throw new Rejected("WECHAT_LOGIN_REJECTED");}
        if(exchange instanceof WechatCode2SessionPort.Unknown){store.finishLoginAttempt(attemptRef,"UNKNOWN",providerCallRef,clock.instant());throw new Rejected("WECHAT_LOGIN_RESULT_UNKNOWN");}
        WechatCode2SessionPort.Success success=(WechatCode2SessionPort.Success)exchange;
        if(!validProviderValue(success.appIdRef())||!validProviderValue(success.providerSubject())||!validRequestRef(success.evidenceRef())){
            store.finishLoginAttempt(attemptRef,"UNKNOWN",providerCallRef,clock.instant());throw new Rejected("WECHAT_LOGIN_RESULT_UNKNOWN");
        }
        Instant sessionIssuedAt=clock.instant();
        String subjectDigest=hmac(pepper,success.providerSubject());byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); String tokenDigest=sha256(token);
        Instant absolute=sessionIssuedAt.plus(absoluteTtl),idle=sessionIssuedAt.plus(idleTtl);
        try {
            BuyerAuthStore.Identity identity=store.establishIdentityAndSession(attemptRef,success.evidenceRef(),success.appIdRef(),subjectDigest,"BUYER-"+UUID.randomUUID(),UUID.randomUUID().toString(),tokenDigest,sessionIssuedAt,absolute,idle,
                    new BuyerAuthStore.Audit("WECHAT_SESSION_ESTABLISHED","SUCCEEDED",subjectDigest,tokenDigest,requestRef,sessionIssuedAt));
            return new SessionResult(token,identity.subjectRef(),absolute);
        } catch(RuntimeException uncertain) { throw new Rejected("BUYER_SESSION_RESULT_UNKNOWN"); }
    }
    Optional<BuyerAuthStore.BuyerPrincipal> authenticate(String token) {
        if(!enabled||token==null||token.isBlank())return Optional.empty();
        Instant now=clock.instant();return store.authenticateAndAdvanceIdle(sha256(token),now,now.plus(idleTtl));
    }
    BuyerAuthStore.LogoutResult logout(String token){if(!enabled||token==null||token.isBlank())return BuyerAuthStore.LogoutResult.UNAVAILABLE;try{return store.revokeCurrentSession(sha256(token),clock.instant());}catch(RuntimeException unknown){return BuyerAuthStore.LogoutResult.UNKNOWN;}}
    private static String hmac(byte[] key,String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("BUYER_IDENTITY_DIGEST_UNAVAILABLE");}}
    private static String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean validProviderValue(String v){return v!=null&&v.matches("[A-Za-z0-9._:-]{8,128}");}
    private static boolean validCode(String v){return v!=null&&!v.isBlank()&&v.length()<=256&&v.indexOf('\n')<0&&v.indexOf('\r')<0;}
    private static boolean validRequestRef(String v){return v!=null&&v.matches("[A-Za-z0-9._:-]{8,128}");}
    record SessionResult(String token,String subjectRef,Instant absoluteExpiresAt){}
    static final class Rejected extends RuntimeException {final String projectCode;Rejected(){this("LOGIN_REQUEST_INVALID");}Rejected(String projectCode){super(projectCode);this.projectCode=projectCode;}}
}
