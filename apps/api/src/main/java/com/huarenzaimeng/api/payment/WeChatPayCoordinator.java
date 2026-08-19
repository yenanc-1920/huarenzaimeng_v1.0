package com.huarenzaimeng.api.payment;

import com.huarenzaimeng.api.BusinessEventStore;
import java.time.Instant;
import java.util.Set;
import java.util.Objects;

/** Deterministic payment state machine; every provider ambiguity remains UNKNOWN and is never retried here. */
public final class WeChatPayCoordinator {
    private final WeChatPayPort provider;
    private final Store store;
    private final PaymentOrderSnapshotPort orders;
    private final int queryBudget;
    private final NotificationAuthority notificationAuthority;

    public WeChatPayCoordinator(WeChatPayPort provider, Store store,PaymentOrderSnapshotPort orders,int queryBudget,
                                NotificationAuthority notificationAuthority,BusinessEventStore events) {
        this.provider = Objects.requireNonNull(provider); this.store = Objects.requireNonNull(store);this.orders=Objects.requireNonNull(orders);
        if(queryBudget<0||queryBudget>20)throw new IllegalArgumentException("PAYMENT_QUERY_BUDGET_INVALID");this.queryBudget=queryBudget;
        this.notificationAuthority=Objects.requireNonNull(notificationAuthority);Objects.requireNonNull(events);
    }

    public View create(CreateCommand requested) {
        return createForBuyer(requested, null);
    }

    public View createForBuyer(CreateCommand requested, String buyerSubjectRef) {
        if(requested==null||blank(requested.merchantOrderRef())||requested.requestDigest()==null||!requested.requestDigest().matches("[a-f0-9]{64}"))throw new Conflict("PAYMENT_COMMAND_INVALID");
        PaymentOrderSnapshotPort.Snapshot trusted=orders.requirePayable(requested.merchantOrderRef(),buyerSubjectRef);
        if(buyerSubjectRef!=null&&!buyerSubjectRef.equals(trusted.buyerSubjectRef()))throw new Conflict("PAYMENT_ORDER_OWNERSHIP_CONFLICT");
        requireOrder(trusted.merchantOrderRef(),trusted.amountMinor(),trusted.currency(),requested.requestDigest());
        WeChatPayPort.UnifiedOrder command=new WeChatPayPort.UnifiedOrder(trusted.merchantOrderRef(),trusted.amountMinor(),trusted.currency(),trusted.payerOpenId(),requested.requestDigest());
        Begin begin = store.begin(trusted,requested.requestDigest(),queryBudget,Instant.now().plusSeconds(300));
        if (begin == Begin.REPLAY) return store.require(trusted.merchantOrderRef());
        if (begin == Begin.CONFLICT) throw new Conflict("PAYMENT_IDEMPOTENCY_CONFLICT");
        WeChatPayPort.Result result = safe(() -> provider.unifiedOrder(command));
        if (result instanceof WeChatPayPort.Accepted accepted)
            store.providerResultWithPrepay(command.merchantOrderRef(),accepted,normalize(accepted.state()));
        else if (result instanceof WeChatPayPort.Rejected rejected)
            store.providerResult(command.merchantOrderRef(), null, State.REJECTED, rejected.reasonCode());
        else store.providerResult(command.merchantOrderRef(), null, State.UNKNOWN, ((WeChatPayPort.Unknown) result).reasonCode());
        return store.require(command.merchantOrderRef());
    }

    public View notification(WeChatPayPort.NotificationEnvelope envelope) {
        if (envelope == null || blank(envelope.notificationId())) throw new Conflict("PAYMENT_NOTIFICATION_INVALID");
        WeChatPayPort.NotificationResult result;
        try { result = provider.verifyAndDecrypt(envelope); }
        catch (RuntimeException failure) { result = new WeChatPayPort.NotificationUnknown("VERIFY_RESULT_UNKNOWN"); }
        if (result instanceof WeChatPayPort.InvalidNotification invalid) throw new Conflict(invalid.reasonCode());
        if (result instanceof WeChatPayPort.NotificationUnknown) throw new Conflict("PAYMENT_NOTIFICATION_UNKNOWN");
        WeChatPayPort.VerifiedNotification verified = (WeChatPayPort.VerifiedNotification) result;
        if(!notificationAuthority.configured())throw new Conflict("PAYMENT_NOTIFICATION_AUTHORITY_NOT_CONFIGURED");
        if(!notificationAuthority.expectedAppId().equals(verified.appId())||!notificationAuthority.merchantId().equals(verified.merchantId())||!notificationAuthority.allowedCertificateSerials().contains(verified.certificateSerial()))throw new Conflict("PAYMENT_NOTIFICATION_AUTHORITY_CONFLICT");
        View current = store.require(verified.merchantOrderRef());
        if (current.amountMinor() != verified.amountMinor() || !current.currency().equals(verified.currency()))
            throw new Conflict("PAYMENT_NOTIFICATION_AMOUNT_CONFLICT");
        NotificationDisposition disposition=store.receiveNotification(verified, normalize(verified.state()));
        if(disposition==NotificationDisposition.CONFLICT)throw new Conflict("PAYMENT_NOTIFICATION_ID_CONFLICT");
        return store.require(verified.merchantOrderRef());
    }

    public View query(String orderRef) { View v=store.require(orderRef);if(v.queryBudgetRemaining()<=0||Instant.now().isAfter(v.queryDeadline()))throw new Conflict("PAYMENT_QUERY_BUDGET_EXHAUSTED");store.consumeQueryBudget(orderRef);return observe(orderRef, safe(() -> provider.query(orderRef))); }
    public View statusForRecovery(String orderRef){return store.require(orderRef);}
    public View statusForBuyer(String orderRef,String buyerSubjectRef){View view=store.require(orderRef);requireBuyer(view,buyerSubjectRef);return view;}
    public View queryForBuyer(String orderRef,String buyerSubjectRef){requireBuyer(store.require(orderRef),buyerSubjectRef);return query(orderRef);}
    public View close(String orderRef) {
        View current = store.require(orderRef);
        if (current.state() == State.PAID || current.state() == State.REFUNDED) throw new Conflict("PAYMENT_CLOSE_NOT_ALLOWED");
        return observe(orderRef, safe(() -> provider.close(orderRef)));
    }
    public View refund(WeChatPayPort.Refund command) {
        View payment=store.require(command.merchantOrderRef());
        WeChatPayPort.Refund trusted=new WeChatPayPort.Refund(command.merchantOrderRef(),command.refundRef(),command.amountMinor(),command.requestDigest(),payment.amountMinor(),payment.currency());
        Begin begin=store.beginRefund(command.merchantOrderRef(),command.refundRef(),command.requestDigest(),command.amountMinor(),queryBudget,Instant.now().plusSeconds(300));
        if(begin==Begin.CONFLICT)throw new Conflict("PAYMENT_REFUND_NOT_ALLOWED");
        if(begin==Begin.REPLAY)return store.require(command.merchantOrderRef());
        WeChatPayPort.Result result=safe(()->provider.refund(trusted));store.refundResult(command.merchantOrderRef(),command.refundRef(),result);return store.require(command.merchantOrderRef());
    }
    public View refundForBuyer(WeChatPayPort.Refund command,String buyerSubjectRef){requireBuyer(store.require(command.merchantOrderRef()),buyerSubjectRef);return refund(command);}
    public RefundView refundStatus(String refundRef){if(blank(refundRef))throw new Conflict("PAYMENT_REFUND_REF_INVALID");return store.requireRefund(refundRef);}
    public RefundView refundStatusForBuyer(String orderRef,String refundRef,String buyerSubjectRef){requireBuyer(store.require(orderRef),buyerSubjectRef);RefundView refund=refundStatus(refundRef);if(!orderRef.equals(refund.merchantOrderRef()))throw new Conflict("PAYMENT_REFUND_OWNERSHIP_CONFLICT");return refund;}
    public RefundView queryRefundOriginal(String refundRef){
        if(blank(refundRef))throw new Conflict("PAYMENT_REFUND_REF_INVALID");
        RefundView current=store.requireRefund(refundRef);
        if(current.state()!=RefundState.UNKNOWN)return current;
        View payment=store.require(current.merchantOrderRef());
        store.consumeRefundQueryBudget(refundRef);
        WeChatPayPort.RefundQueryResult result=safeRefundQuery(()->provider.queryRefundOriginal(refundRef));
        if(result instanceof WeChatPayPort.RefundQueryUnknown)return current;
        WeChatPayPort.RefundObservation observation=(WeChatPayPort.RefundObservation)result;
        if(!current.refundRef().equals(observation.refundRef())||!current.merchantOrderRef().equals(observation.merchantOrderRef())||
                current.amountMinor()!=observation.amountMinor()||!payment.currency().equals(observation.currency()))
            throw new Conflict("PAYMENT_REFUND_QUERY_BINDING_CONFLICT");
        RefundState observed=normalizeRefund(observation.state());
        return store.refundObservation(refundRef,observed,observation.evidenceRef());
    }
    public RefundView queryRefundOriginalForBuyer(String orderRef,String refundRef,String buyerSubjectRef){refundStatusForBuyer(orderRef,refundRef,buyerSubjectRef);return queryRefundOriginal(refundRef);}
    private View observe(String orderRef, WeChatPayPort.Result result) {
        if (result instanceof WeChatPayPort.Accepted accepted)
            store.providerResult(orderRef, accepted.providerRef(), normalize(accepted.state()), accepted.evidenceRef());
        else if (result instanceof WeChatPayPort.Rejected rejected)
            store.observation(orderRef, State.REJECTED, rejected.reasonCode());
        else store.observation(orderRef, State.UNKNOWN, ((WeChatPayPort.Unknown) result).reasonCode());
        return store.require(orderRef);
    }
    private WeChatPayPort.Result safe(Call call) { try { WeChatPayPort.Result r=call.get(); return r==null?new WeChatPayPort.Unknown("PROVIDER_RESULT_UNKNOWN"):r; } catch(RuntimeException e){ return new WeChatPayPort.Unknown("PROVIDER_CALL_UNKNOWN"); } }
    private WeChatPayPort.RefundQueryResult safeRefundQuery(RefundQueryCall call){try{WeChatPayPort.RefundQueryResult r=call.get();return r==null?new WeChatPayPort.RefundQueryUnknown("PROVIDER_RESULT_UNKNOWN"):r;}catch(RuntimeException e){return new WeChatPayPort.RefundQueryUnknown("PROVIDER_CALL_UNKNOWN");}}
    private static State normalize(String state) { try { return State.valueOf(state); } catch(Exception invalid){ return State.UNKNOWN; } }
    private static RefundState normalizeRefund(String state){try{return RefundState.valueOf(state);}catch(Exception invalid){return RefundState.UNKNOWN;}}
    private static void requireOrder(String ref,long amount,String currency,String digest){if(blank(ref)||amount<=0||currency==null||!currency.matches("[A-Z]{3}")||digest==null||!digest.matches("[a-f0-9]{64}"))throw new Conflict("PAYMENT_COMMAND_INVALID");}
    private static void requireBuyer(View view,String buyerSubjectRef){if(blank(buyerSubjectRef)||!buyerSubjectRef.equals(view.buyerSubjectRef()))throw new Conflict("PAYMENT_ORDER_OWNERSHIP_CONFLICT");}
    private static boolean blank(String v){return v==null||v.isBlank();}
    private interface Call { WeChatPayPort.Result get(); }
    private interface RefundQueryCall { WeChatPayPort.RefundQueryResult get(); }

    public enum State { NEW, PREPAY_CREATED, PROCESSING, PAID, CLOSED, REFUND_PROCESSING, REFUNDED, REJECTED, UNKNOWN }
    public enum Begin { CREATED, REPLAY, CONFLICT }
    public record CreateCommand(String merchantOrderRef,String requestDigest) {}
    public record View(String merchantOrderRef,String requestDigest,String buyerSubjectRef,String quoteRef,String priceSnapshotDigest,
                       long amountMinor,String currency,String providerRef,State state,String evidenceRef,
                       int queryBudgetRemaining,Instant queryDeadline,long refundedMinor,long version,Instant updatedAt,
                       WeChatPayPort.PrepayParameters prepayParameters) {
        public View(String merchantOrderRef,String requestDigest,String buyerSubjectRef,String quoteRef,String priceSnapshotDigest,long amountMinor,String currency,String providerRef,State state,String evidenceRef,int queryBudgetRemaining,Instant queryDeadline,long refundedMinor,long version,Instant updatedAt){
            this(merchantOrderRef,requestDigest,buyerSubjectRef,quoteRef,priceSnapshotDigest,amountMinor,currency,providerRef,state,evidenceRef,queryBudgetRemaining,queryDeadline,refundedMinor,version,updatedAt,null);
        }
    }
    public record NotificationAuthority(String expectedAppId,String merchantId,Set<String> allowedCertificateSerials){public NotificationAuthority{allowedCertificateSerials=allowedCertificateSerials==null?Set.of():Set.copyOf(allowedCertificateSerials);}boolean configured(){return !blank(expectedAppId)&&!blank(merchantId)&&!allowedCertificateSerials.isEmpty();}}
    public enum RefundState{PENDING,SUCCEEDED,REJECTED,UNKNOWN}
    public record RefundView(String refundRef,String merchantOrderRef,String requestDigest,long amountMinor,RefundState state,State originalPaymentState,
                             int queryBudgetRemaining,Instant queryDeadline,Instant updatedAt){}
    public enum NotificationDisposition { ACCEPTED,REPLAY,CONFLICT }
    public interface Store {
        Begin begin(PaymentOrderSnapshotPort.Snapshot snapshot,String requestDigest,int queryBudget,Instant queryDeadline);
        View require(String orderRef);
        void providerResult(String orderRef,String providerRef,State state,String evidenceRef);
        default void providerResultWithPrepay(String orderRef,WeChatPayPort.Accepted accepted,State state){providerResult(orderRef,accepted.providerRef(),state,accepted.evidenceRef());}
        void observation(String orderRef,State state,String evidenceRef);
        NotificationDisposition receiveNotification(WeChatPayPort.VerifiedNotification notification,State state);
        void consumeQueryBudget(String orderRef);
        Begin beginRefund(String orderRef,String refundRef,String requestDigest,long amountMinor,int queryBudget,Instant queryDeadline);
        RefundView refundResult(String orderRef,String refundRef,WeChatPayPort.Result result);
        RefundView refundObservation(String refundRef,RefundState state,String evidenceRef);
        void consumeRefundQueryBudget(String refundRef);
        RefundView requireRefund(String refundRef);
    }
    public static final class Conflict extends RuntimeException { public Conflict(String code){super(code);} }
}
