package com.huarenzaimeng.api.recovery;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RecoveryTaskStore {
    enum State { READY, LEASED, SUCCEEDED, DEAD }
    enum Completion { COMPLETED, REPLAY, REJECTED }
    record Task(String taskRef,String aggregateRef,String operationCode,long expectedAggregateVersion,State state,
                int budgetRemaining,Instant deadline,Instant nextAttemptAt,String leaseOwner,Instant leaseUntil,
                long taskVersion,String lastErrorCode,String manualReviewRef) {}

    void schedule(String aggregateRef,String operationCode,long expectedAggregateVersion,int budget,Instant deadline,Instant nextAttemptAt);
    List<Task> claimBatch(String leaseOwner,int limit,Instant now,Duration leaseDuration);
    Optional<Task> consumePermit(String taskRef,String leaseOwner,long taskVersion,Instant now);
    Completion succeed(String taskRef,String leaseOwner,long taskVersion,Instant now);
    Completion retry(String taskRef,String leaseOwner,long taskVersion,Instant now,Instant nextAttemptAt,String errorCode);
    Completion dead(String taskRef,String leaseOwner,long taskVersion,Instant now,String errorCode);
    Task require(String taskRef);
}
