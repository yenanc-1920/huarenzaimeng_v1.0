package com.huarenzaimeng.api.payment;

import com.huarenzaimeng.core.ProjectEnvelope;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Transport boundary only. Provider-specific parsing and authority verification stay in the adapter. */
@RestController
@Profile("release-mysql")
@RequestMapping("/provider-callback/v1/wechat-pay")
final class WeChatPayNotificationController {
    private final WeChatPayCoordinator coordinator;
    private final WeChatPayPort provider;
    WeChatPayNotificationController(WeChatPayCoordinator coordinator,WeChatPayPort provider){this.coordinator=coordinator;this.provider=provider;}

    @PostMapping("/notifications")
    ResponseEntity<?> receive(@RequestHeader("Wechatpay-Timestamp") String timestamp,
                              @RequestHeader("Wechatpay-Nonce") String nonce,
                              @RequestHeader("Wechatpay-Signature") String signature,
                              @RequestHeader("Wechatpay-Serial") String serial,
                              @RequestHeader(value="Wechatpay-Request-Id",required=false) String requestId,
                              @RequestBody String rawBody){
        if(!provider.available())return unavailable("WECHAT_PAY_ADAPTER_DISABLED");
        String notificationId=requestId==null||requestId.isBlank()?"WXNOTICE-"+digest(rawBody).substring(0,32):requestId;
        var view=coordinator.notification(new WeChatPayPort.NotificationEnvelope(notificationId,timestamp,nonce,signature,serial,rawBody));
        return ResponseEntity.ok().header("Cache-Control","no-store").body(ProjectEnvelope.accepted(view));
    }

    @ExceptionHandler(WeChatPayCoordinator.Conflict.class)
    ResponseEntity<?> conflict(WeChatPayCoordinator.Conflict conflict){return ResponseEntity.status(409).header("Cache-Control","no-store").body(ProjectEnvelope.rejected(conflict.getMessage()));}
    private static ResponseEntity<?> unavailable(String code){return ResponseEntity.status(503).header("Cache-Control","no-store").body(ProjectEnvelope.rejected(code));}
    private static String digest(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException("PAYMENT_NOTIFICATION_DIGEST_UNAVAILABLE");}}
}
