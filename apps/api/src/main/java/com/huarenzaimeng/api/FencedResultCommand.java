package com.huarenzaimeng.api;

record FencedResultCommand(
        String taskKey,
        String owner,
        long fencingToken,
        String resultKey,
        String aggregateRef,
        String canonicalFingerprint,
        String payloadJson
) {}
