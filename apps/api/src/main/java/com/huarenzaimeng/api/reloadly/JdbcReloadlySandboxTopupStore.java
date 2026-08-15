package com.huarenzaimeng.api.reloadly;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

final class JdbcReloadlySandboxTopupStore implements ReloadlySandboxTopupStore {
    private static final String SUBJECT = "SYSTEM:RELOADLY_SANDBOX";
    private static final String ENDPOINT = "POST:/admin-read/v1/reloadly-sandbox/topups";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    JdbcReloadlySandboxTopupStore(JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc; this.transactions = transactions; this.clock = clock;
    }

    @Override public Begin begin(String requestRef, String fingerprint) {
        try {
            Boolean inserted = transactions.execute(status -> {
                List<Map<String,Object>> rows = jdbc.queryForList("""
                        SELECT canonical_fingerprint FROM hz_command
                        WHERE project_subject_ref=? AND command_id=? FOR UPDATE
                        """, SUBJECT, commandId(requestRef));
                if (!rows.isEmpty()) {
                    if (rows.size() != 1 || !fingerprint.equals(rows.get(0).get("canonical_fingerprint")))
                        throw new IllegalStateException("RLD_TOPUP_IDEMPOTENCY_CONFLICT");
                    return false;
                }
                jdbc.update("""
                        INSERT INTO hz_command
                          (project_subject_ref,command_id,idempotency_key,endpoint_scope,resource_scope,
                           semantic_action_key,canonical_fingerprint,resource_ref,command_state,created_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?)
                        """, SUBJECT, commandId(requestRef), requestRef, ENDPOINT, "RELOADLY_SANDBOX",
                        "RELOADLY_TOPUP:" + requestRef, fingerprint, requestRef, "SUBMITTED",
                        Timestamp.from(clock.instant()));
                return true;
            });
            return Boolean.TRUE.equals(inserted) ? Begin.NEW : Begin.EXISTING;
        } catch (DuplicateKeyException race) {
            Record record = require(requestRef);
            if (record == null) throw new IllegalStateException("RLD_TOPUP_CONCURRENT_RESULT_UNKNOWN");
            return Begin.EXISTING;
        }
    }

    @Override public void created(String requestRef, long transactionId, BigDecimal amount, String currency) {
        transactions.executeWithoutResult(status -> {
            int updated = jdbc.update("UPDATE hz_command SET command_state='ACCEPTED' WHERE project_subject_ref=? AND command_id=? AND command_state='SUBMITTED'",
                    SUBJECT, commandId(requestRef));
            if (updated != 1) throw new IllegalStateException("RLD_TOPUP_COMMAND_STATE_INVALID");
            jdbc.update("""
                    INSERT INTO hz_external_fact
                      (external_fact_id,case_key,fact_type,provider,provider_fact_ref,amount,currency,evidence_ref,observed_at,created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, "RLD:" + transactionId, requestRef, "TOPUP_ACCEPTED", "RELOADLY_SANDBOX",
                    "TOPUP:" + transactionId, amount, currency, "RLD-TOPUP:" + transactionId,
                    Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        });
    }

    @Override public void rejected(String requestRef) { transition(requestRef, "REJECTED"); }
    @Override public void unknown(String requestRef) { transition(requestRef, "UNKNOWN"); }

    private void transition(String requestRef, String target) {
        int updated = jdbc.update("UPDATE hz_command SET command_state=? WHERE project_subject_ref=? AND command_id=? AND command_state='SUBMITTED'",
                target, SUBJECT, commandId(requestRef));
        if (updated != 1) throw new IllegalStateException("RLD_TOPUP_COMMAND_STATE_INVALID");
    }

    @Override public Record require(String requestRef) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT c.resource_ref,c.command_state,f.provider_fact_ref,f.amount,f.currency,
                       (SELECT sf.fact_type FROM hz_external_fact sf
                        WHERE sf.case_key=c.resource_ref AND sf.provider='RELOADLY_SANDBOX'
                          AND sf.fact_type LIKE 'TOPUP_STATUS_%'
                        ORDER BY sf.observed_at DESC,sf.external_fact_id DESC LIMIT 1) AS last_status
                FROM hz_command c
                LEFT JOIN hz_external_fact f ON f.case_key=c.resource_ref
                  AND f.provider='RELOADLY_SANDBOX' AND f.fact_type='TOPUP_ACCEPTED'
                WHERE c.project_subject_ref=? AND c.command_id=?
                """, SUBJECT, commandId(requestRef));
        if (rows.size() != 1) throw new IllegalStateException("RLD_TOPUP_NOT_FOUND");
        Map<String,Object> row = rows.get(0);
        String providerRef = (String) row.get("provider_fact_ref");
        Long transactionId = providerRef == null ? null : Long.parseLong(providerRef.substring("TOPUP:".length()));
        String factType = (String) row.get("last_status");
        String lastStatus = factType == null ? null : factType.substring("TOPUP_STATUS_".length());
        return new Record((String) row.get("resource_ref"), (String) row.get("command_state"), transactionId,
                (BigDecimal) row.get("amount"), (String) row.get("currency"), lastStatus);
    }

    @Override public void observed(String requestRef, long transactionId, String statusCode) {
        try {
            jdbc.update("""
                    INSERT INTO hz_external_fact
                      (external_fact_id,case_key,fact_type,provider,provider_fact_ref,evidence_ref,observed_at,created_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, "RLD-STATUS:" + transactionId + ":" + statusCode, requestRef,
                    "TOPUP_STATUS_" + statusCode, "RELOADLY_SANDBOX", "STATUS:" + transactionId + ":" + statusCode,
                    "RLD-STATUS:" + transactionId, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        } catch (DuplicateKeyException sameObservation) {
            // The same provider status is an exact idempotent observation.
        }
    }

    private static String commandId(String requestRef) { return "RLD-CREATE:" + requestRef; }
}

