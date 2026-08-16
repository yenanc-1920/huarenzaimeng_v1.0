package com.huarenzaimeng.api;

import com.huarenzaimeng.api.buyerauth.BuyerSessionFilter;
import com.huarenzaimeng.api.buyerauth.BuyerSessionPrincipal;
import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.ProjectEnvelope;
import com.huarenzaimeng.core.ProjectProjection;
import com.huarenzaimeng.core.Quote;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Profile("release-mysql")
@RequestMapping("/buyer-api/v1")
public final class ReleaseBuyerFlowController {
    private final FlowStore store;
    ReleaseBuyerFlowController(FlowStore store) { this.store = store; }

    @PostMapping("/quotes")
    ResponseEntity<?> quote(HttpServletRequest servlet, @Valid @RequestBody QuoteRequest r) {
        principal(servlet); return writeUnavailable();
    }

    @PostMapping("/orders")
    ResponseEntity<?> order(HttpServletRequest servlet, @Valid @RequestBody OrderRequest r) {
        principal(servlet); return writeUnavailable();
    }

    @GetMapping("/orders")
    ResponseEntity<ProjectEnvelope<List<OrderProjection>>> orders(HttpServletRequest servlet) {
        return ok(store.listOrders(principal(servlet).subjectRef()));
    }

    @GetMapping("/orders/{orderRef}")
    ResponseEntity<ProjectEnvelope<OrderProjection>> order(HttpServletRequest servlet, @PathVariable String orderRef) {
        return ok(store.requireOrder(principal(servlet).subjectRef(), orderRef));
    }

    @GetMapping("/orders/{orderRef}/projection")
    ResponseEntity<?> projection(HttpServletRequest servlet, @PathVariable String orderRef) {
        principal(servlet); return ResponseEntity.status(503).header("Cache-Control","no-store")
                .body(ProjectEnvelope.rejected("PROJECTION_NOT_AVAILABLE_WITHOUT_PROVIDER_CONFIGURATION"));
    }

    @PostMapping("/orders/{orderRef}/payment-intents")
    ResponseEntity<?> payment(HttpServletRequest servlet, @PathVariable String orderRef,
                                                   @Valid @RequestBody PaymentRequest r) {
        principal(servlet); return writeUnavailable();
    }

    private static BuyerSessionPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(BuyerSessionFilter.BUYER);
        if (value instanceof BuyerSessionPrincipal buyer) return buyer;
        throw new FlowRejectedException("BUYER_SESSION_REQUIRED");
    }
    private static <T> ResponseEntity<ProjectEnvelope<T>> ok(T body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ProjectEnvelope.accepted(body));
    }
    private static ResponseEntity<?> writeUnavailable(){return ResponseEntity.status(503).header("Cache-Control","no-store")
            .body(ProjectEnvelope.rejected("EXTERNAL_PAYMENT_AND_TOPUP_NOT_CONFIGURED"));}

    record QuoteRequest(@NotBlank String phone, @NotBlank String operatorCode, @NotBlank String productRef,
                        @NotBlank String denominationRef, @Min(1) long supportedOperatorSetVersion,
                        @Min(1) long catalogVersion, @NotBlank String commandId, @NotBlank String idempotencyKey,
                        @NotNull MnpState mnpState) {}
    record OrderRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                        @NotBlank String orderCreationPrecondition, @NotBlank String quoteRef,
                        @Min(1) long sessionVersion, @NotBlank String authorizationSetRef) {}
    record PaymentRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                          @NotBlank String paymentIntentCreationPrecondition, @Min(1) long sessionVersion,
                          @NotBlank String authorizationSetRef, @Min(1) long expectedProjectionVersion,
                          @Min(1) long expectedAggregateVersion) {}
}
