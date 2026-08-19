package com.huarenzaimeng.api.topup;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@Profile("release-mysql")
class OrderRecipientRetentionStore {
    private static final int BATCH_SIZE = 100;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    OrderRecipientRetentionStore(JdbcTemplate jdbc) { this(jdbc, Clock.systemUTC()); }
    OrderRecipientRetentionStore(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }

    @Transactional
    int runOnce() {
        Instant now=clock.instant();
        Timestamp nowTs=Timestamp.from(now);
        List<TerminalOrder> newlyTerminal=jdbc.query("SELECT r.order_ref,o.updated_at FROM hz_order_recipient_fulfillment r JOIN hz_order o ON o.order_ref=r.order_ref WHERE r.retention_state='ACTIVE' AND r.terminal_at IS NULL AND (o.delivery_state='DELIVERED' OR o.refund_state='REFUNDED' OR o.order_state IN ('CANCELLED','CLOSED')) ORDER BY r.order_ref LIMIT "+BATCH_SIZE,
                (rs,n)->new TerminalOrder(rs.getString(1),rs.getTimestamp(2).toInstant()));
        for(TerminalOrder order:newlyTerminal) {
            Timestamp terminalTs=Timestamp.from(order.terminalAt());
            Timestamp retentionTs=Timestamp.from(order.terminalAt().atZone(ZoneOffset.UTC).plusYears(3).toInstant());
            jdbc.update("UPDATE hz_order_recipient_fulfillment SET terminal_at=?,retention_until=?,updated_at=? WHERE order_ref=? AND retention_state='ACTIVE' AND terminal_at IS NULL",terminalTs,retentionTs,nowTs,order.orderRef());
        }
        List<String> expired=jdbc.queryForList("SELECT order_ref FROM hz_order_recipient_fulfillment WHERE retention_state='ACTIVE' AND retention_until IS NOT NULL AND retention_until<=? ORDER BY order_ref LIMIT "+BATCH_SIZE,String.class,nowTs);
        int anonymized=0;
        for(String orderRef:expired) anonymized+=jdbc.update("UPDATE hz_order_recipient_fulfillment SET recipient_plain=NULL,retention_state='ANONYMIZED',updated_at=? WHERE order_ref=? AND retention_state='ACTIVE' AND retention_until<=?",nowTs,orderRef,nowTs);
        jdbc.update("DELETE FROM hz_quote_recipient_pending WHERE expires_at<=?",nowTs);
        return anonymized;
    }

    private record TerminalOrder(String orderRef, Instant terminalAt) {}
}
