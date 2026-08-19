package com.huarenzaimeng.api.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

final class WeChatPayApiV3Adapter implements WeChatPayPort {
    private final WeChatPayApiV3Transport transport;private final ObjectMapper json;private final Clock clock;private final SecureRandom random;
    private final String appId,merchantId,merchantSerial,notifyUrl;private final PrivateKey merchantKey;private final byte[] apiV3Key;private final Map<String,PublicKey> platformKeys;
    WeChatPayApiV3Adapter(WeChatPayApiV3Transport transport,ObjectMapper json,Clock clock,SecureRandom random,String appId,String merchantId,String merchantSerial,String notifyUrl,String privateKeyBase64,String apiV3Key,Map<String,String> platformKeys){
        this.transport=Objects.requireNonNull(transport);this.json=Objects.requireNonNull(json);this.clock=clock;this.random=random;this.appId=required(appId);this.merchantId=required(merchantId);this.merchantSerial=required(merchantSerial);this.notifyUrl=httpsUrl(notifyUrl);
        this.merchantKey=privateKey(privateKeyBase64);this.apiV3Key=required(apiV3Key).getBytes(StandardCharsets.UTF_8);if(this.apiV3Key.length!=32)throw new IllegalArgumentException("WECHAT_PAY_APIV3_KEY_INVALID");
        Map<String,PublicKey> keys=new HashMap<>();platformKeys.forEach((serial,key)->keys.put(required(serial),publicKey(key)));if(keys.isEmpty())throw new IllegalArgumentException("WECHAT_PAY_PLATFORM_KEYS_REQUIRED");this.platformKeys=Map.copyOf(keys);
    }
    public Result unifiedOrder(UnifiedOrder c){
        if(c.amountMinor()<=0||!"CNY".equals(c.currency())||blank(c.payerSubjectRef())||!merchantRef(c.merchantOrderRef(),32))return new Unknown("WECHAT_PAY_COMMAND_INVALID");
        String body=write(Map.of("appid",appId,"mchid",merchantId,"description","华人在孟充值服务","out_trade_no",c.merchantOrderRef(),"notify_url",notifyUrl,"amount",Map.of("total",c.amountMinor(),"currency","CNY"),"payer",Map.of("openid",c.payerSubjectRef())));
        WeChatPayApiV3Transport.Response response=call("POST","/v3/pay/transactions/jsapi",body);if(response.status()!=200)return classify(response.status());
        JsonNode root=read(response.body());String prepay=text(root,"prepay_id");if(blank(prepay))return new Unknown("WECHAT_PAY_PREPAY_ID_MISSING");
        String timestamp=Long.toString(clock.instant().getEpochSecond()),nonce=nonce(),packageValue="prepay_id="+prepay;
        String paySign=sign(appId+"\n"+timestamp+"\n"+nonce+"\n"+packageValue+"\n");
        return new Accepted(prepay,"PREPAY_CREATED",digest(response.body()),new PrepayParameters(timestamp,nonce,packageValue,"RSA",paySign));
    }
    public Result query(String order){if(!merchantRef(order,32))return new Unknown("WECHAT_PAY_COMMAND_INVALID");String path="/v3/pay/transactions/out-trade-no/"+enc(order)+"?mchid="+enc(merchantId);var r=call("GET",path,"");if(r.status()!=200)return classify(r.status());JsonNode n=read(r.body());return accepted(text(n,"transaction_id"),paymentState(text(n,"trade_state")),r.body());}
    public Result close(String order){if(!merchantRef(order,32))return new Unknown("WECHAT_PAY_COMMAND_INVALID");String path="/v3/pay/transactions/out-trade-no/"+enc(order)+"/close";var r=call("POST",path,write(Map.of("mchid",merchantId)));return r.status()==204?new Accepted(null,"CLOSED",digest("CLOSED:"+order)):classify(r.status());}
    public Result refund(Refund c){if(c.totalAmountMinor()<=0||!"CNY".equals(c.currency())||c.amountMinor()>c.totalAmountMinor()||!merchantRef(c.merchantOrderRef(),32)||!merchantRef(c.refundRef(),64))return new Unknown("WECHAT_REFUND_COMMAND_INVALID");String body=write(Map.of("out_trade_no",c.merchantOrderRef(),"out_refund_no",c.refundRef(),"amount",Map.of("refund",c.amountMinor(),"total",c.totalAmountMinor(),"currency","CNY")));var r=call("POST","/v3/refund/domestic/refunds",body);if(r.status()!=200)return classify(r.status());JsonNode n=read(r.body());return accepted(text(n,"refund_id"),refundState(text(n,"status")),r.body());}
    public RefundQueryResult queryRefundOriginal(String refund){var r=call("GET","/v3/refund/domestic/refunds/"+enc(refund),"");if(r.status()!=200)return new RefundQueryUnknown("WECHAT_REFUND_QUERY_UNKNOWN");JsonNode n=read(r.body());JsonNode amount=n.path("amount");return new RefundObservation(text(n,"out_refund_no"),text(n,"out_trade_no"),amount.path("refund").asLong(-1),text(amount,"currency"),refundState(text(n,"status")),digest(r.body()));}
    public NotificationResult verifyAndDecrypt(NotificationEnvelope e){
        try{PublicKey key=platformKeys.get(e.certificateSerial());if(key==null||!verify(key,e.timestamp()+"\n"+e.nonce()+"\n"+e.rawBody()+"\n",e.signature()))return new InvalidNotification("WECHAT_NOTIFICATION_SIGNATURE_INVALID");
            JsonNode outer=read(e.rawBody()),resource=outer.path("resource");String plain=decrypt(text(resource,"ciphertext"),text(resource,"associated_data"),text(resource,"nonce"));JsonNode n=read(plain),amount=n.path("amount");
            String state=paymentState(text(n,"trade_state"));Instant successTime=parseInstant(text(n,"success_time"));if("PAID".equals(state)&&successTime==null)throw new IllegalStateException("WECHAT_NOTIFICATION_SUCCESS_TIME_INVALID");
            return new VerifiedNotification(e.notificationId(),digest(e.rawBody()),text(n,"appid"),text(n,"mchid"),e.certificateSerial(),text(n,"out_trade_no"),text(n,"transaction_id"),amount.path("total").asLong(-1),text(amount,"currency"),state,successTime,digest(plain));
        }catch(RuntimeException invalid){return new NotificationUnknown("WECHAT_NOTIFICATION_UNKNOWN");}
    }
    private WeChatPayApiV3Transport.Response call(String method,String path,String body){String timestamp=Long.toString(clock.instant().getEpochSecond()),nonce=nonce();String authorization="WECHATPAY2-SHA256-RSA2048 mchid=\""+merchantId+"\",nonce_str=\""+nonce+"\",signature=\""+sign(method+"\n"+path+"\n"+timestamp+"\n"+nonce+"\n"+body+"\n")+"\",timestamp=\""+timestamp+"\",serial_no=\""+merchantSerial+"\"";var r=transport.execute(new WeChatPayApiV3Transport.Request(method,path,body,Map.of("Authorization",authorization,"Accept","application/json","Content-Type","application/json")));if(r.status()>=200&&r.status()<300&&!verifyResponse(r))throw new IllegalStateException("WECHAT_PAY_RESPONSE_SIGNATURE_INVALID");return r;}
    private boolean verifyResponse(WeChatPayApiV3Transport.Response r){String serial=r.headers().get("wechatpay-serial"),timestamp=r.headers().get("wechatpay-timestamp"),nonce=r.headers().get("wechatpay-nonce"),signature=r.headers().get("wechatpay-signature");PublicKey key=platformKeys.get(serial);return key!=null&&verify(key,timestamp+"\n"+nonce+"\n"+r.body()+"\n",signature);}
    private Accepted accepted(String provider,String state,String body){return new Accepted(provider,state,digest(body));}
    private Result classify(int status){return new Unknown("WECHAT_PAY_RESULT_UNKNOWN");}
    private String sign(String message){try{Signature s=Signature.getInstance("SHA256withRSA");s.initSign(merchantKey);s.update(message.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(s.sign());}catch(Exception e){throw new IllegalStateException("WECHAT_PAY_SIGNATURE_UNAVAILABLE");}}
    private static boolean verify(PublicKey key,String message,String encoded){try{Signature s=Signature.getInstance("SHA256withRSA");s.initVerify(key);s.update(message.getBytes(StandardCharsets.UTF_8));return s.verify(Base64.getDecoder().decode(encoded));}catch(Exception invalid){return false;}}
    private String decrypt(String cipherText,String associated,String nonce){try{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(apiV3Key,"AES"),new GCMParameterSpec(128,nonce.getBytes(StandardCharsets.UTF_8)));if(associated!=null)c.updateAAD(associated.getBytes(StandardCharsets.UTF_8));return new String(c.doFinal(Base64.getDecoder().decode(cipherText)),StandardCharsets.UTF_8);}catch(Exception invalid){throw new IllegalStateException("WECHAT_NOTIFICATION_DECRYPT_UNKNOWN");}}
    private String nonce(){byte[] b=new byte[16];random.nextBytes(b);return HexFormat.of().formatHex(b);}
    private JsonNode read(String body){try{return json.readTree(body);}catch(Exception invalid){throw new IllegalStateException("WECHAT_PAY_JSON_INVALID");}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception invalid){throw new IllegalStateException("WECHAT_PAY_JSON_UNAVAILABLE");}}
    private static String text(JsonNode node,String field){JsonNode v=node.path(field);return v.isTextual()?v.asText():null;}
    private static String paymentState(String state){return switch(state==null?"":state){case "SUCCESS"->"PAID";case "NOTPAY","USERPAYING"->"PROCESSING";case "CLOSED","REVOKED"->"CLOSED";case "PAYERROR"->"REJECTED";default->"UNKNOWN";};}
    private static String refundState(String state){return switch(state==null?"":state){case "SUCCESS"->"SUCCEEDED";case "CLOSED","ABNORMAL"->"REJECTED";case "PROCESSING"->"UNKNOWN";default->"UNKNOWN";};}
    private static PrivateKey privateKey(String b64){try{return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(required(b64))));}catch(Exception invalid){throw new IllegalArgumentException("WECHAT_PAY_PRIVATE_KEY_INVALID");}}
    private static PublicKey publicKey(String b64){try{return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(required(b64))));}catch(Exception invalid){throw new IllegalArgumentException("WECHAT_PAY_PLATFORM_KEY_INVALID");}}
    private static String required(String v){if(blank(v))throw new IllegalArgumentException("WECHAT_PAY_CONFIGURATION_REQUIRED");return v.trim();}
    private static String httpsUrl(String value){try{URI uri=URI.create(required(value));if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("WECHAT_PAY_NOTIFY_URL_INVALID");return uri.toASCIIString();}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("WECHAT_PAY_NOTIFY_URL_INVALID");}}
    private static boolean merchantRef(String value,int max){return value!=null&&value.length()<=max&&value.matches("[A-Za-z0-9_-]+");}
    private static boolean blank(String v){return v==null||v.isBlank();}private static String enc(String v){return URLEncoder.encode(required(v),StandardCharsets.UTF_8).replace("+","%20");}
    private static String digest(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException("WECHAT_PAY_DIGEST_UNAVAILABLE");}}
    private static Instant parseInstant(String value){try{return Instant.parse(value);}catch(Exception invalid){return null;}}
}
