package com.huarenzaimeng.api;

import com.huarenzaimeng.api.buyerauth.BuyerSessionFilter;
import com.huarenzaimeng.api.buyerauth.BuyerSessionPrincipal;
import com.huarenzaimeng.api.buyerauth.TransactionPrincipal;
import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.ProjectEnvelope;
import com.huarenzaimeng.core.ProjectProjection;
import com.huarenzaimeng.core.Quote;
import com.huarenzaimeng.api.payment.WeChatPayCoordinator;
import com.huarenzaimeng.api.payment.WeChatPayPort;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Profile("release-mysql")
@RequestMapping("/buyer-api/v1")
public final class ReleaseBuyerFlowController {
    private final FlowStore store;
    private final WeChatPayCoordinator payments;
    private final WeChatPayPort paymentProvider;
    private final ReleaseQuoteOrderService quoteOrders;
    private final BuyerConsentStateGuard consent;
    @Autowired
    ReleaseBuyerFlowController(FlowStore store,WeChatPayCoordinator payments,WeChatPayPort paymentProvider,ReleaseQuoteOrderService quoteOrders,BuyerConsentStateGuard consent) { this.store = store;this.payments=payments;this.paymentProvider=paymentProvider;this.quoteOrders=quoteOrders;this.consent=consent; }
    ReleaseBuyerFlowController(FlowStore store,WeChatPayCoordinator payments,WeChatPayPort paymentProvider,ReleaseQuoteOrderService quoteOrders) { this(store,payments,paymentProvider,quoteOrders,null); }
    ReleaseBuyerFlowController(FlowStore store,WeChatPayCoordinator payments,WeChatPayPort paymentProvider) { this(store,payments,paymentProvider,null,null); }

    @PostMapping("/quotes")
    ResponseEntity<?> quote(HttpServletRequest servlet, @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
                            @Valid @RequestBody QuoteRequest r) {
        TransactionPrincipal principal=transactionPrincipal(servlet);requireConsent(principal);return ok(requireQuoteOrders().createQuote(principal.subjectRef(),idempotencyKey,r.requestRef(),r.phone(),r.productRef()));
    }

    @PostMapping("/orders")
    ResponseEntity<?> order(HttpServletRequest servlet, @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
                            @Valid @RequestBody OrderRequest r) {
        TransactionPrincipal principal=transactionPrincipal(servlet);requireConsent(principal);return ok(requireQuoteOrders().createOrder(principal.subjectRef(),idempotencyKey,r.requestRef(),r.quoteRef()));
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
        return ok(requireQuoteOrders().projection(principal(servlet).subjectRef(),orderRef));
    }

    @PostMapping("/orders/{orderRef}/payment-intents")
    ResponseEntity<?> payment(HttpServletRequest servlet, @PathVariable String orderRef,
                                                   @Valid @RequestBody PaymentRequest r) {
        BuyerSessionPrincipal buyer=principal(servlet);
        requireConsent(buyer);
        if(!paymentProvider.available())return providerUnavailable("WECHAT_PAY_ADAPTER_DISABLED");
        return ok(PaymentView.from(payments.createForBuyer(new WeChatPayCoordinator.CreateCommand(orderRef,r.requestDigest()),buyer.subjectRef())));
    }

    @GetMapping("/orders/{orderRef}/payment-intents/result")
    ResponseEntity<?> paymentStatus(HttpServletRequest servlet,@PathVariable String orderRef){
        BuyerSessionPrincipal buyer=principal(servlet);return ok(PaymentView.from(payments.statusForBuyer(orderRef,buyer.subjectRef())));
    }

    @PostMapping("/orders/{orderRef}/payment-intents/query")
    ResponseEntity<?> queryPayment(HttpServletRequest servlet,@PathVariable String orderRef){
        BuyerSessionPrincipal buyer=principal(servlet);
        payments.statusForBuyer(orderRef,buyer.subjectRef());
        if(!paymentProvider.available())return providerUnavailable("WECHAT_PAY_ADAPTER_DISABLED");
        return ok(PaymentView.from(payments.queryForBuyer(orderRef,buyer.subjectRef())));
    }

    @PostMapping("/orders/{orderRef}/refunds")
    ResponseEntity<?> refund(HttpServletRequest servlet,@PathVariable String orderRef,@Valid @RequestBody RefundRequest r){
        BuyerSessionPrincipal buyer=principal(servlet);
        requireConsent(buyer);
        payments.statusForBuyer(orderRef,buyer.subjectRef());
        if(!paymentProvider.available())return providerUnavailable("WECHAT_PAY_ADAPTER_DISABLED");
        return ok(PaymentView.from(payments.refundForBuyer(new WeChatPayPort.Refund(orderRef,r.refundRef(),r.amountMinor(),r.requestDigest()),buyer.subjectRef())));
    }

    @GetMapping("/orders/{orderRef}/refunds/{refundRef}")
    ResponseEntity<?> refundStatus(HttpServletRequest servlet,@PathVariable String orderRef,@PathVariable String refundRef){BuyerSessionPrincipal buyer=principal(servlet);return ok(RefundView.from(payments.refundStatusForBuyer(orderRef,refundRef,buyer.subjectRef())));}

    @PostMapping("/orders/{orderRef}/refunds/{refundRef}/query")
    ResponseEntity<?> queryRefund(HttpServletRequest servlet,@PathVariable String orderRef,@PathVariable String refundRef){
        BuyerSessionPrincipal buyer=principal(servlet);
        payments.refundStatusForBuyer(orderRef,refundRef,buyer.subjectRef());
        if(!paymentProvider.available())return providerUnavailable("WECHAT_PAY_ADAPTER_DISABLED");
        return ok(RefundView.from(payments.queryRefundOriginalForBuyer(orderRef,refundRef,buyer.subjectRef())));
    }

    private static BuyerSessionPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(BuyerSessionFilter.BUYER);
        if (value instanceof BuyerSessionPrincipal buyer) return buyer;
        throw new FlowRejectedException("BUYER_SESSION_REQUIRED");
    }
    private static TransactionPrincipal transactionPrincipal(HttpServletRequest request) {
        Object value=request.getAttribute(BuyerSessionFilter.BUYER);
        if(value instanceof TransactionPrincipal principal)return principal;
        throw new FlowRejectedException("TRANSACTION_SESSION_REQUIRED");
    }
    private static <T> ResponseEntity<ProjectEnvelope<T>> ok(T body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ProjectEnvelope.accepted(body));
    }
    private static ResponseEntity<?> providerUnavailable(String code){return ResponseEntity.status(503).header("Cache-Control","no-store").body(ProjectEnvelope.rejected(code));}
    private ReleaseQuoteOrderService requireQuoteOrders(){if(quoteOrders==null)throw new IllegalStateException("RELEASE_QUOTE_ORDER_SERVICE_REQUIRED");return quoteOrders;}
    private void requireConsent(TransactionPrincipal principal){if(consent!=null&&principal.type()==TransactionPrincipal.Type.BUYER)consent.requireTransactionWrite(principal.subjectRef());}

    @ExceptionHandler(WeChatPayCoordinator.Conflict.class)
    ResponseEntity<?> paymentConflict(WeChatPayCoordinator.Conflict conflict){return ResponseEntity.status(409).header("Cache-Control","no-store").body(ProjectEnvelope.rejected(conflict.getMessage()));}
    @ExceptionHandler(FlowRejectedException.class)
    ResponseEntity<?> flowConflict(FlowRejectedException conflict){return ResponseEntity.status(409).header("Cache-Control","no-store").body(ProjectEnvelope.rejected(conflict.getMessage()));}

    record QuoteRequest(@NotBlank String requestRef,@NotBlank String phone,@NotBlank String productRef) {}
    record OrderRequest(@NotBlank String requestRef,@NotBlank String quoteRef) {}
    record PaymentRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                          @NotBlank String paymentIntentCreationPrecondition, @Min(1) long sessionVersion,
                          @NotBlank String authorizationSetRef, @Min(1) long expectedProjectionVersion,
                          @Min(1) long expectedAggregateVersion,@Pattern(regexp="[a-f0-9]{64}") String requestDigest) {}
    record RefundRequest(@NotBlank String refundRef,@Min(1) long amountMinor,@Pattern(regexp="[a-f0-9]{64}") String requestDigest) {}
    record PaymentView(String orderRef,String state,String providerRef,long amountMinor,String currency,long refundedMinor,long version,PrepayParameters prepayParameters){
        static PaymentView from(WeChatPayCoordinator.View v){var p=v.prepayParameters();return new PaymentView(v.merchantOrderRef(),v.state().name(),v.providerRef(),v.amountMinor(),v.currency(),v.refundedMinor(),v.version(),p==null?null:new PrepayParameters(p.timeStamp(),p.nonceStr(),p.packageValue(),p.signType(),p.paySign()));}
    }
    record PrepayParameters(String timeStamp,String nonceStr,@JsonProperty("package") String packageValue,String signType,String paySign) {}
    record RefundView(String orderRef,String refundRef,String state,long amountMinor){static RefundView from(WeChatPayCoordinator.RefundView v){return new RefundView(v.merchantOrderRef(),v.refundRef(),v.state().name(),v.amountMinor());}}
}
