package com.huarenzaimeng.api.buyerauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("release-mysql")
final class BuyerAuthService {
    private final BuyerAuthStore store; private final boolean enabled; private final byte[] pepper;
    private final String expectedAppidDigest; private final Duration ttl; private final SecureRandom random = new SecureRandom();
    BuyerAuthService(BuyerAuthStore store, @Value("${hz.buyer-auth.enabled:false}") boolean enabled,
                     @Value("${hz.buyer-auth.expected-appid-digest:}") String expectedAppidDigest,
                     @Value("${hz.buyer-auth.identity-pepper:}") String pepper,
                     @Value("${hz.buyer-auth.session-hours:8}") long hours) {
        this.store=store; this.enabled=enabled; this.expectedAppidDigest=expectedAppidDigest;
        this.pepper=pepper.getBytes(StandardCharsets.UTF_8); this.ttl=Duration.ofHours(Math.max(1,Math.min(24,hours)));
    }
    SessionResult establish(String appid, String providerSubject, String requestId) {
        if (!enabled || !validProviderValue(appid) || !validProviderValue(providerSubject)) throw new Rejected();
        String appDigest=hmac(appid), subjectDigest=hmac(providerSubject);
        if (!MessageDigest.isEqual(expectedAppidDigest.getBytes(StandardCharsets.US_ASCII), appDigest.getBytes(StandardCharsets.US_ASCII))) throw new Rejected();
        Instant now=Instant.now(); byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); String tokenDigest=sha256(token);
        String subjectRef="BUYER-"+UUID.randomUUID();
        BuyerAuthStore.Identity identity=store.establishIdentityAndSession(appDigest,subjectDigest,subjectRef,UUID.randomUUID().toString(),tokenDigest,now,now.plus(ttl),
                new BuyerAuthStore.Audit("WECHAT_SESSION_ESTABLISHED","SUCCEEDED",subjectDigest,tokenDigest,safeRequestId(requestId),now));
        return new SessionResult(token,identity.subjectRef(),now.plus(ttl));
    }
    Optional<BuyerAuthStore.AuthenticatedBuyer> authenticate(String token) {
        return !enabled||token==null||token.isBlank()?Optional.empty():store.findActiveSession(sha256(token),Instant.now());
    }
    private String hmac(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(pepper,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("BUYER_IDENTITY_DIGEST_UNAVAILABLE");}}
    private static String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean validProviderValue(String v){return v!=null&&v.matches("[A-Za-z0-9_-]{8,128}");}
    private static String safeRequestId(String value) {
        return value != null && value.matches("[A-Za-z0-9._:-]{8,128}")
                ? value
                : UUID.randomUUID().toString();
    }
    record SessionResult(String token,String subjectRef,Instant expiresAt){}
    static final class Rejected extends RuntimeException {}
}
