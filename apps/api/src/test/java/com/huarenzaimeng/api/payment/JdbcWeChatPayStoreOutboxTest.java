package com.huarenzaimeng.api.payment;

import com.huarenzaimeng.api.BusinessEventStore;
import com.huarenzaimeng.api.recovery.RecoveryTaskStore;
import com.huarenzaimeng.core.BusinessEventLinker;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcWeChatPayStoreOutboxTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;

    @BeforeEach void setUp(){
        JdbcDataSource ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:pay-outbox-"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);
        tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE hz_payment_coordination(merchant_order_ref VARCHAR(64) PRIMARY KEY,request_digest VARCHAR(64),buyer_subject_ref VARCHAR(128),quote_ref VARCHAR(64),price_snapshot_digest VARCHAR(64),amount_minor BIGINT,currency VARCHAR(3),provider_ref VARCHAR(128),state_code VARCHAR(32),evidence_ref VARCHAR(128),query_budget_remaining INT,query_deadline TIMESTAMP,refunded_minor BIGINT,aggregate_version BIGINT,updated_at TIMESTAMP)");
        for(String column:java.util.List.of("prepay_timestamp VARCHAR(32)","prepay_nonce VARCHAR(32)","prepay_package VARCHAR(128)","prepay_sign_type VARCHAR(8)","prepay_pay_sign VARCHAR(1024)","prepay_expires_at TIMESTAMP"))jdbc.execute("ALTER TABLE hz_payment_coordination ADD "+column);
        jdbc.execute("CREATE TABLE hz_payment_refund(refund_ref VARCHAR(64) PRIMARY KEY,merchant_order_ref VARCHAR(64),request_digest VARCHAR(64),amount_minor BIGINT,state_code VARCHAR(32),original_payment_state VARCHAR(32),refund_query_budget_remaining INT,refund_query_deadline TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE event_probe(event_ref VARCHAR(64) PRIMARY KEY,order_ref VARCHAR(64),event_type VARCHAR(40),event_digest VARCHAR(64))");
    }

    @Test void synchronousProviderResultAndQueryObservationEachEmitExactlyOnePaymentConfirmed(){
        JdbcWeChatPayStore store=new JdbcWeChatPayStore(jdbc,new ProbeEvents(jdbc,false));
        insert("PAY-SYNC","a".repeat(64));insert("PAY-QUERY","b".repeat(64));

        tx.executeWithoutResult(ignored->store.providerResult("PAY-SYNC","WX-1",WeChatPayCoordinator.State.PAID,"SYNC-EVIDENCE"));
        tx.executeWithoutResult(ignored->store.providerResult("PAY-SYNC","WX-1",WeChatPayCoordinator.State.PAID,"SYNC-EVIDENCE"));
        tx.executeWithoutResult(ignored->store.observation("PAY-QUERY",WeChatPayCoordinator.State.PAID,"QUERY-EVIDENCE"));
        tx.executeWithoutResult(ignored->store.observation("PAY-QUERY",WeChatPayCoordinator.State.PAID,"QUERY-EVIDENCE"));

        assertThat(store.require("PAY-SYNC").state()).isEqualTo(WeChatPayCoordinator.State.PAID);
        assertThat(store.require("PAY-QUERY").state()).isEqualTo(WeChatPayCoordinator.State.PAID);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_probe WHERE event_type='PAYMENT_CONFIRMED'",Integer.class)).isEqualTo(2);
    }

    @Test void paymentConfirmedEventFailureRollsBackPaidTransition(){
        JdbcWeChatPayStore store=new JdbcWeChatPayStore(jdbc,new ProbeEvents(jdbc,true));
        insert("PAY-ROLLBACK","c".repeat(64));

        assertThatThrownBy(()->tx.executeWithoutResult(ignored->store.observation("PAY-ROLLBACK",WeChatPayCoordinator.State.PAID,"QUERY-EVIDENCE")))
                .hasMessage("INJECTED_EVENT_FAILURE");

        assertThat(store.require("PAY-ROLLBACK").state()).isEqualTo(WeChatPayCoordinator.State.PREPAY_CREATED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_probe",Integer.class)).isZero();
    }

    @Test void paidProducingStoreMethodsAreTransactional() throws Exception {
        assertThat(JdbcWeChatPayStore.class.getMethod("providerResult",String.class,String.class,WeChatPayCoordinator.State.class,String.class).getAnnotation(Transactional.class)).isNotNull();
        assertThat(JdbcWeChatPayStore.class.getMethod("observation",String.class,WeChatPayCoordinator.State.class,String.class).getAnnotation(Transactional.class)).isNotNull();
    }

    @Test void unknownStateAndRecoveryScheduleRollBackTogether(){
        RecoveryTaskStore recovery=mock(RecoveryTaskStore.class);
        doThrow(new IllegalStateException("INJECTED_RECOVERY_FAILURE")).when(recovery).schedule(anyString(),anyString(),anyLong(),anyInt(),any(),any());
        JdbcWeChatPayStore store=new JdbcWeChatPayStore(jdbc,new ProbeEvents(jdbc,false),recovery);
        insert("PAY-UNKNOWN","e".repeat(64));

        assertThatThrownBy(()->tx.executeWithoutResult(ignored->store.observation("PAY-UNKNOWN",WeChatPayCoordinator.State.UNKNOWN,"UNKNOWN-EVIDENCE")))
                .hasMessage("INJECTED_RECOVERY_FAILURE");

        assertThat(store.require("PAY-UNKNOWN").state()).isEqualTo(WeChatPayCoordinator.State.PREPAY_CREATED);
        verify(recovery).schedule(eq("PAY-UNKNOWN"),eq("WECHAT_PAYMENT_QUERY"),eq(2L),eq(2),any(),any());
    }

    @Test void unknownRefundAndRecoveryScheduleRollBackTogether(){
        RecoveryTaskStore recovery=mock(RecoveryTaskStore.class);
        doThrow(new IllegalStateException("INJECTED_REFUND_RECOVERY_FAILURE")).when(recovery).schedule(anyString(),anyString(),anyLong(),anyInt(),any(),any());
        JdbcWeChatPayStore store=new JdbcWeChatPayStore(jdbc,new ProbeEvents(jdbc,false),recovery);
        Instant now=Instant.now();
        jdbc.update("INSERT INTO hz_payment_coordination VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,NULL,NULL,NULL,NULL,NULL)","PAY-REFUND-UNKNOWN","f".repeat(64),"BUYER","QUOTE","d".repeat(64),1000,"CNY","WX","PAID",null,2,Timestamp.from(now.plusSeconds(60)),0,1,Timestamp.from(now));
        WeChatPayCoordinator.Begin begun=tx.execute(ignored->store.beginRefund("PAY-REFUND-UNKNOWN","REFUND-UNKNOWN","a".repeat(64),400,2,now.plusSeconds(60)));
        assertThat(begun).isEqualTo(WeChatPayCoordinator.Begin.CREATED);

        assertThatThrownBy(()->tx.executeWithoutResult(ignored->store.refundResult("PAY-REFUND-UNKNOWN","REFUND-UNKNOWN",new WeChatPayPort.Unknown("PROVIDER_TIMEOUT"))))
                .hasMessage("INJECTED_REFUND_RECOVERY_FAILURE");

        assertThat(store.requireRefund("REFUND-UNKNOWN").state()).isEqualTo(WeChatPayCoordinator.RefundState.PENDING);
        assertThat(store.require("PAY-REFUND-UNKNOWN").state()).isEqualTo(WeChatPayCoordinator.State.REFUND_PROCESSING);
        verify(recovery).schedule(eq("REFUND-UNKNOWN"),eq("WECHAT_REFUND_QUERY"),eq(2L),eq(2),any(),any());
    }

    private void insert(String ref,String digest){
        jdbc.update("INSERT INTO hz_payment_coordination VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,NULL,NULL,NULL,NULL,NULL)",ref,digest,"BUYER","QUOTE","d".repeat(64),1000,"CNY",null,"PREPAY_CREATED",null,2, Timestamp.from(Instant.now().plusSeconds(60)),0,1,Timestamp.from(Instant.now()));
    }

    private record ProbeEvents(JdbcTemplate jdbc,boolean fail) implements BusinessEventStore {
        @Override public void append(String orderRef,BusinessEventLinker.Type type,String eventRef,String digest,Instant occurredAt){jdbc.update("INSERT INTO event_probe VALUES (?,?,?,?)",eventRef,orderRef,type.name(),digest);if(fail)throw new IllegalStateException("INJECTED_EVENT_FAILURE");}
        @Override public void bindSupportCase(String caseRef,String orderRef){}
        @Override public int dispatchBatch(String owner,int limit,Instant now,Duration lease){return 0;}
    }
}
