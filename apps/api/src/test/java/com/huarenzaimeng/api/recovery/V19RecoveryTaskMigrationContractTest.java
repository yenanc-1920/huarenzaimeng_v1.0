package com.huarenzaimeng.api.recovery;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V19RecoveryTaskMigrationContractTest {
    @Test void migrationFreezesLeaseBudgetDeadlineAndAggregateOperationUniqueness() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V19__add_unknown_recovery_task.sql"));
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS hz_unknown_recovery_task")
                .contains("UNIQUE KEY uk_hz_unknown_recovery_aggregate_operation (aggregate_ref,operation_code)")
                .contains("expected_aggregate_version","budget_remaining","deadline","next_attempt_at")
                .contains("lease_owner","lease_until","task_version","completed_by_owner","completion_lease_version","manual_review_ref")
                .doesNotContain("provider_secret","access_token","phone_number");
    }
}
