package com.huarenzaimeng.api.buyerauth;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Repository
@Profile("release-mysql")
class BuyerPiiCleanupTaskStore {
    private final JdbcTemplate jdbc;
    BuyerPiiCleanupTaskStore(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    Optional<Claim> claim(String owner,Instant now,Duration leaseDuration){
        var rows=jdbc.query("SELECT t.cleanup_ref,t.closure_ref,c.subject_ref,c.aggregate_version,t.aggregate_version FROM buyer_pii_cleanup_task t JOIN buyer_account_closure_request c ON c.closure_ref=t.closure_ref WHERE c.closure_state='REQUESTED' AND t.next_attempt_at<=? AND (t.task_state='READY' OR (t.task_state='LEASED' AND t.lease_until<=?)) ORDER BY t.next_attempt_at,t.cleanup_ref LIMIT 1 FOR UPDATE",
                (rs,n)->new Claim(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),owner,rs.getLong(5)+1),
                ts(now),ts(now));
        if(rows.isEmpty())return Optional.empty();
        Claim claim=rows.get(0);Instant until=now.plus(leaseDuration);
        int changed=jdbc.update("UPDATE buyer_pii_cleanup_task SET task_state='LEASED',lease_owner=?,lease_until=?,attempt_count=attempt_count+1,last_error_code=NULL,aggregate_version=aggregate_version+1,updated_at=? WHERE cleanup_ref=? AND aggregate_version=? AND (task_state='READY' OR (task_state='LEASED' AND lease_until<=?))",
                owner,ts(until),ts(now),claim.cleanupRef(),claim.taskVersion()-1,ts(now));
        return changed==1?Optional.of(claim):Optional.empty();
    }

    @Transactional
    void retry(Claim claim,String errorCode,Instant now,Duration retryDelay){
        jdbc.update("UPDATE buyer_pii_cleanup_task SET task_state='READY',next_attempt_at=?,lease_owner=NULL,lease_until=NULL,last_error_code=?,aggregate_version=aggregate_version+1,updated_at=? WHERE cleanup_ref=? AND task_state='LEASED' AND lease_owner=? AND aggregate_version=?",
                ts(now.plus(retryDelay)),safe(errorCode),ts(now),claim.cleanupRef(),claim.leaseOwner(),claim.taskVersion());
    }

    private static String safe(String error){
        if(error==null||error.isBlank())return "PII_CLEANUP_UNKNOWN";
        String normalized=error.replaceAll("[^A-Za-z0-9._:-]","_");
        return normalized.substring(0,Math.min(128,normalized.length()));
    }
    private static Timestamp ts(Instant value){return Timestamp.from(value);}
    record Claim(String cleanupRef,String closureRef,String subjectRef,long closureVersion,String leaseOwner,long taskVersion){}
}
