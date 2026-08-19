package com.huarenzaimeng.api.recovery;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Controlled JDBC lease/CAS evidence only; this does not claim MySQL runtime evidence. */
class JdbcRecoveryTaskStoreTest {
    private JdbcRecoveryTaskStore store;
    private JdbcTemplate jdbc;
    private Instant base;
    private final List<String> manualReviews=new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach void setUp(){
        JdbcDataSource ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:recovery-"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE hz_unknown_recovery_task(task_ref VARCHAR(64) PRIMARY KEY,aggregate_ref VARCHAR(64) NOT NULL,operation_code VARCHAR(40) NOT NULL,expected_aggregate_version BIGINT NOT NULL,state_code VARCHAR(16) NOT NULL,budget_remaining INT NOT NULL,deadline TIMESTAMP NOT NULL,next_attempt_at TIMESTAMP NOT NULL,lease_owner VARCHAR(64),lease_until TIMESTAMP,task_version BIGINT NOT NULL,last_error_code VARCHAR(64),completed_by_owner VARCHAR(64),completion_lease_version BIGINT,manual_review_ref VARCHAR(64),created_at TIMESTAMP NOT NULL,updated_at TIMESTAMP NOT NULL,CONSTRAINT uk_recovery UNIQUE(aggregate_ref,operation_code))");
        manualReviews.clear();
        store=new JdbcRecoveryTaskStore(jdbc,(task,reason)->{manualReviews.add(task.operationCode()+":"+task.aggregateRef());return "MANUAL-"+task.taskRef();});
        base=Instant.parse("2026-08-18T01:00:00Z");
    }

    @Test void twoWorkersCannotLeaseSameTask() throws Exception {
        schedule("ORDER-1",1,3,base.plusSeconds(120));
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->claimAfterSignal("worker-a",ready,go));
            var b=pool.submit(()->claimAfterSignal("worker-b",ready,go));
            assertThat(ready.await(2,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(a.get(2,TimeUnit.SECONDS).size()+b.get(2,TimeUnit.SECONDS).size()).isEqualTo(1);
        }finally{pool.shutdownNow();}
        assertThat(store.require(taskRef("ORDER-1")).state()).isEqualTo(RecoveryTaskStore.State.LEASED);
    }

    @Test void expiredLeaseIsTakenOverAndOldWorkerResultIsRejected(){
        schedule("ORDER-2",1,3,base.plusSeconds(120));
        var first=claim("worker-old",base).get(0);
        assertThat(store.claimBatch("worker-new",1,base.plusSeconds(9),Duration.ofSeconds(10))).isEmpty();
        var takeover=store.claimBatch("worker-new",1,base.plusSeconds(11),Duration.ofSeconds(10)).get(0);
        assertThat(takeover.taskVersion()).isGreaterThan(first.taskVersion());
        assertThat(store.succeed(first.taskRef(),"worker-old",first.taskVersion(),base.plusSeconds(12))).isEqualTo(RecoveryTaskStore.Completion.REJECTED);
        var permitted=store.consumePermit(takeover.taskRef(),"worker-new",takeover.taskVersion(),base.plusSeconds(12)).orElseThrow();
        assertThat(store.succeed(permitted.taskRef(),"worker-new",permitted.taskVersion(),base.plusSeconds(13))).isEqualTo(RecoveryTaskStore.Completion.COMPLETED);
        assertThat(store.succeed(first.taskRef(),"worker-old",first.taskVersion(),base.plusSeconds(14))).isEqualTo(RecoveryTaskStore.Completion.REJECTED);
    }

    @Test void consumedPermitSurvivesCrashAndRestartWithoutRestoringBudget(){
        schedule("ORDER-3",1,2,base.plusSeconds(120));
        var claimed=claim("worker-crash",base).get(0);
        assertThat(store.consumePermit(claimed.taskRef(),"worker-crash",claimed.taskVersion(),base.plusSeconds(1)).orElseThrow().budgetRemaining()).isEqualTo(1);
        var recovered=store.claimBatch("worker-restart",1,base.plusSeconds(11),Duration.ofSeconds(10)).get(0);
        assertThat(recovered.budgetRemaining()).isEqualTo(1);
        assertThat(store.consumePermit(recovered.taskRef(),"worker-restart",recovered.taskVersion(),base.plusSeconds(12)).orElseThrow().budgetRemaining()).isZero();
    }

    @Test void providerIsCalledAtMostBudgetNAndThenTaskDies(){
        schedule("ORDER-4",1,2,base.plusSeconds(120));
        AtomicInteger calls=new AtomicInteger();AtomicReference<Instant> now=new AtomicReference<>(base);
        var handler=new RecoveryTaskWorker.Handler(){public String operationCode(){return "WECHAT_PAYMENT_QUERY";}public RecoveryTaskWorker.Outcome execute(RecoveryTaskStore.Task ignored){calls.incrementAndGet();return RecoveryTaskWorker.Outcome.RETRY;}};
        RecoveryTaskWorker worker=new RecoveryTaskWorker(store,List.of(handler),now::get);
        worker.runOnce("worker-budget",1,Duration.ofSeconds(10),Duration.ofSeconds(1));
        now.set(base.plusSeconds(2));worker.runOnce("worker-budget",1,Duration.ofSeconds(10),Duration.ofSeconds(1));
        now.set(base.plusSeconds(4));worker.runOnce("worker-budget",1,Duration.ofSeconds(10),Duration.ofSeconds(1));
        assertThat(calls).hasValue(2);
        assertThat(store.require(taskRef("ORDER-4")).state()).isEqualTo(RecoveryTaskStore.State.DEAD);
    }

    @Test void expiredDeadlineMakesZeroProviderCalls(){
        store.schedule("ORDER-5","WECHAT_PAYMENT_QUERY",1,3,base.minusSeconds(1),base.minusSeconds(2));
        AtomicInteger calls=new AtomicInteger();
        var handler=new RecoveryTaskWorker.Handler(){public String operationCode(){return "WECHAT_PAYMENT_QUERY";}public RecoveryTaskWorker.Outcome execute(RecoveryTaskStore.Task ignored){calls.incrementAndGet();return RecoveryTaskWorker.Outcome.SUCCEEDED;}};
        new RecoveryTaskWorker(store,List.of(handler),()->base).runOnce("worker-deadline",1,Duration.ofSeconds(10),Duration.ofSeconds(1));
        assertThat(calls).hasValue(0);
        assertThat(store.require(taskRef("ORDER-5")).state()).isEqualTo(RecoveryTaskStore.State.DEAD);
    }

    @Test void newerAggregateVersionResetsTerminalTaskAndInvalidatesOldLease(){
        schedule("ORDER-6",1,2,base.plusSeconds(120));
        var old=claim("worker-old",base).get(0);
        store.schedule("ORDER-6","WECHAT_PAYMENT_QUERY",2,4,base.plusSeconds(180),base.plusSeconds(1));
        assertThat(store.consumePermit(old.taskRef(),"worker-old",old.taskVersion(),base.plusSeconds(1))).isEmpty();
        var reset=store.require(old.taskRef());
        assertThat(reset.expectedAggregateVersion()).isEqualTo(2);
        assertThat(reset.state()).isEqualTo(RecoveryTaskStore.State.READY);
        assertThat(reset.budgetRemaining()).isEqualTo(4);
        assertThat(claim("worker-new",base.plusSeconds(1))).hasSize(1);
    }

    @Test void terminalCompletionIsIdempotent(){
        schedule("ORDER-7",1,1,base.plusSeconds(120));
        var claimed=claim("worker-terminal",base).get(0);
        var permitted=store.consumePermit(claimed.taskRef(),"worker-terminal",claimed.taskVersion(),base.plusSeconds(1)).orElseThrow();
        assertThat(store.succeed(permitted.taskRef(),"worker-terminal",permitted.taskVersion(),base.plusSeconds(2))).isEqualTo(RecoveryTaskStore.Completion.COMPLETED);
        assertThat(store.succeed(permitted.taskRef(),"worker-terminal",permitted.taskVersion(),base.plusSeconds(3))).isEqualTo(RecoveryTaskStore.Completion.REPLAY);
    }

    @Test void exhaustedPaymentRefundAndTopupTasksOpenOneManualReviewEach(){
        store.schedule("PAY-1","WECHAT_PAYMENT_QUERY",1,0,base.plusSeconds(1),base);
        store.schedule("REF-1","WECHAT_REFUND_QUERY",1,0,base.plusSeconds(1),base);
        store.schedule("TOP-1","WINLA_TOPUP_QUERY",1,0,base.plusSeconds(1),base);
        store.claimBatch("worker-manual",10,base,Duration.ofSeconds(10));
        store.claimBatch("worker-manual",10,base.plusSeconds(2),Duration.ofSeconds(10));
        assertThat(manualReviews).containsExactlyInAnyOrder(
                "WECHAT_PAYMENT_QUERY:PAY-1","WECHAT_REFUND_QUERY:REF-1","WINLA_TOPUP_QUERY:TOP-1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_unknown_recovery_task WHERE state_code='DEAD' AND manual_review_ref IS NOT NULL",Integer.class)).isEqualTo(3);
    }

    private void schedule(String aggregate,long version,int budget,Instant deadline){store.schedule(aggregate,"WECHAT_PAYMENT_QUERY",version,budget,deadline,base);}
    private List<RecoveryTaskStore.Task> claim(String owner,Instant now){return store.claimBatch(owner,1,now,Duration.ofSeconds(10));}
    private List<RecoveryTaskStore.Task> claimAfterSignal(String owner,CountDownLatch ready,CountDownLatch go){ready.countDown();try{go.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return claim(owner,base);}
    private String taskRef(String aggregate){return jdbc.queryForObject("SELECT task_ref FROM hz_unknown_recovery_task WHERE aggregate_ref=?",String.class,aggregate);}
}
