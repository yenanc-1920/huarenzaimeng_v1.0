package com.huarenzaimeng.api.buyerauth;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@Profile("release-mysql")
@RequestMapping("/buyer-auth/v1")
final class BuyerAuthController {
    private static final Set<String> LOGIN_FIELDS=Set.of("code","requestRef","guestRef","consent");
    private static final Set<String> CONSENT_FIELDS=Set.of("userAgreementVersion","privacyPolicyVersion","userAgreementAccepted","privacyPolicyAccepted");
    private static final Set<String> CLOSURE_FIELDS=Set.of("requestRef","reason","expectedVersion");
    private final BuyerAuthService auth; private final BuyerAccountLifecycleService lifecycle;
    @Autowired BuyerAuthController(BuyerAuthService auth,BuyerAccountLifecycleService lifecycle){this.auth=auth;this.lifecycle=lifecycle;}
    BuyerAuthController(BuyerAuthService auth){this(auth,null);}

    @PostMapping(value="/wechat/session",consumes=MediaType.APPLICATION_JSON_VALUE) ResponseEntity<?> session(@RequestBody JsonNode body,HttpServletRequest request){
        JsonNode consent=body.path("consent");
        if(request.getQueryString()!=null||!body.isObject()||!fieldNames(body).equals(LOGIN_FIELDS)||!consent.isObject()||!fieldNames(consent).equals(CONSENT_FIELDS)
                ||!body.path("code").isTextual()||!body.path("requestRef").isTextual()||!body.path("guestRef").isTextual()
                ||!consent.path("userAgreementVersion").isTextual()||!consent.path("privacyPolicyVersion").isTextual()
                ||!consent.path("userAgreementAccepted").isBoolean()||!consent.path("privacyPolicyAccepted").isBoolean())return failure(400,"LOGIN_REQUEST_INVALID","NEW_CODE_REQUIRED",text(body,"requestRef"));
        String requestRef=body.path("requestRef").textValue();
        try{
            var result=auth.establish(new BuyerAuthService.LoginCommand(body.path("code").textValue(),requestRef,body.path("guestRef").textValue(),
                    consent.path("userAgreementVersion").textValue(),consent.path("privacyPolicyVersion").textValue(),
                    consent.path("userAgreementAccepted").booleanValue(),consent.path("privacyPolicyAccepted").booleanValue()));
            Map<String,Object> response=envelope("AUTHENTICATED","BUYER_SESSION_CREATED",requestRef,"NONE");
            response.put("schemaVersion","BUYER_SESSION_V2");response.put("consentState","VALID");
            response.put("subjectRef",result.subjectRef());response.put("token",result.token());
            response.put("absoluteExpiresAt",result.absoluteExpiresAt().toString());
            return ResponseEntity.status(201).body(response);
        }catch(BuyerAuthService.Rejected rejected){return failure(status(rejected.projectCode),rejected.projectCode,retry(rejected.projectCode),requestRef);}
    }

    @GetMapping("/consent") ResponseEntity<?> consent(HttpServletRequest request){
        if(request.getQueryString()!=null||request.getContentLengthLong()>0)return ResponseEntity.badRequest().body(Map.of("projectCode","CONSENT_REQUEST_INVALID"));
        String header=request.getHeader("Authorization"),token=header!=null&&header.startsWith("Bearer ")?header.substring(7):null;
        var principal=auth.authenticate(token);if(principal.isEmpty())return ResponseEntity.status(401).body(Map.of("projectCode","BUYER_SESSION_UNAVAILABLE"));
        return auth.consentState(principal.get().subjectRef()).<ResponseEntity<?>>map(c->ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of(
                "schemaVersion","BUYER_CONSENT_V1","subjectRef",c.subjectRef(),"guestRef",c.guestRef(),"consentState",c.state(),
                "userAgreementVersion",c.userAgreementVersion(),"privacyPolicyVersion",c.privacyPolicyVersion(),"acceptedAt",c.acceptedAt(),"version",c.version())))
                .orElseGet(()->ResponseEntity.status(409).body(Map.of("projectCode","BUYER_CONSENT_REQUIRED")));
    }
    @PostMapping(value="/wechat/session",consumes=MediaType.ALL_VALUE) ResponseEntity<?> unsupportedMediaType(){return failure(400,"LOGIN_REQUEST_INVALID","NEW_CODE_REQUIRED",null);}

    @GetMapping("/session") ResponseEntity<?> current(HttpServletRequest request){
        if(request.getQueryString()!=null||request.getContentLengthLong()>0)return ResponseEntity.badRequest().body(Map.of("status","REJECTED","projectCode","SESSION_REQUEST_INVALID"));
        String header=request.getHeader("Authorization");String token=header!=null&&header.startsWith("Bearer ")?header.substring(7):null;
        return auth.authenticate(token).<ResponseEntity<?>>map(principal->ResponseEntity.ok()
                .header("Cache-Control","no-store").body(Map.of("outcome","ACCEPTED","projectCode","BUYER_SESSION_READY",
                        "subjectRef",principal.subjectRef(),"sessionRef",principal.sessionRef(),"role","BUYER")))
                .orElseGet(()->ResponseEntity.status(401).body(Map.of("outcome","REJECTED","projectCode","BUYER_SESSION_UNAVAILABLE")));
    }

    @PostMapping("/session/logout") ResponseEntity<?> logout(HttpServletRequest request){
        if(request.getQueryString()!=null||request.getContentLengthLong()>0)return ResponseEntity.badRequest().body(Map.of("status","REJECTED","projectCode","LOGOUT_REQUEST_INVALID"));
        String header=request.getHeader("Authorization");String token=header!=null&&header.startsWith("Bearer ")?header.substring(7):null;
        BuyerAuthStore.LogoutResult result=auth.logout(token);
        return switch(result){case SUCCEEDED->ResponseEntity.noContent().build();case UNAVAILABLE->ResponseEntity.status(401).body(Map.of("outcome","REJECTED","projectCode","BUYER_SESSION_UNAVAILABLE"));case UNKNOWN->ResponseEntity.status(503).body(Map.of("outcome","UNKNOWN","projectCode","BUYER_LOGOUT_RESULT_UNKNOWN"));};
    }

    @PostMapping(value="/account-closure-requests",consumes=MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> requestClosure(@RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body,HttpServletRequest request){
        if(lifecycle==null)return ResponseEntity.status(503).body(Map.of("projectCode","BUYER_CLOSURE_UNAVAILABLE"));
        if(request.getQueryString()!=null||!body.isObject()||!fieldNames(body).equals(CLOSURE_FIELDS))return ResponseEntity.badRequest().body(Map.of("projectCode","CLOSURE_REQUEST_INVALID"));
        var principal=principal(request);if(principal.isEmpty())return ResponseEntity.status(401).body(Map.of("projectCode","BUYER_SESSION_UNAVAILABLE"));
        try{return ResponseEntity.status(202).header("Cache-Control","no-store").body(lifecycle.request(principal.get().subjectRef(),key,body));}
        catch(BuyerAccountLifecycleService.Conflict c){return ResponseEntity.status(409).body(Map.of("projectCode",c.getMessage()));}
        catch(IllegalArgumentException invalid){return ResponseEntity.badRequest().body(Map.of("projectCode",invalid.getMessage()));}
    }

    private java.util.Optional<BuyerAuthStore.BuyerPrincipal> principal(HttpServletRequest request){
        String header=request.getHeader("Authorization"),token=header!=null&&header.startsWith("Bearer ")?header.substring(7):null;return auth.authenticate(token);
    }

    static ResponseEntity<?> failure(int status,String code,String retry,String requestRef){return ResponseEntity.status(status).body(envelope("REJECTED",code,requestRef,retry));}
    static LinkedHashMap<String,Object> envelope(String outcome,String code,String requestRef,String retry){LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("outcome",outcome);m.put("projectCode",code);m.put("requestRef",requestRef);m.put("subjectRef",null);m.put("token",null);m.put("absoluteExpiresAt",null);m.put("retryClass",retry);return m;}
    private static Set<String> fieldNames(JsonNode node){java.util.HashSet<String>s=new java.util.HashSet<>();node.fieldNames().forEachRemaining(s::add);return s;}
    private static String text(JsonNode n,String key){return n.path(key).isTextual()&&n.path(key).textValue().matches("[A-Za-z0-9._:-]{8,128}")?n.path(key).textValue():null;}
    private static int status(String code){return switch(code){case"BUYER_AUTH_DISABLED","BUYER_AUTH_CONFIGURATION_UNAVAILABLE","WECHAT_LOGIN_RESULT_UNKNOWN","WECHAT_PROVIDER_TIMEOUT","WECHAT_PROVIDER_DNS_FAILURE","WECHAT_PROVIDER_TLS_FAILURE","WECHAT_PROVIDER_CONNECTION_FAILED","WECHAT_PROVIDER_UNAVAILABLE","WECHAT_PROVIDER_HTTP_UNKNOWN","WECHAT_PROVIDER_RESPONSE_INVALID","WECHAT_PROVIDER_IDENTITY_INVALID","WECHAT_PROVIDER_BUSY","BUYER_SESSION_RESULT_UNKNOWN"->503;case"LOGIN_CODE_ALREADY_SUBMITTED","BUYER_ACCOUNT_CLOSURE_PENDING","BUYER_CONSENT_REQUIRED"->409;case"WECHAT_LOGIN_REJECTED"->401;default->400;};}
    private static String retry(String code){return code.equals("BUYER_AUTH_DISABLED")||code.equals("BUYER_AUTH_CONFIGURATION_UNAVAILABLE")?"NOT_RETRYABLE":"NEW_CODE_REQUIRED";}
}
