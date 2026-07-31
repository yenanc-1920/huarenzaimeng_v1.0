package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "mysql")
class MyBatisTaskLeaseStore implements TaskLeaseStore {
    private final FlowMapper mapper;
    private final TransactionTemplate transactions;

    MyBatisTaskLeaseStore(FlowMapper mapper, TransactionTemplate transactions) {
        this.mapper = mapper;
        this.transactions = transactions;
    }

    @Override
    public void register(String taskKey, String taskType, String payloadJson, Instant availableAt) {
        TaskLeaseInputs.register(taskKey, availableAt);
        mapper.insertTask(taskKey, taskType, payloadJson, Timestamp.from(availableAt), Timestamp.from(Instant.now()));
    }

    @Override
    public TaskLease claim(String taskKey, String owner, Instant now, Duration leaseDuration) {
        TaskLeaseInputs.lease(taskKey, owner, now, leaseDuration);
        return required(transactions.execute(status -> {
            Instant databaseNow = mapper.selectDatabaseNow().toInstant();
            TaskRow row = requireRow(mapper.selectTaskForUpdate(taskKey));
            if (row.availableAt().isAfter(databaseNow)) throw new TaskLeaseRejectedException("TASK_NOT_AVAILABLE");
            if (row.owner() != null && row.leaseUntil() != null && row.leaseUntil().isAfter(databaseNow)) {
                throw new TaskLeaseRejectedException("TASK_ALREADY_LEASED");
            }
            long nextToken = row.fencingToken() + 1;
            Instant until = databaseNow.plus(leaseDuration);
            int changed = mapper.claimTask(taskKey, owner, nextToken, Timestamp.from(until),
                    row.fencingToken(), Timestamp.from(databaseNow));
            if (changed != 1) throw new TaskLeaseRejectedException("LEASE_RACE_LOST");
            TaskRow stored = requireRow(mapper.selectTaskForUpdate(taskKey));
            return new TaskLease(taskKey, stored.owner(), stored.fencingToken(), stored.leaseUntil());
        }));
    }

    @Override
    public TaskLease renew(String taskKey, String owner, long fencingToken, Instant now, Duration leaseDuration) {
        TaskLeaseInputs.lease(taskKey, owner, now, leaseDuration);
        return required(transactions.execute(status -> {
            Instant databaseNow = mapper.selectDatabaseNow().toInstant();
            Instant until = databaseNow.plus(leaseDuration);
            int changed = mapper.renewTask(taskKey, owner, fencingToken, Timestamp.from(databaseNow),
                    Timestamp.from(until));
            if (changed != 1) throw new TaskLeaseRejectedException("STALE_OR_EXPIRED_LEASE");
            TaskRow stored = requireRow(mapper.selectTaskForUpdate(taskKey));
            return new TaskLease(taskKey, stored.owner(), stored.fencingToken(), stored.leaseUntil());
        }));
    }

    @Override
    public void release(String taskKey, String owner, long fencingToken, Instant now) {
        TaskLeaseInputs.release(taskKey, owner, now);
        Integer changed = transactions.execute(status -> {
            Timestamp databaseNow = mapper.selectDatabaseNow();
            return mapper.releaseTask(taskKey, owner, fencingToken, databaseNow, databaseNow);
        });
        if (changed == null || changed != 1) throw new TaskLeaseRejectedException("STALE_OR_EXPIRED_LEASE");
    }

    private static TaskRow requireRow(Map<String, Object> row) {
        if (row == null) throw new TaskLeaseRejectedException("TASK_NOT_FOUND");
        return new TaskRow(String.valueOf(row.get("task_key")), nullable(row.get("lease_owner")),
                row.get("lease_until") == null ? null : ((Timestamp) row.get("lease_until")).toInstant(),
                ((Number) row.get("fencing_token")).longValue(),
                ((Timestamp) row.get("available_at")).toInstant());
    }

    private static String nullable(Object value) { return value == null ? null : String.valueOf(value); }
    private static TaskLease required(TaskLease lease) {
        if (lease == null) throw new IllegalStateException("lease transaction returned no result");
        return lease;
    }

    private record TaskRow(String taskKey, String owner, Instant leaseUntil, long fencingToken,
                           Instant availableAt) {}
}
