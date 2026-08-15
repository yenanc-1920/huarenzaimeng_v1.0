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
    private final MockFlowService flow;
    ReleaseBuyerFlowController(MockFlowService flow) { this.flow = flow; }

    @PostMapping("/quotes")
    ResponseEntity<ProjectEnvelope<Quote>> quote(HttpServletRequest servlet, @Valid @RequestBody QuoteRequest r) {
        String subject = principal(servlet).subjectRef();
        return ok(flow.createQuote(subject, r.phone(), r.operatorCode(), r.productRef(), r.denominationRef(),
                r.supportedOperatorSetVersion(), r.catalogVersion(), r.commandId(), r.idempotencyKey(), r.mnpState()));
    }

    @PostMapping("/orders")
    ResponseEntity<OrderCreationResponse> order(HttpServletRequest servlet, @Valid @RequestBody OrderRequest r) {
        String subject = principal(servlet).subjectRef();
        BuyerAuthorization authorization = new BuyerAuthorization("DEVELOPMENT", subject, r.sessionVersion(),
                r.authorizationSetRef(), "DEV-BEARER-V1", List.of());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(flow.createLocalSyntheticOrder(
                authorization, r.quoteRef(), r.orderCreationPrecondition(), r.commandId(), r.idempotencyKey()));
    }

    @GetMapping("/orders")
    ResponseEntity<ProjectEnvelope<List<OrderProjection>>> orders(HttpServletRequest servlet) {
        return ok(flow.listOrders(principal(servlet).subjectRef()));
    }

    @GetMapping("/orders/{orderRef}")
    ResponseEntity<ProjectEnvelope<OrderProjection>> order(HttpServletRequest servlet, @PathVariable String orderRef) {
        return ok(flow.requireOrder(principal(servlet).subjectRef(), orderRef));
    }

    @GetMapping("/orders/{orderRef}/projection")
    ResponseEntity<ProjectEnvelope<ProjectProjection>> projection(HttpServletRequest servlet, @PathVariable String orderRef) {
        return ok(flow.projectOrder(principal(servlet).subjectRef(), orderRef));
    }

    @PostMapping("/orders/{orderRef}/payment-intents")
    ResponseEntity<PaymentIntentResponse> payment(HttpServletRequest servlet, @PathVariable String orderRef,
                                                   @Valid @RequestBody PaymentRequest r) {
        String subject = principal(servlet).subjectRef();
        List<String> refs = flow.listOrders(subject).stream().map(OrderProjection::orderRef).toList();
        BuyerAuthorization authorization = new BuyerAuthorization("DEVELOPMENT", subject, r.sessionVersion(),
                r.authorizationSetRef(), "DEV-BEARER-V1", refs);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(flow.createDevelopmentPaymentIntent(
                authorization, orderRef, r.paymentIntentCreationPrecondition(), r.commandId(), r.idempotencyKey(),
                r.expectedProjectionVersion(), r.expectedAggregateVersion()));
    }

    private static BuyerSessionPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(BuyerSessionFilter.BUYER);
        if (value instanceof BuyerSessionPrincipal buyer) return buyer;
        throw new FlowRejectedException("BUYER_SESSION_REQUIRED");
    }
    private static <T> ResponseEntity<ProjectEnvelope<T>> ok(T body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ProjectEnvelope.accepted(body));
    }

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
