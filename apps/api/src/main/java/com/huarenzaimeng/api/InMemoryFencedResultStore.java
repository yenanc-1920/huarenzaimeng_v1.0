package com.huarenzaimeng.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

class InMemoryFencedResultStore implements FencedResultStore {
    enum FailurePoint { NONE, AFTER_ELIGIBILITY, AFTER_DOMAIN_STAGED, AFTER_LEDGER_STAGED }

    private final InMemoryTaskLeaseStore leases;
    private final Map<String, StoredResult> domainResults = new LinkedHashMap<>();
    private final Map<String, String> ledgerMarkers = new LinkedHashMap<>();
    private final Map<String, String> outboxEvents = new LinkedHashMap<>();

    InMemoryFencedResultStore(InMemoryTaskLeaseStore leases) { this.leases = leases; }

    @Override
    public FencedResultOutcome write(FencedResultCommand command, Instant now) {
        return write(command, now, FailurePoint.NONE);
    }

    synchronized FencedResultOutcome write(FencedResultCommand command, Instant now, FailurePoint failurePoint) {
        FencedResultInputs.validate(command, now);
        leases.assertCurrentLease(command.taskKey(), command.owner(), command.fencingToken(), now);
        failAt(failurePoint, FailurePoint.AFTER_ELIGIBILITY);

        StoredResult existing = domainResults.get(command.resultKey());
        if (existing != null) {
            return existing.matches(command) ? FencedResultOutcome.replayed() : FencedResultOutcome.review();
        }

        StoredResult stagedDomain = StoredResult.from(command);
        failAt(failurePoint, FailurePoint.AFTER_DOMAIN_STAGED);
        String ledgerKey = "LEDGER:" + command.resultKey();
        failAt(failurePoint, FailurePoint.AFTER_LEDGER_STAGED);
        String outboxKey = "OUTBOX:" + command.resultKey();

        domainResults.put(command.resultKey(), stagedDomain);
        ledgerMarkers.put(ledgerKey, command.resultKey());
        outboxEvents.put(outboxKey, command.resultKey());
        return FencedResultOutcome.applied();
    }

    synchronized FencedResultSnapshot snapshot() {
        return new FencedResultSnapshot(domainResults.size(), ledgerMarkers.size(), outboxEvents.size());
    }

    private static void failAt(FailurePoint actual, FailurePoint expected) {
        if (actual == expected) throw new SimulatedCrashException(expected.name());
    }

    static final class SimulatedCrashException extends RuntimeException {
        private SimulatedCrashException(String point) { super(point); }
    }

    private record StoredResult(String taskKey, long fencingToken, String aggregateRef, String fingerprint,
                                String payloadJson) {
        static StoredResult from(FencedResultCommand command) {
            return new StoredResult(command.taskKey(), command.fencingToken(), command.aggregateRef(),
                    command.canonicalFingerprint(), command.payloadJson());
        }

        boolean matches(FencedResultCommand command) {
            return taskKey.equals(command.taskKey()) && aggregateRef.equals(command.aggregateRef())
                    && fingerprint.equals(command.canonicalFingerprint()) && payloadJson.equals(command.payloadJson());
        }
    }
}
