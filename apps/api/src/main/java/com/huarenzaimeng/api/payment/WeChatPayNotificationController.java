package com.huarenzaimeng.api.payment;

import com.huarenzaimeng.core.ProjectEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
    ResponseEntity<?> receive(@Valid @RequestBody NotificationRequest request){
        if(!provider.available())return unavailable("WECHAT_PAY_ADAPTER_DISABLED");
        var view=coordinator.notification(new WeChatPayPort.NotificationEnvelope(request.notificationId(),request.timestamp(),request.nonce(),request.signature(),request.encryptedBody()));
        return ResponseEntity.ok().header("Cache-Control","no-store").body(ProjectEnvelope.accepted(view));
    }

    @ExceptionHandler(WeChatPayCoordinator.Conflict.class)
    ResponseEntity<?> conflict(WeChatPayCoordinator.Conflict conflict){return ResponseEntity.status(409).header("Cache-Control","no-store").body(ProjectEnvelope.rejected(conflict.getMessage()));}
    private static ResponseEntity<?> unavailable(String code){return ResponseEntity.status(503).header("Cache-Control","no-store").body(ProjectEnvelope.rejected(code));}
    record NotificationRequest(@NotBlank String notificationId,@NotBlank String timestamp,@NotBlank String nonce,@NotBlank String signature,@NotBlank String encryptedBody){}
}
