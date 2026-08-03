package com.huarenzaimeng.api;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
final class P021OrderDetailSideEffectProbe {
    enum Counter { Command, CommandAlias, TopupBusinessKey, TopupSemanticAction, TopupIntent,
        DispatchSemanticAction, DispatchIntent, OrderVersion, ProjectionVersion, SyntheticObservation,
        PaymentAttempt, SendAttempt, RemoteAcceptance, WechatPrepay, RequestPayment, Notification,
        ExternalFact, W, U, D, L, LedgerEntry, ExternalCall, QueryCall, FileWrite, QueueWrite,
        NotificationSend }

    private final EnumMap<Counter, Long> counts = new EnumMap<>(Counter.class);

    P021OrderDetailSideEffectProbe() { reset(); }

    synchronized void observeQuery() { incrementObserved(Counter.QueryCall); }

    BoundaryObserver observerFor(Counter expected) {
        if (expected == Counter.QueryCall) throw new IllegalArgumentException("QueryCall is not a write boundary");
        return mutation -> {
            if (mutation == null || mutation.counter() != expected || mutation.localSequence() < 1
                    || mutation.boundaryRef() == null || mutation.boundaryRef().isBlank()) {
                throw new IllegalArgumentException("boundary observation mismatch");
            }
            synchronized (this) { incrementObserved(expected); }
        };
    }

    private void incrementObserved(Counter counter) { counts.put(counter, counts.get(counter) + 1); }
    synchronized Map<String, Long> snapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        Counter[] values = Counter.values();
        for (Counter value : values) result.put(value.name(), counts.get(value));
        return Map.copyOf(result);
    }
    synchronized void reset() { for (Counter value : Counter.values()) counts.put(value, 0L); }

    interface BoundaryObserver { void observed(LocalBoundaryMutation mutation); }
    record LocalBoundaryMutation(Counter counter, String boundaryRef, long localSequence) {}
}
