package com.huarenzaimeng.api.topup;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Controlled JDBC concurrency evidence only; this does not claim MySQL runtime evidence. */
class JdbcTopupStoreConcurrencyTest {
    @Test void competingTerminalObservationsCommitExactlyOneState() throws Exception {
        JdbcTemplate jdbc=jdbc();
        jdbc.execute("CREATE TABLE hz_topup_coordination(merchant_order_ref VARCHAR(64) PRIMARY KEY,request_ref VARCHAR(64),request_digest VARCHAR(64),buyer_subject_ref VARCHAR(128),provider_sku VARCHAR(128),recipient VARCHAR(32),entitlement_digest VARCHAR(64),provider_ref VARCHAR(128),state_code VARCHAR(32),reserved_minor BIGINT,reserve_currency VARCHAR(3),unknown_queries_remaining INT,evidence_ref VARCHAR(128),aggregate_version BIGINT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_provider_balance_reservation(reservation_ref VARCHAR(64) PRIMARY KEY,merchant_order_ref VARCHAR(64),provider_code VARCHAR(32),amount_minor BIGINT,currency VARCHAR(3),reservation_state VARCHAR(32),aggregate_version BIGINT DEFAULT 1,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_provider_balance_position(provider_code VARCHAR(32),currency VARCHAR(3),confirmed_balance_minor BIGINT,safety_buffer_minor BIGINT,active_reserved_minor BIGINT,observed_at TIMESTAMP,expires_at TIMESTAMP,evidence_ref VARCHAR(128),updated_at TIMESTAMP,PRIMARY KEY(provider_code,currency))");
        jdbc.update("INSERT INTO hz_provider_balance_position VALUES('WINLA','BDT',100000,0,0,CURRENT_TIMESTAMP,DATEADD('MINUTE',15,CURRENT_TIMESTAMP),'E',CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_provider_balance_ledger(entry_ref VARCHAR(64) PRIMARY KEY,merchant_order_ref VARCHAR(64),entry_type VARCHAR(32),amount_minor BIGINT,currency VARCHAR(3),occurred_at TIMESTAMP)");
        JdbcTopupStore store=new JdbcTopupStore(jdbc);
        for(int i=0;i<20;i++){
            String ref="ORDER-"+i;
            jdbc.update("INSERT INTO hz_topup_coordination VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",ref,"REQ-"+i,"a".repeat(64),"BUYER","SKU","8801712345678","b".repeat(64),"PROVIDER","UNKNOWN",1000,"BDT",2,null,1,Timestamp.from(Instant.now()));
            jdbc.update("INSERT INTO hz_provider_balance_reservation(reservation_ref,merchant_order_ref,provider_code,amount_minor,currency,reservation_state,aggregate_version,created_at,updated_at) VALUES (?,?, 'WINLA',?,?,'RESERVED',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)","RES-"+i,ref,1000,"BDT");
            jdbc.update("UPDATE hz_provider_balance_position SET active_reserved_minor=1000");
            race(()->store.observation(ref,TopupCoordinator.State.DELIVERED,"DELIVERED-EVIDENCE"),()->store.observation(ref,TopupCoordinator.State.REJECTED,"REJECTED-EVIDENCE"));
            TopupCoordinator.View view=store.require(ref);
            assertThat(view.state()).isIn(TopupCoordinator.State.DELIVERED,TopupCoordinator.State.REJECTED);
            assertThat(view.version()).isEqualTo(2);
        }
    }
    private static JdbcTemplate jdbc(){JdbcDataSource dataSource=new JdbcDataSource();dataSource.setURL("jdbc:h2:mem:topup-cas-"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");return new JdbcTemplate(dataSource);}
    private static void race(Runnable one,Runnable two)throws Exception{ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);try{Future<?> a=pool.submit(()->{ready.countDown();await(go);one.run();});Future<?> b=pool.submit(()->{ready.countDown();await(go);two.run();});assertThat(ready.await(2,TimeUnit.SECONDS)).isTrue();go.countDown();a.get(2,TimeUnit.SECONDS);b.get(2,TimeUnit.SECONDS);}finally{pool.shutdownNow();}}
    private static void await(CountDownLatch latch){try{latch.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
