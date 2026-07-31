package com.huarenzaimeng.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FencedResultStoreTest {
    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    private InMemoryTaskLeaseStore leases;
    private InMemoryFencedResultStore results;
    private TaskLease current;

    @BeforeEach
    void setUp() {
        leases = new InMemoryTaskLeaseStore();
        leases.register("TASK-RESULT", "MOCK_RESULT", "{}", T0);
        current = leases.claim("TASK-RESULT", "WORKER-A", T0, Duration.ofSeconds(30));
        results = new InMemoryFencedResultStore(leases);
    }

    @Test
    void currentOwnerAppliesDomainLedgerAndOutboxExactlyOnce() {
        assertThat(results.write(command(current, "RESULT-1", "FP-1"), T0.plusSeconds(1)))
                .isEqualTo(FencedResultOutcome.applied());
        assertThat(results.snapshot()).isEqualTo(new FencedResultSnapshot(1, 1, 1));
    }

    @Test
    void exactReplayIsIdempotent() {
        FencedResultCommand command = command(current, "RESULT-1", "FP-1");
        results.write(command, T0.plusSeconds(1));
        assertThat(results.write(command, T0.plusSeconds(2))).isEqualTo(FencedResultOutcome.replayed());
        assertThat(results.snapshot()).isEqualTo(new FencedResultSnapshot(1, 1, 1));
    }

    @Test
    void reclaimedCurrentWorkerCanReplayAlreadyCommittedSameResultWithoutIncrement() {
        FencedResultCommand first = command(current, "RESULT-1", "FP-1");
        results.write(first, T0.plusSeconds(1));
        TaskLease reclaimed = leases.claim("TASK-RESULT", "WORKER-B", T0.plusSeconds(30),
                Duration.ofSeconds(30));
        assertThat(results.write(command(reclaimed, "RESULT-1", "FP-1"), T0.plusSeconds(31)))
                .isEqualTo(FencedResultOutcome.replayed());
        assertThat(results.snapshot()).isEqualTo(new FencedResultSnapshot(1, 1, 1));
    }

    @Test
    void conflictingResultEntersReviewWithoutAnyIncrement() {
        results.write(command(current, "RESULT-1", "FP-1"), T0.plusSeconds(1));
        assertThat(results.write(command(current, "RESULT-1", "FP-CONFLICT"), T0.plusSeconds(2)))
                .isEqualTo(FencedResultOutcome.review());
        assertThat(results.snapshot()).isEqualTo(new FencedResultSnapshot(1, 1, 1));
    }

    @Test
    void staleWorkerAfterReclaimCannotWriteAnyLayer() {
        TaskLease old = current;
        TaskLease next = leases.claim("TASK-RESULT", "WORKER-B", T0.plusSeconds(30), Duration.ofSeconds(30));
        assertThatThrownBy(() -> results.write(command(old, "RESULT-OLD", "FP-OLD"), T0.plusSeconds(31)))
                .hasMessage("STALE_FENCING_TOKEN");
        assertThat(results.snapshot()).isEqualTo(new FencedResultSnapshot(0, 0, 0));
        assertThat(results.write(command(next, "RESULT-NEW", "FP-NEW"), T0.plusSeconds(31)))
                .isEqualTo(FencedResultOutcome.applied());
    }

    @Test
    void expiredLeaseCannotWriteAnyLayer() {
        assertThatThrownBy(() -> results.write(command(current, "RESULT-1", "FP-1"), T0.plusSeconds(30)))
                .hasMessage("LEASE_EXPIRED");
        assertThat(results.snapshot()).isEqualTo(new FencedResultSnapshot(0, 0, 0));
    }

    @Test
    void allSimulatedCrashWindowsLeaveAllThreeLayersAtZero() {
        for (InMemoryFencedResultStore.FailurePoint point : new InMemoryFencedResultStore.FailurePoint[]{
                InMemoryFencedResultStore.FailurePoint.AFTER_ELIGIBILITY,
                InMemoryFencedResultStore.FailurePoint.AFTER_DOMAIN_STAGED,
                InMemoryFencedResultStore.FailurePoint.AFTER_LEDGER_STAGED}) {
            assertThatThrownBy(() -> results.write(command(current, "RESULT-" + point, "FP-" + point),
                    T0.plusSeconds(1), point)).isInstanceOf(InMemoryFencedResultStore.SimulatedCrashException.class);
            assertThat(results.snapshot()).isEqualTo(new FencedResultSnapshot(0, 0, 0));
        }
    }

    @Test
    void invalidInputFailsBeforeLeaseOrLayerMutation() {
        FencedResultCommand invalid = new FencedResultCommand("", "WORKER-A", 1, "R", "A", "F", "{}");
        assertThatThrownBy(() -> results.write(invalid, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(results.snapshot()).isEqualTo(new FencedResultSnapshot(0, 0, 0));
    }

    private static FencedResultCommand command(TaskLease lease, String resultKey, String fingerprint) {
        return new FencedResultCommand(lease.taskKey(), lease.owner(), lease.fencingToken(), resultKey,
                "ORDER-MOCK-1", fingerprint, "{\"mock\":true}");
    }
}
