package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Profile({"mock", "test"})
@ConditionalOnProperty(name = "hz.temporal-overview.mode", havingValue = "local-synthetic")
class TemporalOverviewSideEffectProbe {
    enum Boundary {
        QueryCall,
        Command, CommandAlias, SemanticAction, ContentItem, ContentVersion, HolidayRecord, AuditEvent,
        Notification, Reminder, SupportCase, Order, PaymentIntent, PaymentAttempt,
        AcceptedW, AcceptedU, AcceptedD, AcceptedR, AcceptedL, LedgerEntry, A120Action, ExternalCall,
        PersistenceWrite, QueueWrite, FileWrite, NotificationCall
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
