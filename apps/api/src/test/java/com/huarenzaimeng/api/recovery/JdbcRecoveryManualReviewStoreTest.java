package com.huarenzaimeng.api.recovery;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRecoveryManualReviewStoreTest {
    @Test void paymentRefundAndTopupDeadTasksOpenStableA110CasesWithoutDuplicates(){
        JdbcDataSource ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:manual-review-"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE hz_payment_coordination(merchant_order_ref VARCHAR(64) PRIMARY KEY,amount_minor BIGINT,currency VARCHAR(3))");
        jdbc.execute("CREATE TABLE hz_payment_refund(refund_ref VARCHAR(64) PRIMARY KEY,merchant_order_ref VARCHAR(64),amount_minor BIGINT)");
        jdbc.execute("CREATE TABLE hz_topup_coordination(merchant_order_ref VARCHAR(64) PRIMARY KEY,reserved_minor BIGINT,reserve_currency VARCHAR(3))");
        jdbc.execute("CREATE TABLE hz_reconciliation_case(reconciliation_ref VARCHAR(64) PRIMARY KEY,order_ref VARCHAR(64),difference_type VARCHAR(48),amount DECIMAL(19,2),currency VARCHAR(3),case_state VARCHAR(24),owner_ref VARCHAR(96),data_origin VARCHAR(32),aggregate_version BIGINT,discovered_at TIMESTAMP,updated_at TIMESTAMP,CONSTRAINT uk_case UNIQUE(order_ref,difference_type))");
        jdbc.update("INSERT INTO hz_payment_coordination VALUES('ORDER-PAY',1234,'CNY')");
        jdbc.update("INSERT INTO hz_payment_coordination VALUES('ORDER-REF',5000,'CNY')");
        jdbc.update("INSERT INTO hz_payment_refund VALUES('REF-1','ORDER-REF',600)");
        jdbc.update("INSERT INTO hz_topup_coordination VALUES('ORDER-TOP',7000,'BDT')");
        JdbcRecoveryManualReviewStore store=new JdbcRecoveryManualReviewStore(jdbc);
        assertThat(store.open(task("ORDER-PAY","WECHAT_PAYMENT_QUERY"),"EXHAUSTED")).isEqualTo("RCN-TASK-WECHAT_PAYMENT_QUERY");
        assertThat(store.open(task("REF-1","WECHAT_REFUND_QUERY"),"EXHAUSTED")).isEqualTo("RCN-TASK-WECHAT_REFUND_QUERY");
        assertThat(store.open(task("ORDER-TOP","WINLA_TOPUP_QUERY"),"EXHAUSTED")).isEqualTo("RCN-TASK-WINLA_TOPUP_QUERY");
        store.open(task("ORDER-PAY","WECHAT_PAYMENT_QUERY"),"EXHAUSTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_reconciliation_case",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT amount FROM hz_reconciliation_case WHERE order_ref='ORDER-PAY'",java.math.BigDecimal.class)).isEqualByComparingTo("12.34");
        assertThat(jdbc.queryForObject("SELECT order_ref FROM hz_reconciliation_case WHERE difference_type='WECHAT_REFUND_QUERY'",String.class)).isEqualTo("ORDER-REF");
    }

    private static RecoveryTaskStore.Task task(String aggregate,String operation){
        Instant now=Instant.parse("2026-08-18T01:00:00Z");
        return new RecoveryTaskStore.Task("TASK-"+operation,aggregate,operation,1,RecoveryTaskStore.State.LEASED,0,
                now,now,"worker-1",now,2,null,null);
    }
}
