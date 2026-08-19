package com.huarenzaimeng.api.payment;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Controlled JDBC concurrency evidence only; this does not claim MySQL runtime evidence. */
class JdbcWeChatPayStoreConcurrencyTest {
    @Test void competingTerminalObservationsCommitExactlyOneState() throws Exception {
        JdbcTemplate jdbc=jdbc();
        jdbc.execute("CREATE TABLE hz_payment_coordination(merchant_order_ref VARCHAR(64) PRIMARY KEY,request_digest VARCHAR(64),buyer_subject_ref VARCHAR(128),quote_ref VARCHAR(64),price_snapshot_digest VARCHAR(64),amount_minor BIGINT,currency VARCHAR(3),provider_ref VARCHAR(128),state_code VARCHAR(32),evidence_ref VARCHAR(128),query_budget_remaining INT,query_deadline TIMESTAMP,refunded_minor BIGINT,aggregate_version BIGINT,updated_at TIMESTAMP)");
        addPrepayColumns(jdbc);
        JdbcWeChatPayStore store=new JdbcWeChatPayStore(jdbc);
        for(int i=0;i<20;i++){
            String ref="ORDER-"+i;
            jdbc.update("INSERT INTO hz_payment_coordination VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,NULL,NULL,NULL,NULL,NULL)",ref,"a".repeat(64),"BUYER","QUOTE","b".repeat(64),1000,"CNY",null,"UNKNOWN",null,2,Timestamp.from(Instant.now().plusSeconds(60)),0,1,Timestamp.from(Instant.now()));
            race(()->store.observation(ref,WeChatPayCoordinator.State.PAID,"PAID-EVIDENCE"),()->store.observation(ref,WeChatPayCoordinator.State.REJECTED,"REJECTED-EVIDENCE"));
            WeChatPayCoordinator.View view=store.require(ref);
            assertThat(view.state()).isIn(WeChatPayCoordinator.State.PAID,WeChatPayCoordinator.State.REJECTED);
            assertThat(view.version()).isEqualTo(2);
        }
    }
    @Test void unknownRefundCompetingSuccessAndRejectionStayAtomicallyConsistent() throws Exception {
        JdbcTemplate jdbc=refundJdbc();
        JdbcWeChatPayStore store=new JdbcWeChatPayStore(jdbc);
        for(int i=0;i<20;i++){
            String order="REFUND-ORDER-"+i,refund="REFUND-"+i;
            insertUnknownRefund(jdbc,order,refund);
            race(()->store.refundObservation(refund,WeChatPayCoordinator.RefundState.SUCCEEDED,"SUCCESS"),
                    ()->store.refundObservation(refund,WeChatPayCoordinator.RefundState.REJECTED,"REJECTED"));
            assertRefundConsistency(jdbc,store,order,refund);
        }
    }

    @Test void providerRefundResultAndQueryObservationCannotOverwriteEachOther() throws Exception {
        JdbcTemplate jdbc=refundJdbc();
        JdbcWeChatPayStore store=new JdbcWeChatPayStore(jdbc);
        for(int i=0;i<20;i++){
            String order="RESULT-ORDER-"+i,refund="RESULT-REFUND-"+i;
            insertUnknownRefund(jdbc,order,refund);
            race(()->store.refundResult(order,refund,new WeChatPayPort.Accepted("PROVIDER","SUCCEEDED","RESULT")),
                    ()->store.refundObservation(refund,WeChatPayCoordinator.RefundState.REJECTED,"QUERY"));
            assertRefundConsistency(jdbc,store,order,refund);
        }
    }

    private static JdbcTemplate refundJdbc(){
        JdbcTemplate jdbc=jdbc();
        jdbc.execute("CREATE TABLE hz_payment_coordination(merchant_order_ref VARCHAR(64) PRIMARY KEY,request_digest VARCHAR(64),buyer_subject_ref VARCHAR(128),quote_ref VARCHAR(64),price_snapshot_digest VARCHAR(64),amount_minor BIGINT,currency VARCHAR(3),provider_ref VARCHAR(128),state_code VARCHAR(32),evidence_ref VARCHAR(128),query_budget_remaining INT,query_deadline TIMESTAMP,refunded_minor BIGINT,aggregate_version BIGINT,updated_at TIMESTAMP)");
        addPrepayColumns(jdbc);
        jdbc.execute("CREATE TABLE hz_payment_refund(refund_ref VARCHAR(64) PRIMARY KEY,merchant_order_ref VARCHAR(64),request_digest VARCHAR(64),amount_minor BIGINT,state_code VARCHAR(32),original_payment_state VARCHAR(32),refund_query_budget_remaining INT,refund_query_deadline TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP)");
        return jdbc;
    }
    private static void insertUnknownRefund(JdbcTemplate jdbc,String order,String refund){
        Instant now=Instant.now();
        jdbc.update("INSERT INTO hz_payment_coordination VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,NULL,NULL,NULL,NULL,NULL)",order,"a".repeat(64),"BUYER","QUOTE","b".repeat(64),400,"CNY","P","REFUND_PROCESSING",null,2,Timestamp.from(now.plusSeconds(60)),400,1,Timestamp.from(now));
        jdbc.update("INSERT INTO hz_payment_refund VALUES (?,?,?,?,?,?,?,?,?,?)",refund,order,"c".repeat(64),400,"UNKNOWN","PAID",2,Timestamp.from(now.plusSeconds(60)),Timestamp.from(now),Timestamp.from(now));
    }
    private static void assertRefundConsistency(JdbcTemplate jdbc,JdbcWeChatPayStore store,String order,String refund){
        WeChatPayCoordinator.RefundView refundView=store.requireRefund(refund);
        WeChatPayCoordinator.View payment=store.require(order);
        assertThat(refundView.state()).isIn(WeChatPayCoordinator.RefundState.SUCCEEDED,WeChatPayCoordinator.RefundState.REJECTED);
        if(refundView.state()==WeChatPayCoordinator.RefundState.SUCCEEDED){
            assertThat(payment.state()).isEqualTo(WeChatPayCoordinator.State.REFUNDED);
            assertThat(payment.refundedMinor()).isEqualTo(400);
        }else{
            assertThat(payment.state()).isEqualTo(WeChatPayCoordinator.State.PAID);
            assertThat(payment.refundedMinor()).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_payment_refund WHERE refund_ref=?",Integer.class,refund)).isEqualTo(1);
    }
    private static JdbcTemplate jdbc(){JdbcDataSource dataSource=new JdbcDataSource();dataSource.setURL("jdbc:h2:mem:pay-cas-"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");return new JdbcTemplate(dataSource);}
    private static void race(Runnable one,Runnable two)throws Exception{ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);try{Future<?> a=pool.submit(()->{ready.countDown();await(go);one.run();});Future<?> b=pool.submit(()->{ready.countDown();await(go);two.run();});assertThat(ready.await(2,TimeUnit.SECONDS)).isTrue();go.countDown();a.get(2,TimeUnit.SECONDS);b.get(2,TimeUnit.SECONDS);}finally{pool.shutdownNow();}}
    private static void await(CountDownLatch latch){try{latch.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    private static void addPrepayColumns(JdbcTemplate jdbc){for(String column:java.util.List.of("prepay_timestamp VARCHAR(32)","prepay_nonce VARCHAR(32)","prepay_package VARCHAR(128)","prepay_sign_type VARCHAR(8)","prepay_pay_sign VARCHAR(1024)","prepay_expires_at TIMESTAMP"))jdbc.execute("ALTER TABLE hz_payment_coordination ADD "+column);}
}
