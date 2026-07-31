package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "in-memory")
class InMemoryTaskLeaseStore implements TaskLeaseStore {
    private final Map<String, TaskRecord> tasks = new HashMap<>();

    @Override
    public synchronized void register(String taskKey, String taskType, String payloadJson, Instant availableAt) {
        TaskLeaseInputs.register(taskKey, availableAt);
        if (tasks.putIfAbsent(taskKey, new TaskRecord(taskKey, availableAt)) != null) {
            throw new TaskLeaseRejectedException("TASK_ALREADY_EXISTS");
        }
    }

    @Override
    public synchronized TaskLease claim(String taskKey, String owner, Instant now, Duration leaseDuration) {
        TaskLeaseInputs.lease(taskKey, owner, now, leaseDuration);
        TaskRecord task = requireTask(taskKey);
        if (task.availableAt.isAfter(now)) throw new TaskLeaseRejectedException("TASK_NOT_AVAILABLE");
        if (task.owner != null && task.leaseUntil != null && task.leaseUntil.isAfter(now)) {
            throw new TaskLeaseRejectedException("TASK_ALREADY_LEASED");
        }
        task.owner = owner;
        task.fencingToken++;
        task.leaseUntil = now.plus(leaseDuration);
        return task.lease();
    }

    @Override
    public synchronized TaskLease renew(String taskKey, String owner, long fencingToken, Instant now,
                                        Duration leaseDuration) {
        TaskLeaseInputs.lease(taskKey, owner, now, leaseDuration);
        TaskRecord task = requireTask(taskKey);
        assertCurrent(task, owner, fencingToken, now);
        task.leaseUntil = now.plus(leaseDuration);
        return task.lease();
    }

    @Override
    public synchronized void release(String taskKey, String owner, long fencingToken, Instant now) {
        TaskLeaseInputs.release(taskKey, owner, now);
        TaskRecord task = requireTask(taskKey);
        assertCurrent(task, owner, fencingToken, now);
        task.owner = null;
        task.leaseUntil = null;
    }

    private static void assertCurrent(TaskRecord task, String owner, long token, Instant now) {
        if (task.owner == null || !task.owner.equals(owner) || task.fencingToken != token) {
            throw new TaskLeaseRejectedException("STALE_FENCING_TOKEN");
        }
        if (task.leaseUntil == null || !task.leaseUntil.isAfter(now)) {
            throw new TaskLeaseRejectedException("LEASE_EXPIRED");
        }
    }

    private TaskRecord requireTask(String taskKey) {
        TaskRecord task = tasks.get(taskKey);
        if (task == null) throw new TaskLeaseRejectedException("TASK_NOT_FOUND");
        return task;
    }

    private static final class TaskRecord {
        private final String taskKey;
        private final Instant availableAt;
        private String owner;
        private Instant leaseUntil;
        private long fencingToken;

        private TaskRecord(String taskKey, Instant availableAt) {
            this.taskKey = taskKey;
            this.availableAt = availableAt;
        }

        private TaskLease lease() { return new TaskLease(taskKey, owner, fencingToken, leaseUntil); }
    }
}
