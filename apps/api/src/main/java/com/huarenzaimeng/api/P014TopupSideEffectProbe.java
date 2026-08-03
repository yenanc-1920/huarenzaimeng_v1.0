package com.huarenzaimeng.api;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Collection;
import java.util.Map;

@Component
@Profile({"mock", "test"})
@ConditionalOnProperty(name="hz.p014.mode",havingValue="local-synthetic")
final class P014TopupSideEffectProbe {
    enum Counter { Command, CommandAlias, TopupBusinessKey, TopupSemanticAction, TopupIntent,
        DispatchSemanticAction, DispatchIntent, OrderVersion, ProjectionVersion, SyntheticObservation,
        PaymentAttempt, SendAttempt, RemoteAcceptance, WechatPrepay, RequestPayment, Notification,
        ExternalFact, W, U, D, L, LedgerEntry, ExternalCall, QueryCall }
    private final EnumMap<Counter, Long> counts = new EnumMap<>(Counter.class);
    private static final EnumSet<Counter> FORBIDDEN_REAL_BOUNDARIES = EnumSet.range(Counter.PaymentAttempt,
            Counter.ExternalCall);
    P014TopupSideEffectProbe() { reset(); }
    synchronized void increment(Counter counter) { counts.put(counter, counts.get(counter) + 1); }
    synchronized void incrementAll(Collection<Counter> counters) {
        for (Counter counter : counters) counts.put(counter, counts.get(counter) + 1);
    }
    boolean hasNoForbiddenDelta(Map<String, Long> before, Map<String, Long> after) {
        return FORBIDDEN_REAL_BOUNDARIES.stream().allMatch(counter -> before.containsKey(counter.name())
                && after.containsKey(counter.name()) && after.get(counter.name()).equals(before.get(counter.name())));
    }
    synchronized Map<String, Long> snapshot() {
        var result = new java.util.LinkedHashMap<String, Long>();
        for (Counter c : Counter.values()) result.put(c.name(), counts.get(c));
        return Map.copyOf(result);
    }
    synchronized void reset() { for (Counter c : Counter.values()) counts.put(c, 0L); }
}
