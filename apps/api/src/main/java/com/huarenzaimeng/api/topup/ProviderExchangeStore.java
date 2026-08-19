package com.huarenzaimeng.api.topup;

import java.time.Instant;

/** Immutable provider-wire evidence. Raw values are insert-only and never exposed to coordinators. */
public interface ProviderExchangeStore {
    SaveResult record(Attempt attempt);
    Original requireOriginal(String providerCode,String merchantOrderRef);

    enum Operation { SUBMIT, QUERY, CALLBACK }
    enum NormalizationStatus { VALIDATED, INVALID_AMOUNT, INVALID_CURRENCY, BINDING_MISMATCH, UNKNOWN_STATE }
    enum SaveResult { CREATED, REPLAY }

    record Attempt(String exchangeRef,String providerCode,Operation operation,String merchantOrderRef,
                   String providerRequestRef,long localAmountMinor,String localCurrency,
                   String providerAmountRaw,String providerCurrencyRaw,String providerSku,
                   String recipientDigest,String canonicalRequestDigest,String rawResponseDigest,
                   String providerRef,String contractVersion,Instant observedAt,Long normalizedAmountMinor,
                   String normalizedCurrency,NormalizationStatus normalizationStatus) {}
    record Original(String merchantOrderRef,String providerRef,long localAmountMinor,String localCurrency,
                    String providerSku,String recipientDigest,String canonicalRequestDigest,String contractVersion) {}
    final class Conflict extends RuntimeException { public Conflict(String code){super(code);} }
}
