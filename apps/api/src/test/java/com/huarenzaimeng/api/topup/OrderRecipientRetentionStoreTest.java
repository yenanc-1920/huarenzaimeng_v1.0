package com.huarenzaimeng.api.topup;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRecipientRetentionStoreTest {
    private final Instant now=Instant.parse("2030-08-19T00:00:00Z");

    @Test void terminalStartsThreeYearWindowAndExpiredPlaintextIsAnonymized(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+ UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE hz_order(order_ref VARCHAR(64) PRIMARY KEY,order_state VARCHAR(32),delivery_state VARCHAR(32),refund_state VARCHAR(32),updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_order_recipient_fulfillment(order_ref VARCHAR(64) PRIMARY KEY,recipient_plain VARCHAR(32),recipient_digest CHAR(64),recipient_masked VARCHAR(32),retention_state VARCHAR(24),terminal_at TIMESTAMP,retention_until TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_quote_recipient_pending(quote_ref VARCHAR(64) PRIMARY KEY,recipient_plain VARCHAR(32),recipient_digest CHAR(64),recipient_masked VARCHAR(32),expires_at TIMESTAMP,created_at TIMESTAMP)");
        Instant terminalAt=now.minusSeconds(60);
        jdbc.update("INSERT INTO hz_order VALUES('O-1','CREATED','DELIVERED','NOT_REQUESTED',?)",Timestamp.from(terminalAt));
        jdbc.update("INSERT INTO hz_order_recipient_fulfillment VALUES('O-1','+8801712345678',REPEAT('a',64),'+88017****678','ACTIVE',NULL,NULL,?,?)",Timestamp.from(now.minusSeconds(60)),Timestamp.from(now.minusSeconds(60)));
        var store=new OrderRecipientRetentionStore(jdbc, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(store.runOnce()).isZero();
        assertThat(jdbc.queryForObject("SELECT recipient_plain FROM hz_order_recipient_fulfillment",String.class)).isEqualTo("+8801712345678");
        Timestamp retention=jdbc.queryForObject("SELECT retention_until FROM hz_order_recipient_fulfillment",Timestamp.class);
        assertThat(jdbc.queryForObject("SELECT terminal_at FROM hz_order_recipient_fulfillment",Timestamp.class).toInstant()).isEqualTo(terminalAt);
        assertThat(retention.toInstant()).isEqualTo(terminalAt.atZone(ZoneOffset.UTC).plusYears(3).toInstant());
        jdbc.update("UPDATE hz_order_recipient_fulfillment SET retention_until=?",Timestamp.from(now.minusSeconds(1)));
        assertThat(store.runOnce()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recipient_plain FROM hz_order_recipient_fulfillment",String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT retention_state FROM hz_order_recipient_fulfillment",String.class)).isEqualTo("ANONYMIZED");
        assertThat(store.runOnce()).isZero();
    }

    @Test void nonTerminalOrderNeverStartsRetentionWindow(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+ UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE hz_order(order_ref VARCHAR(64) PRIMARY KEY,order_state VARCHAR(32),delivery_state VARCHAR(32),refund_state VARCHAR(32),updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_order_recipient_fulfillment(order_ref VARCHAR(64) PRIMARY KEY,recipient_plain VARCHAR(32),recipient_digest CHAR(64),recipient_masked VARCHAR(32),retention_state VARCHAR(24),terminal_at TIMESTAMP,retention_until TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_quote_recipient_pending(quote_ref VARCHAR(64) PRIMARY KEY,recipient_plain VARCHAR(32),recipient_digest CHAR(64),recipient_masked VARCHAR(32),expires_at TIMESTAMP,created_at TIMESTAMP)");
        jdbc.update("INSERT INTO hz_order VALUES('O-2','CREATED','PROCESSING','NOT_REQUESTED',?)",Timestamp.from(now.minusSeconds(60)));
        jdbc.update("INSERT INTO hz_order_recipient_fulfillment VALUES('O-2','+8801812345678',REPEAT('b',64),'+88018****678','ACTIVE',NULL,NULL,?,?)",Timestamp.from(now.minusSeconds(60)),Timestamp.from(now.minusSeconds(60)));
        var store=new OrderRecipientRetentionStore(jdbc, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(store.runOnce()).isZero();
        assertThat(jdbc.queryForObject("SELECT retention_until FROM hz_order_recipient_fulfillment",Timestamp.class)).isNull();
    }
}
