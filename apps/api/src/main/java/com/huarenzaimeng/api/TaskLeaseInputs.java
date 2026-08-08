package com.huarenzaimeng.api;

import java.time.Duration;
import java.time.Instant;

final class TaskLeaseInputs {
    private TaskLeaseInputs() {}

    static void register(String taskKey, Instant availableAt) {
        if (taskKey == null || taskKey.isBlank() || availableAt == null) {
            throw new IllegalArgumentException("invalid task input");
        }
    }

    static void lease(String taskKey, String owner, Instant now, Duration duration) {
        release(taskKey, owner, now);
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("invalid lease input");
        }
    }

    static void release(String taskKey, String owner, Instant now) {
        if (taskKey == null || taskKey.isBlank() || owner == null || owner.isBlank() || now == null) {
            throw new IllegalArgumentException("invalid lease input");
        }
    }
}
