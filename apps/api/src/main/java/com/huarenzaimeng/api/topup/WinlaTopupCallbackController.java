package com.huarenzaimeng.api.topup;

import com.huarenzaimeng.core.ProjectEnvelope;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
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

    @PostMapping(value="/topup",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces=MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<?> receive(@RequestBody String body){
        if(!provider.available())return ResponseEntity.status(503).header("Cache-Control","no-store").body(ProjectEnvelope.rejected("WINLA_ADAPTER_DISABLED"));
        coordinator.callback(new TopupProviderPort.CallbackEnvelope("WINLA_FORM_CALLBACK","","","",body));
        return ResponseEntity.ok().header("Cache-Control","no-store").body("success");
    }
    @ExceptionHandler(TopupCoordinator.Conflict.class)
    ResponseEntity<?> conflict(TopupCoordinator.Conflict conflict){return ResponseEntity.status(409).header("Cache-Control","no-store").body(ProjectEnvelope.rejected(conflict.getMessage()));}
}
