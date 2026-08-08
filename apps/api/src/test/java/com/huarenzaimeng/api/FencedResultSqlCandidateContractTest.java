package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FencedResultSqlCandidateContractTest {
    @Test
    void mysqlCandidateChecksLeaseAndWritesThreeLayersInOneTransaction() throws Exception {
        String store = Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/MyBatisFencedResultStore.java"));
        assertThat(store).contains("TransactionTemplate", "transactions.execute", "selectDatabaseNow",
                "selectTaskForUpdate", "insertFencedDomainResult", "insertFencedLedgerMarker",
                "insertFencedOutbox", "STALE_FENCING_TOKEN", "LEASE_EXPIRED");
    }

    @Test
    void migrationCandidateUsesUniqueResultLedgerAndOutboxKeysButRemainsNotRun() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V2__add_subject_scoped_commands_versions_and_worker_tables.sql"));
        String runbook = Files.readString(Path.of("src/main/resources/db/migration/TASK_LEASE_CANDIDATE_RUNBOOK.md"));
        assertThat(migration).contains("PRIMARY KEY (result_key)", "PRIMARY KEY (marker_key)",
                "UNIQUE KEY uk_hz_task_ledger_marker_result");
        assertThat(runbook).contains("未在 MySQL 执行", "未接真实资金端点", "未选择队列");
    }
}
