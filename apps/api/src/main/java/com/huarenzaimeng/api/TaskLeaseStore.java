package com.huarenzaimeng.api;

import java.time.Duration;
import java.time.Instant;

interface TaskLeaseStore {
    void register(String taskKey, String taskType, String payloadJson, Instant availableAt);

    TaskLease claim(String taskKey, String owner, Instant now, Duration leaseDuration);

    TaskLease renew(String taskKey, String owner, long fencingToken, Instant now, Duration leaseDuration);

    void release(String taskKey, String owner, long fencingToken, Instant now);
}
