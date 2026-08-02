package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "hz.life-content.mode", havingValue = "local-synthetic")
class LifeContentSideEffectProbe {
    enum Boundary {
        QueryCall,
        ContentItem,
        ContentVersion,
        PublicationQualification,
        AuditEvent,
        ReportCandidate,
        SupportCase,
        Command,
        SemanticAction,
        Order,
        PaymentIntent,
        PaymentAttempt,
        DispatchIntent,
        AcceptedW,
        AcceptedU,
        AcceptedD,
        AcceptedR,
        AcceptedL,
        LedgerEntry,
        A120PublishAction,
        ExternalCall,
        FileWrite,
        DatabaseWrite,
        QueueWrite,
        BackupWrite,
        PersistentLogWrite,
        ExternalAnalytics
    }

    private final long[] counts = new long[Boundary.values().length];

    synchronized void observeQuery() { counts[Boundary.QueryCall.ordinal()]++; }

    synchronized Map<String, Long> snapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Boundary boundary : Boundary.values()) result.put(boundary.name(), counts[boundary.ordinal()]);
        return Map.copyOf(result);
    }

    synchronized void observeForSensitivityTest(Boundary boundary) { counts[boundary.ordinal()]++; }

    synchronized void resetForTest() {
        java.util.Arrays.fill(counts, 0L);
    }
}
