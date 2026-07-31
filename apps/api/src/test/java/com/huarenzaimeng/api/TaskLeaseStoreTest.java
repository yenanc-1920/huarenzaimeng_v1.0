package com.huarenzaimeng.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskLeaseStoreTest {
    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(30);
    private InMemoryTaskLeaseStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryTaskLeaseStore();
        store.register("TASK-1", "MOCK_PROJECTION", "{}", T0);
    }

    @Test
    void onlyOneCurrentOwnerCanHoldLiveLease() {
        TaskLease first = store.claim("TASK-1", "WORKER-A", T0, LEASE);
        assertThat(first.fencingToken()).isEqualTo(1);
        assertThatThrownBy(() -> store.claim("TASK-1", "WORKER-B", T0.plusSeconds(1), LEASE))
                .hasMessage("TASK_ALREADY_LEASED");
    }

    @Test
    void renewKeepsTokenAndExtendsLease() {
        TaskLease first = store.claim("TASK-1", "WORKER-A", T0, LEASE);
        TaskLease renewed = store.renew("TASK-1", "WORKER-A", first.fencingToken(),
                T0.plusSeconds(20), LEASE);
        assertThat(renewed.fencingToken()).isEqualTo(first.fencingToken());
        assertThat(renewed.leaseUntil()).isEqualTo(T0.plusSeconds(50));
        assertThatThrownBy(() -> store.claim("TASK-1", "WORKER-B", T0.plusSeconds(31), LEASE))
                .hasMessage("TASK_ALREADY_LEASED");
    }

    @Test
    void crashWithoutReleaseAllowsReclaimOnlyAfterExpiryWithHigherToken() {
        TaskLease abandoned = store.claim("TASK-1", "WORKER-A", T0, LEASE);
        TaskLease reclaimed = store.claim("TASK-1", "WORKER-B", T0.plusSeconds(30), LEASE);
        assertThat(reclaimed.owner()).isEqualTo("WORKER-B");
        assertThat(reclaimed.fencingToken()).isGreaterThan(abandoned.fencingToken());
    }

    @Test
    void staleWorkerCannotRenewOrReleaseAfterReclaim() {
        TaskLease old = store.claim("TASK-1", "WORKER-A", T0, LEASE);
        TaskLease current = store.claim("TASK-1", "WORKER-B", T0.plusSeconds(30), LEASE);
        assertThatThrownBy(() -> store.renew("TASK-1", "WORKER-A", old.fencingToken(),
                T0.plusSeconds(31), LEASE)).hasMessage("STALE_FENCING_TOKEN");
        assertThatThrownBy(() -> store.release("TASK-1", "WORKER-A", old.fencingToken(),
                T0.plusSeconds(31))).hasMessage("STALE_FENCING_TOKEN");
        assertThat(store.renew("TASK-1", "WORKER-B", current.fencingToken(),
                T0.plusSeconds(31), LEASE).owner()).isEqualTo("WORKER-B");
    }

    @Test
    void releaseMakesTaskAvailableAndNextClaimAdvancesFence() {
        TaskLease first = store.claim("TASK-1", "WORKER-A", T0, LEASE);
        store.release("TASK-1", "WORKER-A", first.fencingToken(), T0.plusSeconds(1));
        TaskLease second = store.claim("TASK-1", "WORKER-B", T0.plusSeconds(1), LEASE);
        assertThat(second.fencingToken()).isEqualTo(first.fencingToken() + 1);
    }

    @Test
    void expiredOwnerCannotRenewWithoutReclaim() {
        TaskLease first = store.claim("TASK-1", "WORKER-A", T0, LEASE);
        assertThatThrownBy(() -> store.renew("TASK-1", "WORKER-A", first.fencingToken(),
                T0.plusSeconds(30), LEASE)).hasMessage("LEASE_EXPIRED");
    }

    @Test
    void futureTaskCannotBeClaimedEarly() {
        store.register("TASK-FUTURE", "MOCK_PROJECTION", "{}", T0.plusSeconds(60));
        assertThatThrownBy(() -> store.claim("TASK-FUTURE", "WORKER-A", T0, LEASE))
                .hasMessage("TASK_NOT_AVAILABLE");
    }

    @Test
    void invalidParametersFailBeforeChangingLeaseState() {
        assertThatThrownBy(() -> store.claim("", "WORKER-A", T0, LEASE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.claim("TASK-1", "", T0, LEASE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.claim("TASK-1", "WORKER-A", null, LEASE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.claim("TASK-1", "WORKER-A", T0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.claim("TASK-1", "WORKER-A", T0, LEASE).fencingToken()).isEqualTo(1);
    }
}
