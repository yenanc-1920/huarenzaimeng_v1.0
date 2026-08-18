package com.huarenzaimeng.api.topup;

import com.huarenzaimeng.core.ProjectEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Frozen internal callback envelope; the adapter alone understands supplier transport fields. */
@RestController
@Profile("release-mysql")
@RequestMapping("/supplier-callback/v1/winla")
final class WinlaTopupCallbackController {
    private final TopupCoordinator coordinator;
    private final TopupProviderPort provider;
    WinlaTopupCallbackController(TopupCoordinator coordinator,TopupProviderPort provider){this.coordinator=coordinator;this.provider=provider;}

    @PostMapping("/topup")
    ResponseEntity<?> receive(@Valid @RequestBody CallbackRequest request){
        if(!provider.available())return ResponseEntity.status(503).header("Cache-Control","no-store").body(ProjectEnvelope.rejected("WINLA_ADAPTER_DISABLED"));
        var view=coordinator.callback(new TopupProviderPort.CallbackEnvelope(request.callbackId(),request.timestamp(),request.nonce(),request.signature(),request.body()));
        return ResponseEntity.ok().header("Cache-Control","no-store").body(ProjectEnvelope.accepted(view));
    }
    @ExceptionHandler(TopupCoordinator.Conflict.class)
    ResponseEntity<?> conflict(TopupCoordinator.Conflict conflict){return ResponseEntity.status(409).header("Cache-Control","no-store").body(ProjectEnvelope.rejected(conflict.getMessage()));}
    record CallbackRequest(@NotBlank String callbackId,@NotBlank String timestamp,@NotBlank String nonce,@NotBlank String signature,@NotBlank String body){}
}
