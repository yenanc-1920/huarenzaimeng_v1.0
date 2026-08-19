package com.huarenzaimeng.api;

import com.huarenzaimeng.core.BusinessEventLinker;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class BusinessEventTransactionalOutboxTest {
    private JdbcTemplate jdbc;
    private JdbcBusinessEventStore store;
    private TransactionTemplate tx;
    private final Instant occurredAt=Instant.parse("2026-08-18T01:02:03.123Z");

    @BeforeEach void setUp(){
        JdbcDataSource ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:outbox"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);store=new JdbcBusinessEventStore(jdbc);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE hz_order(order_ref VARCHAR(64) PRIMARY KEY,project_subject_ref VARCHAR(128))");
        jdbc.execute("CREATE TABLE hz_business_event_outbox(outbox_ref VARCHAR(64) PRIMARY KEY,event_ref VARCHAR(64) UNIQUE NOT NULL,merchant_order_ref VARCHAR(64) NOT NULL,event_type VARCHAR(40) NOT NULL,event_digest CHAR(64) NOT NULL,occurred_at TIMESTAMP(3) NOT NULL,created_at TIMESTAMP(3) NOT NULL,lease_owner VARCHAR(64),lease_until TIMESTAMP(3),delivery_attempts INT DEFAULT 0 NOT NULL,dispatched_at TIMESTAMP(3),last_error_code VARCHAR(64))");
        jdbc.execute("CREATE TABLE hz_business_event_link(event_ref VARCHAR(64) PRIMARY KEY,parent_event_ref VARCHAR(64),merchant_order_ref VARCHAR(64),event_type VARCHAR(40),parent_event_type VARCHAR(40),event_digest CHAR(64) UNIQUE,occurred_at TIMESTAMP(3),observed_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE hz_customer_case_subject_binding(case_ref VARCHAR(64) PRIMARY KEY,merchant_order_ref VARCHAR(64),order_subject_ref VARCHAR(128),created_at TIMESTAMP(3))");
        jdbc.update("INSERT INTO hz_order(order_ref,project_subject_ref) VALUES ('ORDER-1','BUYER-1')");
    }

    @Test void aggregateAndDraftRollbackTogetherOnInjectedFailure(){
        jdbc.execute("CREATE TABLE aggregate_probe(id VARCHAR(20) PRIMARY KEY,state_code VARCHAR(20))");
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            jdbc.update("INSERT INTO aggregate_probe VALUES ('A1','PAID')");
            store.append(draft());
            throw new IllegalStateException("INJECTED_AFTER_DRAFT");
        })).hasMessage("INJECTED_AFTER_DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aggregate_probe",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_business_event_outbox",Integer.class)).isZero();
    }

    @Test void committedDraftSurvivesCrashWindowAndLaterDispatchesExactlyOnce(){
        tx.executeWithoutResult(status->store.append(draft()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_business_event_link",Integer.class)).isZero();
        assertThat(store.dispatchBatch("dispatcher-A",10,occurredAt.plusSeconds(1),Duration.ofSeconds(30))).isOne();
        assertThat(store.dispatchBatch("dispatcher-B",10,occurredAt.plusSeconds(2),Duration.ofSeconds(30))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_business_event_link",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT delivery_attempts FROM hz_business_event_outbox",Integer.class)).isOne();
    }

    @Test void activeLeaseExcludesSecondDispatcherAndExpiredLeaseIsRecoverable(){
        store.append(draft());
        jdbc.update("UPDATE hz_business_event_outbox SET lease_owner='dispatcher-A',lease_until=?",java.sql.Timestamp.from(occurredAt.plusSeconds(30)));
        assertThat(store.dispatchBatch("dispatcher-B",10,occurredAt.plusSeconds(1),Duration.ofSeconds(30))).isZero();
        assertThat(store.dispatchBatch("dispatcher-B",10,occurredAt.plusSeconds(31),Duration.ofSeconds(30))).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_business_event_link",Integer.class)).isOne();
    }

    @Test void duplicatePaymentNotificationDraftProducesOneEvent(){
        store.append(draft());
        store.dispatchBatch("dispatcher-A",10,occurredAt.plusSeconds(1),Duration.ofSeconds(30));
        BusinessEventStore.EventDraft notification=new BusinessEventStore.EventDraft("ORDER-1",BusinessEventLinker.Type.PAYMENT_CONFIRMED,
                "PAY-NOTICE-N1","b".repeat(64),occurredAt.plusSeconds(2));
        store.append(notification);store.append(notification);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_business_event_outbox",Integer.class)).isEqualTo(2);
        assertThat(store.dispatchBatch("dispatcher-B",10,occurredAt.plusSeconds(3),Duration.ofSeconds(30))).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_business_event_link",Integer.class)).isEqualTo(2);
    }

    @Test void v18CarriesLeaseAndNoSensitivePayloadColumns() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V18__add_business_event_transactional_outbox.sql"));
        assertThat(sql).contains("lease_owner","lease_until","delivery_attempts","dispatched_at","UNIQUE KEY uk_hz_business_event_outbox_event");
        assertThat(sql.toLowerCase()).doesNotContain("buyer_subject","recipient","phone","request_body","payload_json","secret","password");
    }

    private BusinessEventStore.EventDraft draft(){return new BusinessEventStore.EventDraft("ORDER-1", BusinessEventLinker.Type.ORDER_CREATED,"EVT-ROOT-1","a".repeat(64),occurredAt);}
}
