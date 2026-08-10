package com.huarenzaimeng.api.buyerauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("release-mysql")
final class TrustedWechatIngressVerifier {
    static final String APPID_HEADER="X-WX-APPID",SUBJECT_HEADER="X-WX-OPENID",REQUEST_ID_HEADER="X-Request-Id";
    static final String TIMESTAMP_HEADER="X-HZM-Trusted-Timestamp",NONCE_HEADER="X-HZM-Trusted-Nonce";
    static final String SIGNATURE_HEADER="X-HZM-Trusted-Ingress-Signature";
    private static final long MAX_SKEW_SECONDS=60;
    private final boolean enabled;private final String trustedIngress;private final byte[] secret;private final Clock clock;
    private final ConcurrentHashMap<String,Long> acceptedNonces=new ConcurrentHashMap<>();

    TrustedWechatIngressVerifier(@Value("${hz.buyer-auth.enabled:false}") boolean enabled,
            @Value("${hz.buyer-auth.trusted-ingress:}") String trustedIngress,
            @Value("${hz.buyer-auth.ingress-hmac-secret:}") String secret){this(enabled,trustedIngress,secret,Clock.systemUTC());}
    TrustedWechatIngressVerifier(boolean enabled,String trustedIngress,String secret,Clock clock){
        this.enabled=enabled;this.trustedIngress=trustedIngress;this.secret=secret.getBytes(StandardCharsets.UTF_8);this.clock=clock;
    }

    TrustedWechatIdentity verify(HttpServletRequest request){
        if(!enabled||!"WECHAT_CLOUD_HOSTING_HMAC_V1".equals(trustedIngress)||secret.length<32)throw new Rejected();
        String appid=request.getHeader(APPID_HEADER),subject=request.getHeader(SUBJECT_HEADER),requestId=request.getHeader(REQUEST_ID_HEADER);
        String timestamp=request.getHeader(TIMESTAMP_HEADER),nonce=request.getHeader(NONCE_HEADER),signature=request.getHeader(SIGNATURE_HEADER);
        if(!safe(appid,"[A-Za-z0-9_-]{8,128}")||!safe(subject,"[A-Za-z0-9_-]{8,128}")
                ||!safe(requestId,"[A-Za-z0-9._:-]{8,128}")||!safe(nonce,"[A-Za-z0-9_-]{16,128}")
                ||timestamp==null||!timestamp.matches("[0-9]{10}")||signature==null||!signature.matches("[0-9a-f]{64}"))throw new Rejected();
        long epoch;try{epoch=Long.parseLong(timestamp);}catch(NumberFormatException invalid){throw new Rejected();}
        long now=clock.instant().getEpochSecond();if(Math.abs(now-epoch)>MAX_SKEW_SECONDS)throw new Rejected();
        String canonical=canonical(request.getMethod(),request.getRequestURI(),timestamp,nonce,appid,subject,requestId);
        if(!MessageDigest.isEqual(hmac(canonical).getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII)))throw new Rejected();
        acceptedNonces.entrySet().removeIf(entry->entry.getValue()<now-MAX_SKEW_SECONDS);
        if(acceptedNonces.putIfAbsent(nonce,epoch)!=null)throw new Rejected();
        return new TrustedWechatIdentity(appid,subject,requestId);
    }
    static String canonical(String method,String path,String timestamp,String nonce,String appid,String subject,String requestId){
        return String.join("\n",method,path,timestamp,nonce,appid,subject,requestId);
    }
    String signForTest(String canonical){return hmac(canonical);}
    private String hmac(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException("TRUSTED_INGRESS_VERIFICATION_UNAVAILABLE");}}
    private static boolean safe(String value,String pattern){return value!=null&&value.matches(pattern);}
    record TrustedWechatIdentity(String appid,String providerSubject,String requestId){}
    static final class Rejected extends RuntimeException {}
}
