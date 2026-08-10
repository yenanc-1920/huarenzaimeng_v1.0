package com.huarenzaimeng.api.buyerauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedWechatIngressVerifierTest {
    private static final Instant NOW=Instant.parse("2026-08-10T00:00:00Z");
    private static final String SECRET="synthetic-independent-ingress-secret-123456789";
    private static final String APPID="wx_appid_12345678",SUBJECT="openid_12345678",REQUEST="REQ-TRUST-0001";

    @Test void validSignedRequestCreatesTrustedIdentityAndReplayIsRejected(){
        var verifier=verifier(true);var request=signed(verifier,"NONCE-TRUST-00000001",NOW);
        var identity=verifier.verify(request);
        assertThat(identity.appid()).isEqualTo(APPID);assertThat(identity.providerSubject()).isEqualTo(SUBJECT);
        assertThatThrownBy(()->verifier.verify(request)).isInstanceOf(TrustedWechatIngressVerifier.Rejected.class);
    }
    @Test void publicHeadersWithoutValidSignatureAndExpiredSignatureFailClosed(){
        var verifier=verifier(true);var unsigned=new MockHttpServletRequest("POST","/buyer-auth/v1/wechat/session");
        unsigned.addHeader(TrustedWechatIngressVerifier.APPID_HEADER,APPID);unsigned.addHeader(TrustedWechatIngressVerifier.SUBJECT_HEADER,SUBJECT);
        assertThatThrownBy(()->verifier.verify(unsigned)).isInstanceOf(TrustedWechatIngressVerifier.Rejected.class);
        assertThatThrownBy(()->verifier.verify(signed(verifier,"NONCE-TRUST-00000002",NOW.minusSeconds(61))))
                .isInstanceOf(TrustedWechatIngressVerifier.Rejected.class);
    }
    @Test void disabledOrUnprovenPlatformBoundaryFailsClosed(){
        assertThatThrownBy(()->verifier(false).verify(new MockHttpServletRequest("POST","/buyer-auth/v1/wechat/session")))
                .isInstanceOf(TrustedWechatIngressVerifier.Rejected.class);
        var wrongIngress=new TrustedWechatIngressVerifier(true,"WECHAT_CLOUD_HOSTING",SECRET,Clock.fixed(NOW,ZoneOffset.UTC));
        assertThatThrownBy(()->wrongIngress.verify(signed(wrongIngress,"NONCE-TRUST-00000003",NOW)))
                .isInstanceOf(TrustedWechatIngressVerifier.Rejected.class);
    }
    @Test void knownAppidAndArbitraryOpenidWithoutSignatureCannotReachIdentityWrite(){
        var store=new CountingStore();
        var service=new BuyerAuthService(store,true,hmacWith("identity-pepper-value-with-at-least-32-characters",APPID),
                "identity-pepper-value-with-at-least-32-characters",8);
        var controller=new BuyerAuthController(service,verifier(true));
        var request=new MockHttpServletRequest("POST","/buyer-auth/v1/wechat/session");
        request.addHeader(TrustedWechatIngressVerifier.APPID_HEADER,APPID);
        request.addHeader(TrustedWechatIngressVerifier.SUBJECT_HEADER,"arbitrary_openid_12345678");
        assertThatThrownBy(()->controller.session(request)).isInstanceOf(TrustedWechatIngressVerifier.Rejected.class);
        assertThat(store.writeCount).isZero();
    }
    private static TrustedWechatIngressVerifier verifier(boolean enabled){return new TrustedWechatIngressVerifier(enabled,"WECHAT_CLOUD_HOSTING_HMAC_V1",SECRET,Clock.fixed(NOW,ZoneOffset.UTC));}
    private static MockHttpServletRequest signed(TrustedWechatIngressVerifier verifier,String nonce,Instant timestamp){
        var request=new MockHttpServletRequest("POST","/buyer-auth/v1/wechat/session");String time=Long.toString(timestamp.getEpochSecond());
        request.addHeader(TrustedWechatIngressVerifier.APPID_HEADER,APPID);request.addHeader(TrustedWechatIngressVerifier.SUBJECT_HEADER,SUBJECT);
        request.addHeader(TrustedWechatIngressVerifier.REQUEST_ID_HEADER,REQUEST);request.addHeader(TrustedWechatIngressVerifier.TIMESTAMP_HEADER,time);
        request.addHeader(TrustedWechatIngressVerifier.NONCE_HEADER,nonce);
        request.addHeader(TrustedWechatIngressVerifier.SIGNATURE_HEADER,verifier.signForTest(TrustedWechatIngressVerifier.canonical("POST","/buyer-auth/v1/wechat/session",time,nonce,APPID,SUBJECT,REQUEST)));
        return request;
    }
    private static String hmacWith(String secret,String value){
        try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(Exception error){throw new IllegalStateException(error);}
    }
    private static final class CountingStore implements BuyerAuthStore{
        int writeCount;
        @Override public Identity establishIdentityAndSession(String appidDigest,String subjectDigest,String subjectRef,String sessionId,String tokenDigest,Instant issuedAt,Instant expiresAt,Audit audit){writeCount++;return new Identity("buyer",subjectRef);}
        @Override public Optional<AuthenticatedBuyer> findActiveSession(String tokenDigest,Instant now){return Optional.empty();}
    }
}
