package com.huarenzaimeng.api.buyerauth;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
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
    private static final Set<String> LOGIN_FIELDS=Set.of("code","requestRef");
    private final BuyerAuthService auth;
    BuyerAuthController(BuyerAuthService auth){this.auth=auth;}

    @PostMapping(value="/wechat/session",consumes=MediaType.APPLICATION_JSON_VALUE) ResponseEntity<?> session(@RequestBody JsonNode body,HttpServletRequest request){
        if(request.getQueryString()!=null||!body.isObject()||!fieldNames(body).equals(LOGIN_FIELDS)
                ||!body.path("code").isTextual()||!body.path("requestRef").isTextual())return failure(400,"LOGIN_REQUEST_INVALID","NEW_CODE_REQUIRED",text(body,"requestRef"));
        String requestRef=body.path("requestRef").textValue();
        try{
            var result=auth.establish(body.path("code").textValue(),requestRef);
            Map<String,Object> response=envelope("AUTHENTICATED","BUYER_SESSION_CREATED",requestRef,"NONE");
            response.put("subjectRef",result.subjectRef());response.put("token",result.token());
            response.put("absoluteExpiresAt",result.absoluteExpiresAt().toString());
            return ResponseEntity.status(201).body(response);
        }catch(BuyerAuthService.Rejected rejected){return failure(status(rejected.projectCode),rejected.projectCode,retry(rejected.projectCode),requestRef);}
    }
    @PostMapping(value="/wechat/session",consumes=MediaType.ALL_VALUE) ResponseEntity<?> unsupportedMediaType(){return failure(400,"LOGIN_REQUEST_INVALID","NEW_CODE_REQUIRED",null);}

    @PostMapping("/session/logout") ResponseEntity<?> logout(HttpServletRequest request){
        if(request.getQueryString()!=null||request.getContentLengthLong()>0)return ResponseEntity.badRequest().body(Map.of("status","REJECTED","projectCode","LOGOUT_REQUEST_INVALID"));
        String header=request.getHeader("Authorization");String token=header!=null&&header.startsWith("Bearer ")?header.substring(7):null;
        BuyerAuthStore.LogoutResult result=auth.logout(token);
        return switch(result){case SUCCEEDED->ResponseEntity.noContent().build();case UNAVAILABLE->ResponseEntity.status(401).body(Map.of("outcome","REJECTED","projectCode","BUYER_SESSION_UNAVAILABLE"));case UNKNOWN->ResponseEntity.status(503).body(Map.of("outcome","UNKNOWN","projectCode","BUYER_LOGOUT_RESULT_UNKNOWN"));};
    }

    static ResponseEntity<?> failure(int status,String code,String retry,String requestRef){return ResponseEntity.status(status).body(envelope("REJECTED",code,requestRef,retry));}
    static LinkedHashMap<String,Object> envelope(String outcome,String code,String requestRef,String retry){LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("outcome",outcome);m.put("projectCode",code);m.put("requestRef",requestRef);m.put("subjectRef",null);m.put("token",null);m.put("absoluteExpiresAt",null);m.put("retryClass",retry);return m;}
    private static Set<String> fieldNames(JsonNode node){java.util.HashSet<String>s=new java.util.HashSet<>();node.fieldNames().forEachRemaining(s::add);return s;}
    private static String text(JsonNode n,String key){return n.path(key).isTextual()&&n.path(key).textValue().matches("[A-Za-z0-9._:-]{8,128}")?n.path(key).textValue():null;}
    private static int status(String code){return switch(code){case"BUYER_AUTH_DISABLED","BUYER_AUTH_CONFIGURATION_UNAVAILABLE","WECHAT_LOGIN_RESULT_UNKNOWN","BUYER_SESSION_RESULT_UNKNOWN"->503;case"LOGIN_CODE_ALREADY_SUBMITTED"->409;case"WECHAT_LOGIN_REJECTED"->401;default->400;};}
    private static String retry(String code){return code.equals("BUYER_AUTH_DISABLED")||code.equals("BUYER_AUTH_CONFIGURATION_UNAVAILABLE")?"NOT_RETRYABLE":"NEW_CODE_REQUIRED";}
}
