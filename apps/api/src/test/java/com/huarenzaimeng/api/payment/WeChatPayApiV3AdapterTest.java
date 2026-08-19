package com.huarenzaimeng.api.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class WeChatPayApiV3AdapterTest {
    private static final String API_KEY="12345678901234567890123456789012";
    @Test void unifiedOrderUsesTrustedOpenidAndReturnsSignedFiveParameters(){
        Keys keys=keys();FakeTransport transport=new FakeTransport(keys.platform().getPrivate(),"{\"prepay_id\":\"wx-prepay-1\"}",200);
        WeChatPayApiV3Adapter adapter=adapter(keys,transport);
        WeChatPayPort.Accepted accepted=(WeChatPayPort.Accepted)adapter.unifiedOrder(new WeChatPayPort.UnifiedOrder("ORDER-1",525,"CNY","openid-trusted","a".repeat(64)));
        assertThat(transport.request.pathAndQuery()).isEqualTo("/v3/pay/transactions/jsapi");
        assertThat(transport.request.body()).contains("\"openid\":\"openid-trusted\"","\"total\":525").doesNotContain(API_KEY);
        assertThat(transport.request.headers().get("Authorization")).startsWith("WECHATPAY2-SHA256-RSA2048 ").doesNotContain(API_KEY,"openid-trusted");
        assertThat(accepted.prepayParameters()).satisfies(p->{assertThat(p.timeStamp()).isEqualTo("1787097600");assertThat(p.packageValue()).isEqualTo("prepay_id=wx-prepay-1");assertThat(p.signType()).isEqualTo("RSA");assertThat(p.paySign()).isNotBlank();});
    }
    @Test void responseSignatureFailureIsUnknownToCoordinatorBoundary(){Keys keys=keys();FakeTransport transport=new FakeTransport(keys.merchant().getPrivate(),"{\"prepay_id\":\"x\"}",200);WeChatPayApiV3Adapter adapter=adapter(keys,transport);assertThatThrownBy(()->adapter.unifiedOrder(new WeChatPayPort.UnifiedOrder("O",1,"CNY","openid","a".repeat(64)))).hasMessage("WECHAT_PAY_RESPONSE_SIGNATURE_INVALID");}
    @Test void verifiesAndDecryptsOfficialNotificationEnvelope(){
        Keys keys=keys();WeChatPayApiV3Adapter adapter=adapter(keys,new FakeTransport(keys.platform().getPrivate(),"{}",500));
        String plain="{\"appid\":\"wx-app\",\"mchid\":\"mch-1\",\"out_trade_no\":\"ORDER-1\",\"transaction_id\":\"WX-1\",\"trade_state\":\"SUCCESS\",\"success_time\":\"2026-08-19T00:00:00Z\",\"amount\":{\"total\":525,\"currency\":\"CNY\"}}";
        String resource=encrypt(plain,"aad","012345678901",API_KEY),raw="{\"resource\":{\"ciphertext\":\""+resource+"\",\"associated_data\":\"aad\",\"nonce\":\"012345678901\"}}",timestamp="1787097600",nonce="notice-nonce";
        String signature=sign(keys.platform().getPrivate(),timestamp+"\n"+nonce+"\n"+raw+"\n");
        var result=adapter.verifyAndDecrypt(new WeChatPayPort.NotificationEnvelope("N1",timestamp,nonce,signature,"PLATFORM-1",raw));
        assertThat(result).isInstanceOfSatisfying(WeChatPayPort.VerifiedNotification.class,n->{assertThat(n.merchantOrderRef()).isEqualTo("ORDER-1");assertThat(n.amountMinor()).isEqualTo(525);assertThat(n.state()).isEqualTo("PAID");});
    }
    @Test void unknownPlatformSerialFailsClosed(){Keys keys=keys();var adapter=adapter(keys,new FakeTransport(keys.platform().getPrivate(),"{}",500));assertThat(adapter.verifyAndDecrypt(new WeChatPayPort.NotificationEnvelope("N","1","n","bad","OTHER","{}"))).isInstanceOf(WeChatPayPort.InvalidNotification.class);}
    @Test void invalidMerchantReferenceAndNonSuccessHttpRemainUnknown(){Keys keys=keys();FakeTransport transport=new FakeTransport(keys.platform().getPrivate(),"{}",400);var adapter=adapter(keys,transport);assertThat(adapter.unifiedOrder(new WeChatPayPort.UnifiedOrder("X".repeat(33),525,"CNY","openid","a".repeat(64)))).isInstanceOf(WeChatPayPort.Unknown.class);assertThat(transport.request).isNull();assertThat(adapter.unifiedOrder(new WeChatPayPort.UnifiedOrder("ORDER-1",525,"CNY","openid","a".repeat(64)))).isInstanceOf(WeChatPayPort.Unknown.class);}
    @Test void paidNotificationWithoutValidSuccessTimeFailsClosed(){Keys keys=keys();WeChatPayApiV3Adapter adapter=adapter(keys,new FakeTransport(keys.platform().getPrivate(),"{}",500));String plain="{\"appid\":\"wx-app\",\"mchid\":\"mch-1\",\"out_trade_no\":\"ORDER-1\",\"transaction_id\":\"WX-1\",\"trade_state\":\"SUCCESS\",\"amount\":{\"total\":525,\"currency\":\"CNY\"}}";String resource=encrypt(plain,"aad","012345678901",API_KEY),raw="{\"resource\":{\"ciphertext\":\""+resource+"\",\"associated_data\":\"aad\",\"nonce\":\"012345678901\"}}",timestamp="1787097600",nonce="notice-nonce",signature=sign(keys.platform().getPrivate(),timestamp+"\n"+nonce+"\n"+raw+"\n");assertThat(adapter.verifyAndDecrypt(new WeChatPayPort.NotificationEnvelope("N1",timestamp,nonce,signature,"PLATFORM-1",raw))).isInstanceOf(WeChatPayPort.NotificationUnknown.class);}
    @Test void nonHttpsNotificationUrlIsRejected(){Keys keys=keys();assertThatThrownBy(()->new WeChatPayApiV3Adapter(new FakeTransport(keys.platform().getPrivate(),"{}",500),new ObjectMapper(),Clock.systemUTC(),new SecureRandom(),"wx-app","mch-1","MERCHANT-1","http://api.guiye.xyz/callback",Base64.getEncoder().encodeToString(keys.merchant().getPrivate().getEncoded()),API_KEY,Map.of("PLATFORM-1",Base64.getEncoder().encodeToString(keys.platform().getPublic().getEncoded())))).hasMessage("WECHAT_PAY_NOTIFY_URL_INVALID");}
    private static WeChatPayApiV3Adapter adapter(Keys k,WeChatPayApiV3Transport transport){return new WeChatPayApiV3Adapter(transport,new ObjectMapper(),Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"),ZoneOffset.UTC),new SecureRandom(new byte[]{1,2,3}),"wx-app","mch-1","MERCHANT-1","https://api.guiye.xyz/provider-callback/v1/wechat-pay/notifications",Base64.getEncoder().encodeToString(k.merchant().getPrivate().getEncoded()),API_KEY,Map.of("PLATFORM-1",Base64.getEncoder().encodeToString(k.platform().getPublic().getEncoded())));}
    private static Keys keys(){try{KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);return new Keys(g.generateKeyPair(),g.generateKeyPair());}catch(Exception e){throw new IllegalStateException(e);}}
    private static String sign(PrivateKey key,String text){try{Signature s=Signature.getInstance("SHA256withRSA");s.initSign(key);s.update(text.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(s.sign());}catch(Exception e){throw new IllegalStateException(e);}}
    private static String encrypt(String plain,String aad,String nonce,String key){try{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES"),new GCMParameterSpec(128,nonce.getBytes(StandardCharsets.UTF_8)));c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(c.doFinal(plain.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    record Keys(KeyPair merchant,KeyPair platform){}
    static final class FakeTransport implements WeChatPayApiV3Transport{final PrivateKey signer;final String body;final int status;Request request;FakeTransport(PrivateKey signer,String body,int status){this.signer=signer;this.body=body;this.status=status;}public Response execute(Request r){request=r;String timestamp="1787097600",nonce="response-nonce",signature=sign(signer,timestamp+"\n"+nonce+"\n"+body+"\n");return new Response(status,body,Map.of("wechatpay-serial","PLATFORM-1","wechatpay-timestamp",timestamp,"wechatpay-nonce",nonce,"wechatpay-signature",signature));}}
}
