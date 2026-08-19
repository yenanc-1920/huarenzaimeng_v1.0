package com.huarenzaimeng.api.topup;

import com.huarenzaimeng.api.BusinessEventStore;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/** Fail-closed supplier orchestration with reservation, one submit latch and bounded UNKNOWN queries. */
public final class TopupCoordinator {
    private final TopupProviderPort provider; private final Store store; private final TopupEligibilityPort eligibility;private final int unknownBudget;
    public TopupCoordinator(TopupProviderPort provider,Store store,TopupEligibilityPort eligibility,int unknownBudget,BusinessEventStore events){this.provider=Objects.requireNonNull(provider);this.store=Objects.requireNonNull(store);this.eligibility=Objects.requireNonNull(eligibility);Objects.requireNonNull(events);if(unknownBudget<0||unknownBudget>20)throw new IllegalArgumentException("UNKNOWN_BUDGET_INVALID");this.unknownBudget=unknownBudget;}

    public View submit(SubmitCommand requested) {
        return submitForBuyer(requested,null);
    }
    public View submitForBuyer(SubmitCommand requested,String buyerSubjectRef) {
        if(requested==null||blank(requested.merchantOrderRef())||blank(requested.requestRef())||requested.requestDigest()==null||!requested.requestDigest().matches("[a-f0-9]{64}"))throw new Conflict("TOPUP_COMMAND_INVALID");
        TopupEligibilityPort.Snapshot trusted=eligibility.requirePaidEntitlement(requested.merchantOrderRef());
        if(buyerSubjectRef!=null&&!buyerSubjectRef.equals(trusted.buyerSubjectRef()))throw new Conflict("TOPUP_ORDER_OWNERSHIP_CONFLICT");
        long providerAmount;
        try{providerAmount=trusted.supplierCost().movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).longValueExact();}
        catch(Exception invalid){throw new Conflict("TOPUP_PROVIDER_AMOUNT_INVALID");}
        TopupProviderPort.Command command=new TopupProviderPort.Command(trusted.merchantOrderRef(),requested.requestRef(),trusted.providerSku(),BangladeshPhoneNumber.normalize(trusted.recipient()),providerAmount,trusted.settlementCurrency(),requested.requestDigest());
        validate(command); Begin begin=store.beginAndReserve(command,trusted,unknownBudget);
        if(begin==Begin.REPLAY)return store.require(command.merchantOrderRef());
        if(begin==Begin.CONFLICT)throw new Conflict("TOPUP_IDEMPOTENCY_CONFLICT");
        TopupProviderPort.Result result=safe(()->provider.submit(command));
        if(result instanceof TopupProviderPort.Accepted a)store.submissionResult(command.merchantOrderRef(),a.providerRef(),state(a.state()),a.evidenceRef());
        else if(result instanceof TopupProviderPort.Rejected r)store.submissionRejectedAndRelease(command.merchantOrderRef(),r.reasonCode());
        else store.submissionResult(command.merchantOrderRef(),null,State.UNKNOWN,((TopupProviderPort.Unknown)result).reasonCode());
        return store.require(command.merchantOrderRef());
    }
    public View query(String orderRef){View current=store.require(orderRef);if(current.state()!=State.UNKNOWN&&current.state()!=State.PROCESSING)return current;if(current.unknownQueriesRemaining()<=0){store.reconciliationRequired(orderRef,"UNKNOWN_QUERY_BUDGET_EXHAUSTED");return store.require(orderRef);}TopupProviderPort.Result r=safe(()->provider.query(current.providerRef(),orderRef));store.consumeUnknownQuery(orderRef);if(r instanceof TopupProviderPort.Accepted a)store.observation(orderRef,state(a.state()),a.evidenceRef());else if(r instanceof TopupProviderPort.Rejected x)store.observation(orderRef,State.REJECTED,x.reasonCode());else store.observation(orderRef,State.UNKNOWN,((TopupProviderPort.Unknown)r).reasonCode());return store.require(orderRef);}
    public View statusForRecovery(String orderRef){return store.require(orderRef);}
    public View statusForBuyer(String orderRef,String buyerSubjectRef){View view=store.require(orderRef);requireBuyer(view,buyerSubjectRef);return view;}
    public View queryForBuyer(String orderRef,String buyerSubjectRef){requireBuyer(store.require(orderRef),buyerSubjectRef);return query(orderRef);}
    public View callback(TopupProviderPort.CallbackEnvelope envelope){TopupProviderPort.CallbackResult r;try{r=provider.verifyCallback(envelope);}catch(RuntimeException e){r=new TopupProviderPort.CallbackUnknown("CALLBACK_UNKNOWN");}if(r instanceof TopupProviderPort.InvalidCallback invalid)throw new Conflict(invalid.reasonCode());if(r instanceof TopupProviderPort.CallbackUnknown)throw new Conflict("TOPUP_CALLBACK_UNKNOWN");TopupProviderPort.VerifiedCallback v=(TopupProviderPort.VerifiedCallback)r;View current=store.require(v.merchantOrderRef());if(current.providerRef()!=null&&!current.providerRef().equals(v.providerRef()))throw new Conflict("TOPUP_CALLBACK_PROVIDER_CONFLICT");State target=state(v.state());boolean amountBound=v.amountMinor()!=null&&v.currency()!=null&&v.amountMinor()==current.reservedMinor()&&v.currency().equals(current.reserveCurrency());boolean optionalSkuBound=v.providerSku()==null||v.providerSku().equals(current.providerSku());boolean optionalRecipientBound=v.recipient()==null||BangladeshPhoneNumber.normalize(v.recipient()).equals(current.recipient());if(target==State.DELIVERED&&(!amountBound||!optionalSkuBound||!optionalRecipientBound))target=State.UNKNOWN;store.callback(v,target);return store.require(v.merchantOrderRef());}
    public TopupProviderPort.BalanceResult observeBalance(){try{return provider.balance();}catch(RuntimeException e){return new TopupProviderPort.BalanceUnknown("BALANCE_UNKNOWN");}}
    public BalanceReconciliation reconcileBalance(String currency){if(currency==null||!currency.matches("[A-Z]{3}"))throw new Conflict("BALANCE_CURRENCY_INVALID");TopupProviderPort.BalanceResult result=observeBalance();long reserved=store.reservedTotal(currency);if(result instanceof TopupProviderPort.BalanceUnknown unknown)return new BalanceReconciliation(BalanceState.UNKNOWN,null,reserved,currency,unknown.reasonCode(),null);TopupProviderPort.BalanceObserved observed=(TopupProviderPort.BalanceObserved)result;if(!currency.equals(observed.currency()))return new BalanceReconciliation(BalanceState.UNKNOWN,observed.availableMinor(),reserved,currency,"BALANCE_CURRENCY_MISMATCH",observed.observedAt());store.recordBalanceObservation("WINLA",observed.currency(),observed.availableMinor(),observed.observedAt(),observed.evidenceRef());return new BalanceReconciliation(observed.availableMinor()>=reserved?BalanceState.COVERED:BalanceState.SHORTFALL,observed.availableMinor(),reserved,currency,observed.evidenceRef(),observed.observedAt());}
    private TopupProviderPort.Result safe(Call c){try{TopupProviderPort.Result r=c.get();return r==null?new TopupProviderPort.Unknown("PROVIDER_RESULT_UNKNOWN"):r;}catch(RuntimeException e){return new TopupProviderPort.Unknown("PROVIDER_CALL_UNKNOWN");}}
    private static State state(String s){try{return State.valueOf(s);}catch(Exception e){return State.UNKNOWN;}}
    private static void validate(TopupProviderPort.Command c){if(c==null||blank(c.merchantOrderRef())||blank(c.requestRef())||blank(c.providerSku())||blank(c.recipient())||c.providerAmountMinor()<=0||c.providerCurrency()==null||!c.providerCurrency().matches("[A-Z]{3}")||c.requestDigest()==null||!c.requestDigest().matches("[a-f0-9]{64}"))throw new Conflict("TOPUP_COMMAND_INVALID");}
    private static void requireBuyer(View view,String buyerSubjectRef){if(blank(buyerSubjectRef)||!buyerSubjectRef.equals(view.buyerSubjectRef()))throw new Conflict("TOPUP_ORDER_OWNERSHIP_CONFLICT");}
    private static boolean blank(String s){return s==null||s.isBlank();} private interface Call{TopupProviderPort.Result get();}
    public enum State{RESERVED,SUBMITTED,PROCESSING,DELIVERED,REJECTED,UNKNOWN,RECONCILIATION_REQUIRED}
    public enum BalanceState{COVERED,SHORTFALL,UNKNOWN}
    public enum Begin{CREATED,REPLAY,CONFLICT}
    public record SubmitCommand(String merchantOrderRef,String requestRef,String requestDigest){}
    public record View(String merchantOrderRef,String requestRef,String requestDigest,String buyerSubjectRef,String providerSku,String recipient,String entitlementDigest,String providerRef,State state,long reservedMinor,
                       String reserveCurrency,int unknownQueriesRemaining,String evidenceRef,long version,Instant updatedAt){}
    public record BalanceReconciliation(BalanceState state,Long providerAvailableMinor,long locallyReservedMinor,
                                        String currency,String evidenceRef,Instant observedAt){}
    public interface Store{
        Begin beginAndReserve(TopupProviderPort.Command command,TopupEligibilityPort.Snapshot trusted,int unknownBudget); View require(String orderRef);
        void submissionResult(String orderRef,String providerRef,State state,String evidenceRef);
        void submissionRejectedAndRelease(String orderRef,String evidenceRef); void consumeUnknownQuery(String orderRef);
        void observation(String orderRef,State state,String evidenceRef); CallbackDisposition callback(TopupProviderPort.VerifiedCallback callback,State state);
        void reconciliationRequired(String orderRef,String evidenceRef); long reservedTotal(String currency);
        void recordBalanceObservation(String providerCode,String currency,long confirmedBalanceMinor,Instant observedAt,String evidenceRef);
    }
    public enum CallbackDisposition{ACCEPTED,REPLAY}
    public static final class Conflict extends RuntimeException{public Conflict(String code){super(code);}}
}
