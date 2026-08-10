package com.huarenzaimeng.api.buyerauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @Profile("release-mysql") @RequestMapping("/buyer-auth/v1")
final class BuyerAuthController {
    private final BuyerAuthService auth; private final TrustedWechatIngressVerifier ingress;
    BuyerAuthController(BuyerAuthService auth, TrustedWechatIngressVerifier ingress){this.auth=auth;this.ingress=ingress;}
    @PostMapping("/wechat/session") ResponseEntity<?> session(HttpServletRequest request){
        TrustedWechatIngressVerifier.TrustedWechatIdentity identity=ingress.verify(request);
        var result=auth.establish(identity.appid(),identity.providerSubject(),identity.requestId());
        return ResponseEntity.status(201).body(Map.of("status","AUTHENTICATED","token",result.token(),"subjectRef",result.subjectRef(),"expiresAt",result.expiresAt().toString()));
    }
    @ExceptionHandler({BuyerAuthService.Rejected.class,TrustedWechatIngressVerifier.Rejected.class}) ResponseEntity<?> rejected(){return ResponseEntity.status(403).body(Map.of("status","REJECTED","code","TRUSTED_WECHAT_IDENTITY_REQUIRED"));}
}
