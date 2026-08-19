package com.huarenzaimeng.api.topup;

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

class JdbcTopupStoreOutboxTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;

    @BeforeEach void setUp(){
        JdbcDataSource ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:topup-outbox-"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);
        tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE hz_topup_coordination(merchant_order_ref VARCHAR(64) PRIMARY KEY,request_ref VARCHAR(64),request_digest VARCHAR(64),buyer_subject_ref VARCHAR(128),provider_sku VARCHAR(128),recipient VARCHAR(32),entitlement_digest VARCHAR(64),provider_ref VARCHAR(128),state_code VARCHAR(32),reserved_minor BIGINT,reserve_currency VARCHAR(3),unknown_queries_remaining INT,evidence_ref VARCHAR(128),aggregate_version BIGINT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_provider_balance_reservation(reservation_ref VARCHAR(64) PRIMARY KEY,merchant_order_ref VARCHAR(64),provider_code VARCHAR(32),amount_minor BIGINT,currency VARCHAR(3),reservation_state VARCHAR(32),aggregate_version BIGINT DEFAULT 1,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_provider_balance_position(provider_code VARCHAR(32),currency VARCHAR(3),confirmed_balance_minor BIGINT,safety_buffer_minor BIGINT,active_reserved_minor BIGINT,observed_at TIMESTAMP,expires_at TIMESTAMP,evidence_ref VARCHAR(128),updated_at TIMESTAMP,PRIMARY KEY(provider_code,currency))");
        jdbc.update("INSERT INTO hz_provider_balance_position VALUES('WINLA','BDT',100000,0,0,CURRENT_TIMESTAMP,DATEADD('MINUTE',15,CURRENT_TIMESTAMP),'E',CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_provider_balance_ledger(entry_ref VARCHAR(64) PRIMARY KEY,merchant_order_ref VARCHAR(64),entry_type VARCHAR(32),amount_minor BIGINT,currency VARCHAR(3),occurred_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE event_probe(event_ref VARCHAR(64) PRIMARY KEY,order_ref VARCHAR(64),event_type VARCHAR(40),event_digest VARCHAR(64))");
    }

    @Test void synchronousSubmissionAndQueryObservationReleaseAndEmitExactlyOnce(){
        JdbcTopupStore store=new JdbcTopupStore(jdbc,new ProbeEvents(jdbc,false));
        insert("TOPUP-SYNC","a".repeat(64));insert("TOPUP-QUERY","b".repeat(64));

        tx.executeWithoutResult(ignored->store.submissionResult("TOPUP-SYNC","WINLA-1",TopupCoordinator.State.DELIVERED,"SYNC-EVIDENCE"));
        tx.executeWithoutResult(ignored->store.submissionResult("TOPUP-SYNC","WINLA-1",TopupCoordinator.State.DELIVERED,"SYNC-EVIDENCE"));
        tx.executeWithoutResult(ignored->store.observation("TOPUP-QUERY",TopupCoordinator.State.DELIVERED,"QUERY-EVIDENCE"));
        tx.executeWithoutResult(ignored->store.observation("TOPUP-QUERY",TopupCoordinator.State.DELIVERED,"QUERY-EVIDENCE"));

        assertThat(store.require("TOPUP-SYNC").state()).isEqualTo(TopupCoordinator.State.DELIVERED);
        assertThat(store.require("TOPUP-QUERY").state()).isEqualTo(TopupCoordinator.State.DELIVERED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_provider_balance_reservation WHERE reservation_state='CONSUMED'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_provider_balance_ledger WHERE entry_type='CONSUMED'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_probe WHERE event_type='TOPUP_DELIVERED'",Integer.class)).isEqualTo(2);
    }

    @Test void topupDeliveredEventFailureRollsBackStateReleaseLedgerAndEvent(){
        JdbcTopupStore store=new JdbcTopupStore(jdbc,new ProbeEvents(jdbc,true));
        insert("TOPUP-ROLLBACK","c".repeat(64));

        assertThatThrownBy(()->tx.executeWithoutResult(ignored->store.observation("TOPUP-ROLLBACK",TopupCoordinator.State.DELIVERED,"QUERY-EVIDENCE")))
                .hasMessage("INJECTED_EVENT_FAILURE");

        assertThat(store.require("TOPUP-ROLLBACK").state()).isEqualTo(TopupCoordinator.State.SUBMITTED);
        assertThat(jdbc.queryForObject("SELECT reservation_state FROM hz_provider_balance_reservation WHERE merchant_order_ref='TOPUP-ROLLBACK'",String.class)).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_provider_balance_ledger",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_probe",Integer.class)).isZero();
    }

    @Test void deliveredProducingStoreMethodsAreTransactional() throws Exception {
        assertThat(JdbcTopupStore.class.getMethod("submissionResult",String.class,String.class,TopupCoordinator.State.class,String.class).getAnnotation(Transactional.class)).isNotNull();
        assertThat(JdbcTopupStore.class.getMethod("observation",String.class,TopupCoordinator.State.class,String.class).getAnnotation(Transactional.class)).isNotNull();
    }

    @Test void unknownStateAndRecoveryScheduleRollBackTogether(){
        RecoveryTaskStore recovery=mock(RecoveryTaskStore.class);
        doThrow(new IllegalStateException("INJECTED_RECOVERY_FAILURE")).when(recovery).schedule(anyString(),anyString(),anyLong(),anyInt(),any(),any());
        JdbcTopupStore store=new JdbcTopupStore(jdbc,new ProbeEvents(jdbc,false),recovery);
        insert("TOPUP-UNKNOWN","f".repeat(64));

        assertThatThrownBy(()->tx.executeWithoutResult(ignored->store.observation("TOPUP-UNKNOWN",TopupCoordinator.State.UNKNOWN,"UNKNOWN-EVIDENCE")))
                .hasMessage("INJECTED_RECOVERY_FAILURE");

        assertThat(store.require("TOPUP-UNKNOWN").state()).isEqualTo(TopupCoordinator.State.SUBMITTED);
        verify(recovery).schedule(eq("TOPUP-UNKNOWN"),eq("WINLA_TOPUP_QUERY"),eq(2L),eq(2),any(),any());
    }

    private void insert(String ref,String digest){
        Instant now=Instant.now();
        jdbc.update("INSERT INTO hz_topup_coordination VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",ref,"REQ-"+ref,digest,"BUYER","SKU","8801712345678","d".repeat(64),null,"SUBMITTED",1000,"BDT",2,null,1,Timestamp.from(now));
        jdbc.update("INSERT INTO hz_provider_balance_reservation(reservation_ref,merchant_order_ref,provider_code,amount_minor,currency,reservation_state,aggregate_version,created_at,updated_at) VALUES (?,?, 'WINLA',?,?,'RESERVED',1,?,?)","RES-"+ref,ref,1000,"BDT",Timestamp.from(now),Timestamp.from(now));
        jdbc.update("UPDATE hz_provider_balance_position SET active_reserved_minor=active_reserved_minor+1000");
    }

    private record ProbeEvents(JdbcTemplate jdbc,boolean fail) implements BusinessEventStore {
        @Override public void append(String orderRef,BusinessEventLinker.Type type,String eventRef,String digest,Instant occurredAt){jdbc.update("INSERT INTO event_probe VALUES (?,?,?,?)",eventRef,orderRef,type.name(),digest);if(fail)throw new IllegalStateException("INJECTED_EVENT_FAILURE");}
        @Override public void bindSupportCase(String caseRef,String orderRef){}
        @Override public int dispatchBatch(String owner,int limit,Instant now,Duration lease){return 0;}
    }
}
