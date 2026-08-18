package com.huarenzaimeng.api.recovery;

/** Opens the human-only reconciliation boundary when automated recovery is exhausted. */
public interface RecoveryManualReviewStore {
    String open(RecoveryTaskStore.Task task, String reasonCode);

    RecoveryManualReviewStore NOOP = (task, reason) -> "NOOP-" + task.taskRef();
}
