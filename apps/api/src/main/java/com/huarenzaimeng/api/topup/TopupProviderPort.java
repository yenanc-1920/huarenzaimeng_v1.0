package com.huarenzaimeng.api.topup;

import java.time.Instant;

/** Supplier-neutral recharge boundary. Submit is single-shot; ambiguity must be queried, never resubmitted. */
public interface TopupProviderPort {
    default boolean available() { return true; }
    Result submit(Command command);
    Result query(String providerRef, String merchantOrderRef);
    BalanceResult balance();
    CallbackResult verifyCallback(CallbackEnvelope envelope);

    record Command(String merchantOrderRef,String requestRef,String providerSku,String recipient,
                   long faceValueMinor,String targetCurrency,String requestDigest) {}
    record CallbackEnvelope(String callbackId,String timestamp,String nonce,String signature,String body) {}
    sealed interface Result permits Accepted,Rejected,Unknown {}
    record Accepted(String providerRef,String state,String evidenceRef) implements Result {}
    record Rejected(String reasonCode) implements Result {}
    record Unknown(String reasonCode) implements Result {}
    sealed interface BalanceResult permits BalanceObserved,BalanceUnknown {}
    record BalanceObserved(long availableMinor,String currency,String evidenceRef,Instant observedAt) implements BalanceResult {}
    record BalanceUnknown(String reasonCode) implements BalanceResult {}
    sealed interface CallbackResult permits VerifiedCallback,InvalidCallback,CallbackUnknown {}
    record VerifiedCallback(String callbackId,String callbackDigest,String merchantOrderRef,String providerRef,String state,
                            Long amountMinor,String currency,String providerSku,String recipient,
                            Instant occurredAt,String evidenceRef) implements CallbackResult {}
    record InvalidCallback(String reasonCode) implements CallbackResult {}
    record CallbackUnknown(String reasonCode) implements CallbackResult {}
}
