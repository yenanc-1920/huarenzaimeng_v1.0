package com.huarenzaimeng.api.topup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** WINLA v5 protocol translation. This class never logs request fields or raw responses. */
final class WinlaHttpTransport implements WinlaTopupAdapter.Transport {
    static final String SUBMIT_URL="https://www.95cxmd.com/globe_api/public/topup";
    static final String QUERY_URL="https://www.95cxmd.com/globe_api/public/order";
    static final String BALANCE_URL="https://www.95cxmd.com/globe_api/public/dl/balance";
    private static final String CURRENCY="CNY";
    private final String uid; private final char[] apiKey; private final HttpExchange http; private final ObjectMapper json;

    WinlaHttpTransport(String uid,char[] apiKey,HttpExchange http,ObjectMapper json){
        if(uid==null||!uid.matches("[A-Za-z0-9_.-]{1,64}")||apiKey==null||apiKey.length<16||http==null||json==null)throw new IllegalArgumentException("WINLA_CONFIGURATION_INVALID");
        this.uid=uid;this.apiKey=apiKey.clone();this.http=http;this.json=json;
    }

    @Override public WinlaTopupAdapter.RawResult submit(TopupProviderPort.Command command){
        String phone=providerPhone(command.recipient());
        Map<String,String> fields=new LinkedHashMap<>();fields.put("recharge_no",phone);fields.put("user_order_no",order(command.merchantOrderRef()));fields.put("product_code",sku(command.providerSku()));fields.put("price",major(command.providerAmountMinor()));fields.put("uid",uid);
        JsonNode root=post(SUBMIT_URL,fields);if(code(root)!=10000)return unknownSubmit(command,"WINLA_SUBMIT_RESPONSE_UNKNOWN");JsonNode result=object(root,"result");
        String providerRef=text(result,"order_no",1,128);String merchant=text(result,"user_order_no",1,64);String amount=decimalText(result,"price");String digest=digest(canonicalJson(root));
        return new WinlaTopupAdapter.RawResult(requestRef("SUBMIT",merchant,providerRef,digest),merchant,providerRef,"PROCESSING",amount,WinlaTopupAdapter.AmountUnit.MAJOR,CURRENCY,null,null,digest,Instant.now(),"WINLA-SUBMIT-"+digest.substring(0,24),null);
    }

    @Override public WinlaTopupAdapter.RawResult query(String providerRef,String merchantOrderRef){
        Map<String,String> fields=new LinkedHashMap<>();fields.put("user_order_no",order(merchantOrderRef));fields.put("uid",uid);
        JsonNode root=post(QUERY_URL,fields);if(code(root)!=10000)return null;JsonNode result=object(root,"result");String merchant=text(result,"user_order_no",1,64);String returnedProvider=text(result,"order_no",1,128);String amount=decimalText(result,"price");String state=switch(integer(result,"state")){case 1->"PROCESSING";case 2->"DELIVERED";case 3->"REJECTED";default->"UNKNOWN";};String digest=digest(canonicalJson(root));
        return new WinlaTopupAdapter.RawResult(requestRef("QUERY",merchant,returnedProvider,digest),merchant,returnedProvider,state,amount,WinlaTopupAdapter.AmountUnit.MAJOR,CURRENCY,null,null,digest,Instant.now(),"WINLA-QUERY-"+digest.substring(0,24),"REJECTED".equals(state)?"WINLA_QUERY_REJECTED":null);
    }

    @Override public TopupProviderPort.BalanceResult balance(){
        Map<String,String> fields=new LinkedHashMap<>();fields.put("uid",uid);JsonNode root=post(BALANCE_URL,fields);if(code(root)!=10000)return new TopupProviderPort.BalanceUnknown("WINLA_BALANCE_RESPONSE_UNKNOWN");String raw=decimalText(object(root,"result"),"balance");try{long minor=new BigDecimal(raw).movePointRight(2).longValueExact();if(minor<0)return new TopupProviderPort.BalanceUnknown("WINLA_BALANCE_INVALID");String d=digest(canonicalJson(root));return new TopupProviderPort.BalanceObserved(minor,CURRENCY,"WINLA-BALANCE-"+d.substring(0,24),Instant.now());}catch(ArithmeticException e){return new TopupProviderPort.BalanceUnknown("WINLA_BALANCE_INVALID");}
    }

    @Override public WinlaTopupAdapter.RawCallback verifyCallback(TopupProviderPort.CallbackEnvelope envelope){
        Map<String,String> fields=form(envelope==null?null:envelope.body());String supplied=fields.remove("sign");if(supplied==null||!constantEquals(supplied,sign(fields,apiKey)))throw new IllegalStateException("WINLA_CALLBACK_SIGNATURE_INVALID");
        String merchant=required(fields,"user_order_no",64);String provider=required(fields,"order_no",128);String status=required(fields,"status",3);String amount=required(fields,"order_money",24);String state=switch(status){case "200"->"DELIVERED";case "201"->"PROCESSING";case "202"->"REJECTED";default->"UNKNOWN";};String callbackDigest=digest(canonical(fields));String callbackId="WINLA-CB-"+digest(provider+"|"+merchant+"|"+status+"|"+amount+"|"+callbackDigest).substring(0,48);
        return new WinlaTopupAdapter.RawCallback(requestRef("CALLBACK",merchant,provider,callbackDigest),callbackId,callbackDigest,merchant,provider,state,amount,WinlaTopupAdapter.AmountUnit.MINOR,CURRENCY,null,null,callbackDigest,Instant.now(),"WINLA-CALLBACK-"+callbackDigest.substring(0,24));
    }

    private JsonNode post(String url,Map<String,String> fields){Map<String,String> signed=new LinkedHashMap<>(fields);signed.put("sign",sign(fields,apiKey));byte[] response=http.post(url,formBody(signed));if(response==null||response.length==0||response.length>65536)throw new IllegalStateException("WINLA_RESPONSE_INVALID");try{JsonNode root=json.readTree(response);if(root==null||!root.isObject())throw new IllegalStateException("WINLA_RESPONSE_INVALID");return root;}catch(Exception e){throw new IllegalStateException("WINLA_RESPONSE_INVALID");}}
    private WinlaTopupAdapter.RawResult unknownSubmit(TopupProviderPort.Command c,String reason){String d=digest("UNKNOWN|"+c.merchantOrderRef());return new WinlaTopupAdapter.RawResult(requestRef("SUBMIT",c.merchantOrderRef(),"UNKNOWN",d),c.merchantOrderRef(),"UNKNOWN","UNKNOWN",major(c.providerAmountMinor()),WinlaTopupAdapter.AmountUnit.MAJOR,CURRENCY,null,null,d,Instant.now(),"WINLA-SUBMIT-UNKNOWN",reason);}
    static String sign(Map<String,String> fields,char[] key){StringBuilder b=new StringBuilder();new TreeMap<>(fields).forEach((k,v)->{if(!"sign".equals(k)&&v!=null&&!v.isEmpty()){if(b.length()>0)b.append('&');b.append(k).append('=').append(v);}});b.append(key);return md5(b.toString());}
    static String providerPhone(String canonical){String value=BangladeshPhoneNumber.normalize(canonical);if(!value.startsWith("880")||value.length()!=13)throw new IllegalArgumentException("WINLA_RECIPIENT_INVALID");return "0"+value.substring(3);}
    private static String major(long minor){if(minor<=0)throw new IllegalArgumentException("WINLA_AMOUNT_INVALID");return BigDecimal.valueOf(minor,2).stripTrailingZeros().toPlainString();}
    private static String sku(String value){if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("WINLA_PRODUCT_CODE_INVALID");return value;}
    private static String order(String value){if(value==null||!value.matches("[A-Za-z0-9]{1,32}"))throw new IllegalArgumentException("WINLA_ORDER_REF_INVALID");return value;}
    private static int code(JsonNode root){return integer(root,"code");}private static int integer(JsonNode node,String field){JsonNode v=node.get(field);if(v==null||!v.canConvertToInt())return Integer.MIN_VALUE;return v.intValue();}
    private static JsonNode object(JsonNode node,String field){JsonNode v=node.get(field);if(v==null||!v.isObject())throw new IllegalStateException("WINLA_RESPONSE_INVALID");return v;}
    private static String text(JsonNode node,String field,int min,int max){JsonNode v=node.get(field);if(v==null||!v.isValueNode())throw new IllegalStateException("WINLA_RESPONSE_INVALID");String s=v.asText();if(s.length()<min||s.length()>max)return required(Map.of(),field,max);return s;}
    private static String decimalText(JsonNode node,String field){JsonNode v=node.get(field);if(v==null||!v.isNumber()&&!v.isTextual())throw new IllegalStateException("WINLA_RESPONSE_INVALID");String s=v.asText();if(!s.matches("[0-9]+(?:\\.[0-9]{1,2})?"))throw new IllegalStateException("WINLA_RESPONSE_INVALID");return s;}
    private String canonicalJson(JsonNode node){try{return json.writeValueAsString(node);}catch(Exception e){throw new IllegalStateException("WINLA_RESPONSE_INVALID");}}
    private static String required(Map<String,String> fields,String name,int max){String v=fields.get(name);if(v==null||v.isBlank()||v.length()>max)throw new IllegalStateException("WINLA_CALLBACK_INVALID");return v;}
    private static Map<String,String> form(String body){if(body==null||body.length()>65536)throw new IllegalStateException("WINLA_CALLBACK_INVALID");Map<String,String> out=new LinkedHashMap<>();for(String pair:body.split("&")){int at=pair.indexOf('=');if(at<=0)throw new IllegalStateException("WINLA_CALLBACK_INVALID");String k=decode(pair.substring(0,at)),v=decode(pair.substring(at+1));if(out.putIfAbsent(k,v)!=null)throw new IllegalStateException("WINLA_CALLBACK_INVALID");}return out;}
    private static String formBody(Map<String,String> fields){StringBuilder b=new StringBuilder();fields.forEach((k,v)->{if(b.length()>0)b.append('&');b.append(encode(k)).append('=').append(encode(v));});return b.toString();}
    private static String canonical(Map<String,String> fields){StringBuilder b=new StringBuilder();new TreeMap<>(fields).forEach((k,v)->{if(b.length()>0)b.append('&');b.append(k).append('=').append(v);});return b.toString();}
    private static String encode(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}private static String decode(String v){return URLDecoder.decode(v,StandardCharsets.UTF_8);}
    private static boolean constantEquals(String a,String b){return MessageDigest.isEqual(a.toLowerCase().getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII));}
    private static String requestRef(String op,String order,String provider,String digest){return "WINLA-"+op+"-"+digest(op+"|"+order+"|"+provider+"|"+digest).substring(0,40);}
    private static String md5(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("WINLA_SIGNATURE_UNAVAILABLE");}}
    private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("WINLA_DIGEST_UNAVAILABLE");}}
    interface HttpExchange{byte[] post(String url,String formBody);}
}
