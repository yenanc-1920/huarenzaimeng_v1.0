package com.huarenzaimeng.api.buyerauth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BuyerPiiCleanupWorkerTest {
    private static final Instant NOW=Instant.parse("2026-08-18T12:00:00Z");
    private static final BuyerPiiCleanupTaskStore.Claim CLAIM=new BuyerPiiCleanupTaskStore.Claim("CLEANUP-1","CLOSURE-1","BUYER-1",1,"worker-1",2);

    @Test void commitThenRepeatDoesNotRunCleanupTwice(){
        BuyerPiiCleanupTaskStore tasks=mock(BuyerPiiCleanupTaskStore.class);BuyerAccountLifecycleService lifecycle=mock(BuyerAccountLifecycleService.class);
        when(tasks.claim(anyString(),any(),any())).thenReturn(Optional.of(CLAIM),Optional.empty());
        BuyerPiiCleanupWorker worker=worker(tasks,lifecycle);
        assertThat(worker.runOnce("worker-1")).isTrue();assertThat(worker.runOnce("worker-1")).isFalse();
        verify(lifecycle,times(1)).completeClaimed(CLAIM);verify(tasks,never()).retry(any(),anyString(),any(),any());
    }

    @Test void failedAttemptIsReleasedForRetryAndExpiredLeaseCanBeRecovered(){
        BuyerPiiCleanupTaskStore tasks=mock(BuyerPiiCleanupTaskStore.class);BuyerAccountLifecycleService lifecycle=mock(BuyerAccountLifecycleService.class);
        when(tasks.claim(anyString(),any(),any())).thenReturn(Optional.of(CLAIM),Optional.empty(),Optional.of(CLAIM));
        when(lifecycle.completeClaimed(CLAIM)).thenThrow(new BuyerAccountLifecycleService.Conflict("SYNTHETIC_CRASH_BEFORE_COMMIT")).thenReturn(null);
        BuyerPiiCleanupWorker worker=worker(tasks,lifecycle);
        assertThat(worker.runOnce("worker-1")).isFalse();
        verify(tasks).retry(eq(CLAIM),eq("SYNTHETIC_CRASH_BEFORE_COMMIT"),eq(NOW),eq(Duration.ofSeconds(5)));
        assertThat(worker.runOnce("worker-1")).isFalse();
        assertThat(worker.runOnce("worker-1")).isTrue();
        verify(lifecycle,times(2)).completeClaimed(CLAIM);
    }

    private static BuyerPiiCleanupWorker worker(BuyerPiiCleanupTaskStore tasks,BuyerAccountLifecycleService lifecycle){
        return new BuyerPiiCleanupWorker(tasks,lifecycle,Clock.fixed(NOW,ZoneOffset.UTC),Duration.ofSeconds(10),Duration.ofSeconds(5));
    }
}
