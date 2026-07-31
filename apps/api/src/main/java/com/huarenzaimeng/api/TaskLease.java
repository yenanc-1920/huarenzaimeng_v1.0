package com.huarenzaimeng.api;

import java.time.Instant;

record TaskLease(String taskKey, String owner, long fencingToken, Instant leaseUntil) {}
