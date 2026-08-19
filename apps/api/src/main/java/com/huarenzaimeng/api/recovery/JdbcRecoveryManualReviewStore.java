package com.huarenzaimeng.api.recovery;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
@Profile("release-mysql")
class JdbcRecoveryManualReviewStore implements RecoveryManualReviewStore {
    private final JdbcTemplate jdbc;

    JdbcRecoveryManualReviewStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public String open(RecoveryTaskStore.Task task, String reasonCode) {
        Binding binding = binding(task);
        String reviewRef = "RCN-" + task.taskRef();
        jdbc.update("INSERT INTO hz_reconciliation_case(reconciliation_ref,order_ref,difference_type,amount,currency,case_state,owner_ref,data_origin,aggregate_version,discovered_at,updated_at) " +
                        "VALUES (?,?,?,?,?,'OPEN',NULL,'RECOVERY',1,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)) " +
                        "ON DUPLICATE KEY UPDATE updated_at=VALUES(updated_at)",
                reviewRef, binding.orderRef(), task.operationCode(), BigDecimal.valueOf(binding.amountMinor(), 2), binding.currency());
        return jdbc.queryForObject("SELECT reconciliation_ref FROM hz_reconciliation_case WHERE order_ref=? AND difference_type=?",
                String.class, binding.orderRef(), task.operationCode());
    }

    private Binding binding(RecoveryTaskStore.Task task) {
        List<Binding> rows = switch (task.operationCode()) {
            case "WECHAT_PAYMENT_QUERY" -> jdbc.query(
                    "SELECT merchant_order_ref,amount_minor,currency FROM hz_payment_coordination WHERE merchant_order_ref=?",
                    (rs, n) -> new Binding(rs.getString(1), rs.getLong(2), rs.getString(3)), task.aggregateRef());
            case "WECHAT_REFUND_QUERY" -> jdbc.query(
                    "SELECT r.merchant_order_ref,r.amount_minor,p.currency FROM hz_payment_refund r JOIN hz_payment_coordination p ON p.merchant_order_ref=r.merchant_order_ref WHERE r.refund_ref=?",
                    (rs, n) -> new Binding(rs.getString(1), rs.getLong(2), rs.getString(3)), task.aggregateRef());
            case "WINLA_TOPUP_QUERY" -> jdbc.query(
                    "SELECT merchant_order_ref,reserved_minor,reserve_currency FROM hz_topup_coordination WHERE merchant_order_ref=?",
                    (rs, n) -> new Binding(rs.getString(1), rs.getLong(2), rs.getString(3)), task.aggregateRef());
            default -> throw new IllegalStateException("RECOVERY_OPERATION_UNSUPPORTED");
        };
        if (rows.size() != 1) throw new IllegalStateException("RECOVERY_MANUAL_REVIEW_BINDING_MISSING");
        return rows.get(0);
    }

    private record Binding(String orderRef, long amountMinor, String currency) {}
}
