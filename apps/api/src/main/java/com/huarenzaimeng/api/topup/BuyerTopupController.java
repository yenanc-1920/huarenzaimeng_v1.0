package com.huarenzaimeng.api.topup;

import com.huarenzaimeng.api.buyerauth.BuyerSessionFilter;
import com.huarenzaimeng.api.buyerauth.BuyerSessionPrincipal;
import com.huarenzaimeng.core.ProjectEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("release-mysql")
@RequestMapping("/buyer-api/v1/orders/{orderRef}/topup")
final class BuyerTopupController {
    private final TopupCoordinator coordinator;
    private final TopupProviderPort provider;
    BuyerTopupController(TopupCoordinator coordinator,TopupProviderPort provider){this.coordinator=coordinator;this.provider=provider;}

    @PostMapping
    ResponseEntity<?> submit(HttpServletRequest servlet,@PathVariable String orderRef,@Valid @RequestBody SubmitRequest request){
        BuyerSessionPrincipal buyer=principal(servlet);if(!provider.available())return unavailable();
        return ok(TopupView.from(coordinator.submitForBuyer(new TopupCoordinator.SubmitCommand(orderRef,request.requestRef(),request.requestDigest()),buyer.subjectRef())));
    }
    @GetMapping ResponseEntity<?> status(HttpServletRequest servlet,@PathVariable String orderRef){BuyerSessionPrincipal buyer=principal(servlet);return ok(TopupView.from(coordinator.statusForBuyer(orderRef,buyer.subjectRef())));}
    @PostMapping("/query") ResponseEntity<?> query(HttpServletRequest servlet,@PathVariable String orderRef){
        BuyerSessionPrincipal buyer=principal(servlet);
        coordinator.statusForBuyer(orderRef,buyer.subjectRef());
        if(!provider.available())return unavailable();
        return ok(TopupView.from(coordinator.queryForBuyer(orderRef,buyer.subjectRef())));
    }

    @ExceptionHandler(TopupCoordinator.Conflict.class)
    ResponseEntity<?> conflict(TopupCoordinator.Conflict conflict){return ResponseEntity.status(409).header("Cache-Control","no-store").body(ProjectEnvelope.rejected(conflict.getMessage()));}
    private static BuyerSessionPrincipal principal(HttpServletRequest request){Object value=request.getAttribute(BuyerSessionFilter.BUYER);if(value instanceof BuyerSessionPrincipal buyer)return buyer;throw new TopupCoordinator.Conflict("BUYER_SESSION_REQUIRED");}
    private static ResponseEntity<?> ok(Object value){return ResponseEntity.ok().header("Cache-Control","no-store").body(ProjectEnvelope.accepted(value));}
    private static ResponseEntity<?> unavailable(){return ResponseEntity.status(503).header("Cache-Control","no-store").body(ProjectEnvelope.rejected("WINLA_ADAPTER_DISABLED"));}
    record SubmitRequest(@NotBlank String requestRef,@Pattern(regexp="[a-f0-9]{64}") String requestDigest){}
    record TopupView(String orderRef,String requestRef,String state,String providerRef,String evidenceRef,long version){
        static TopupView from(TopupCoordinator.View v){return new TopupView(v.merchantOrderRef(),v.requestRef(),v.state().name(),v.providerRef(),v.evidenceRef(),v.version());}
    }
}
