package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskLeaseSqlCandidateContractTest {
    @Test
    void mapperCandidateUsesLockFenceOwnerAndExpiryPredicates() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/FlowMapper.java"));
        assertThat(source).contains("FOR UPDATE", "fencing_token=#{expectedToken}",
                "lease_owner=#{owner}", "fencing_token=#{token}", "lease_until > #{now}");
    }

    @Test
    void mysqlCandidateKeepsLeaseMutationsInsideExplicitTransactions() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/MyBatisTaskLeaseStore.java"));
        assertThat(source).contains("TransactionTemplate", "transactions.execute", "selectTaskForUpdate")
                .doesNotContain("PASS_MYSQL", "VERIFIED_MYSQL");
    }

    @Test
    void differentWorkerClocksProduceSameLeaseFromDatabaseClockCandidate() {
        TaskLease slowWorker = claimUsingDatabaseClock(Instant.parse("2020-01-01T00:00:00Z"));
        TaskLease fastWorker = claimUsingDatabaseClock(Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(slowWorker.leaseUntil()).isEqualTo(Instant.parse("2026-08-01T00:00:30Z"));
        assertThat(fastWorker).isEqualTo(slowWorker);
    }

    @Test
    void leaseRowsAcceptLocalDateTimeReturnedByMyBatis() {
        Instant databaseNow = Instant.parse("2026-08-01T00:00:00Z");
        FlowMapper mapper = mock(FlowMapper.class);
        TransactionTemplate transactions = immediateTransactions();
        Map<String, Object> before = taskRow(null, null, 0, databaseNow.minusSeconds(1));
        Map<String, Object> after = taskRow("WORKER-A", databaseNow.plusSeconds(30), 1,
                databaseNow.minusSeconds(1));
        after.put("lease_until", java.time.LocalDateTime.of(2026, 8, 1, 0, 0, 30));
        after.put("available_at", java.time.LocalDateTime.of(2026, 7, 31, 23, 59, 59));
        when(mapper.selectDatabaseNow()).thenReturn(Timestamp.from(databaseNow));
        when(mapper.selectTaskForUpdate("TASK-LOCAL-DATETIME")).thenReturn(before, after);
        when(mapper.claimTask(anyString(), anyString(), anyLong(), any(), anyLong(), any())).thenReturn(1);

        TaskLease lease = new MyBatisTaskLeaseStore(mapper, transactions).claim("TASK-LOCAL-DATETIME", "WORKER-A",
                Instant.EPOCH, Duration.ofSeconds(30));

        assertThat(lease.leaseUntil()).isEqualTo(Timestamp.valueOf("2026-08-01 00:00:30").toInstant());
    }

    @Test
    void invalidMysqlCandidateInputFailsBeforeTransactionOrSql() {
        FlowMapper mapper = mock(FlowMapper.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        MyBatisTaskLeaseStore store = new MyBatisTaskLeaseStore(mapper, transactions);
        assertThatThrownBy(() -> store.claim("", "WORKER-A", Instant.EPOCH, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.renew("TASK", "WORKER-A", 1, null, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.release("TASK", "", 1, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper, transactions);
    }

    private static TaskLease claimUsingDatabaseClock(Instant workerNow) {
        Instant databaseNow = Instant.parse("2026-08-01T00:00:00Z");
        FlowMapper mapper = mock(FlowMapper.class);
        TransactionTemplate transactions = immediateTransactions();
        Map<String, Object> before = taskRow(null, null, 0, databaseNow.minusSeconds(1));
        Map<String, Object> after = taskRow("WORKER-A", databaseNow.plusSeconds(30), 1,
                databaseNow.minusSeconds(1));
        when(mapper.selectDatabaseNow()).thenReturn(Timestamp.from(databaseNow));
        when(mapper.selectTaskForUpdate("TASK-CLOCK")).thenReturn(before, after);
        when(mapper.claimTask(anyString(), anyString(), anyLong(), any(), anyLong(), any())).thenReturn(1);
        return new MyBatisTaskLeaseStore(mapper, transactions).claim("TASK-CLOCK", "WORKER-A", workerNow,
                Duration.ofSeconds(30));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TransactionTemplate immediateTransactions() {
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(call ->
                ((TransactionCallback) call.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        return transactions;
    }

    private static Map<String, Object> taskRow(String owner, Instant leaseUntil, long token, Instant availableAt) {
        Map<String, Object> row = new HashMap<>();
        row.put("task_key", "TASK-CLOCK");
        row.put("lease_owner", owner);
        row.put("lease_until", leaseUntil == null ? null : Timestamp.from(leaseUntil));
        row.put("fencing_token", token);
        row.put("available_at", Timestamp.from(availableAt));
        return row;
    }
}
