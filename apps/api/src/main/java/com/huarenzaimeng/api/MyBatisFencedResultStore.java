package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "mysql")
class MyBatisFencedResultStore implements FencedResultStore {
    private final FlowMapper mapper;
    private final TransactionTemplate transactions;

    MyBatisFencedResultStore(FlowMapper mapper, TransactionTemplate transactions) {
        this.mapper = mapper;
        this.transactions = transactions;
    }

    @Override
    public FencedResultOutcome write(FencedResultCommand command, Instant now) {
        FencedResultInputs.validate(command, now);
        FencedResultOutcome outcome = transactions.execute(status -> {
            Instant databaseNow = mapper.selectDatabaseNow().toInstant();
            Map<String, Object> lease = mapper.selectTaskForUpdate(command.taskKey());
            assertCurrentLease(lease, command, databaseNow);

            Map<String, Object> existing = mapper.selectFencedResultForUpdate(command.resultKey());
            if (existing != null) {
                return matches(existing, command) ? FencedResultOutcome.replayed() : FencedResultOutcome.review();
            }

            Timestamp createdAt = Timestamp.from(databaseNow);
            if (mapper.insertFencedDomainResult(command.resultKey(), command.taskKey(), command.owner(),
                    command.fencingToken(), command.aggregateRef(), command.canonicalFingerprint(),
                    command.payloadJson(), createdAt) != 1) throw new IllegalStateException("domain result not inserted");
            if (mapper.insertFencedLedgerMarker("LEDGER:" + command.resultKey(), command.resultKey(),
                    command.taskKey(), command.fencingToken(), createdAt) != 1) {
                throw new IllegalStateException("ledger marker not inserted");
            }
            if (mapper.insertFencedOutbox("OUTBOX:" + command.resultKey(), command.aggregateRef(),
                    command.resultKey(), command.fencingToken(), createdAt) != 1) {
                throw new IllegalStateException("outbox event not inserted");
            }
            return FencedResultOutcome.applied();
        });
        if (outcome == null) throw new IllegalStateException("fenced result transaction returned no result");
        return outcome;
    }

    private static void assertCurrentLease(Map<String, Object> row, FencedResultCommand command, Instant databaseNow) {
        if (row == null) throw new TaskLeaseRejectedException("TASK_NOT_FOUND");
        String owner = row.get("lease_owner") == null ? null : String.valueOf(row.get("lease_owner"));
        long token = ((Number) row.get("fencing_token")).longValue();
        Instant leaseUntil = row.get("lease_until") == null ? null
                : ((Timestamp) row.get("lease_until")).toInstant();
        if (!command.owner().equals(owner) || command.fencingToken() != token) {
            throw new TaskLeaseRejectedException("STALE_FENCING_TOKEN");
        }
        if (leaseUntil == null || !leaseUntil.isAfter(databaseNow)) {
            throw new TaskLeaseRejectedException("LEASE_EXPIRED");
        }
    }

    private static boolean matches(Map<String, Object> row, FencedResultCommand command) {
        return String.valueOf(row.get("task_key")).equals(command.taskKey())
                && String.valueOf(row.get("aggregate_ref")).equals(command.aggregateRef())
                && String.valueOf(row.get("canonical_fingerprint")).equals(command.canonicalFingerprint());
    }
}
