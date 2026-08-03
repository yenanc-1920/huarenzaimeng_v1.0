package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Profile({"mock", "test"})
@ConditionalOnProperty(name = "hz.a110.mode", havingValue = "local-synthetic")
class A110ReconciliationSideEffectProbe {
    enum Boundary {
        QueryCall, Command, AuditEvent, SupportCase, AcceptedW, AcceptedU, AcceptedD, AcceptedR, AcceptedL,
        LedgerEntry, RefundAction, ReconciliationAction, ExternalCall, PersistenceWrite, QueueWrite, FileWrite
    }

    private final long[] counts = new long[Boundary.values().length];

    synchronized void observeQuery() { counts[Boundary.QueryCall.ordinal()]++; }

    synchronized Map<String, Long> snapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Boundary boundary : Boundary.values()) result.put(boundary.name(), counts[boundary.ordinal()]);
        return Map.copyOf(result);
    }

    synchronized void observeForSensitivityTest(Boundary boundary) { counts[boundary.ordinal()]++; }

    synchronized void resetForTest() { Arrays.fill(counts, 0L); }
}
