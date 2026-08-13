package com.huarenzaimeng.api;

enum StateAdvanceConcurrentResult {
    REPLAYED, IDEMPOTENCY_CONFLICT, STORAGE_INTEGRITY_CONFLICT, CONCURRENT_RESULT_UNKNOWN
}
