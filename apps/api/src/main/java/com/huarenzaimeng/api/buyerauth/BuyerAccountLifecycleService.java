package com.huarenzaimeng.api.buyerauth;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Profile("release-mysql")
class BuyerAccountLifecycleService {
    private final JdbcTemplate jdbc;
    BuyerAccountLifecycleService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    ClosureView request(String subjectRef,String key,JsonNode body){
        validKey(key);String requestRef=required(body,"requestRef"),reason=required(body,"reason");long expected=version(body);
        if(expected!=1)throw new Conflict("CLOSURE_VERSION_CONFLICT");
        String requestDigest=digest(requestRef+"\n"+reason+"\n"+expected);
        ClosureView replay=replay(key,requestDigest);if(replay!=null)return replay;
        List<Map<String,Object>> identities=jdbc.queryForList("SELECT buyer_id,status_code FROM buyer_identity WHERE subject_ref=? FOR UPDATE",subjectRef);
        if(identities.size()!=1)throw new Conflict("BUYER_ACCOUNT_NOT_FOUND");
        String buyerId=String.valueOf(identities.get(0).get("buyer_id"));String status=String.valueOf(identities.get(0).get("status_code"));
        if(!"ACTIVE".equals(status))throw new Conflict("BUYER_ACCOUNT_NOT_ACTIVE");
        String closureRef="CLOSURE-"+UUID.randomUUID();Instant now=Instant.now();int blockers=blockers(subjectRef);
        try{jdbc.update("INSERT INTO buyer_account_closure_request(closure_ref,buyer_id,subject_ref,idempotency_key,request_ref,request_digest,reason,closure_state,blocker_count,aggregate_version,requested_at,closed_at,updated_at) VALUES(?,?,?,?,?,?,?,'REQUESTED',?,1,?,NULL,?)",
                closureRef,buyerId,subjectRef,key,requestRef,requestDigest,reason,blockers,ts(now),ts(now));}
        catch(DuplicateKeyException duplicate){ClosureView prior=replay(key,requestDigest);if(prior!=null)return prior;throw new Conflict("CLOSURE_ALREADY_REQUESTED");}
        jdbc.update("UPDATE buyer_identity SET status_code='CLOSURE_REQUESTED',updated_at=? WHERE buyer_id=? AND status_code='ACTIVE'",ts(now),buyerId);
        jdbc.update("UPDATE buyer_consent_state SET consent_state='CLOSURE_REQUESTED',aggregate_version=aggregate_version+1,updated_at=? WHERE buyer_id=?",ts(now),buyerId);
        jdbc.update("UPDATE buyer_session SET status_code='REVOKED',revoked_at=?,aggregate_version=aggregate_version+1 WHERE buyer_id=? AND status_code='ACTIVE' AND revoked_at IS NULL",ts(now),buyerId);
        jdbc.update("INSERT INTO buyer_pii_cleanup_task(cleanup_ref,closure_ref,buyer_id,task_state,idempotency_key,attempt_count,next_attempt_at,aggregate_version,created_at,completed_at,updated_at) VALUES(?,?,?,'READY',?,0,?,1,?,NULL,?)",
                "CLEANUP-"+UUID.randomUUID(),closureRef,buyerId,"PII-"+closureRef,ts(now),ts(now),ts(now));
        return new ClosureView("BUYER_CLOSURE_V1",closureRef,"REQUESTED",blockers,1,requestRef,false);
    }

    @Transactional
    ClosureView complete(String subjectRef,String closureRef,String key,JsonNode body){
        validKey(key);long expected=version(body);String reason=required(body,"reason");
        return completeInternal(subjectRef,closureRef,expected,reason,null,null);
    }

    @Transactional
    ClosureView completeClaimed(BuyerPiiCleanupTaskStore.Claim claim){
        return completeInternal(claim.subjectRef(),claim.closureRef(),claim.closureVersion(),
                "controlled pii cleanup worker",claim.leaseOwner(),claim.taskVersion());
    }

    private ClosureView completeInternal(String subjectRef,String closureRef,long expected,String reason,
                                         String leaseOwner,Long taskVersion){
        if(expected<1)throw new Conflict("CLOSURE_VERSION_CONFLICT");Instant now=Instant.now();
        if(leaseOwner!=null){
            Integer lease=jdbc.queryForObject("SELECT COUNT(*) FROM buyer_pii_cleanup_task WHERE closure_ref=? AND task_state='LEASED' AND lease_owner=? AND aggregate_version=? AND lease_until>? FOR UPDATE",
                    Integer.class,closureRef,leaseOwner,taskVersion,ts(now));
            if(lease==null||lease!=1)throw new Conflict("PII_CLEANUP_LEASE_LOST");
        }
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT buyer_id,request_ref,closure_state,aggregate_version FROM buyer_account_closure_request WHERE closure_ref=? AND subject_ref=? FOR UPDATE",closureRef,subjectRef);
        if(rows.size()!=1)throw new Conflict("CLOSURE_NOT_FOUND");Map<String,Object> row=rows.get(0);long current=((Number)row.get("aggregate_version")).longValue();
        if("CLOSED".equals(row.get("closure_state")))return new ClosureView("BUYER_CLOSURE_V1",closureRef,"CLOSED",0,current,String.valueOf(row.get("request_ref")),true);
        if(current!=expected)throw new Conflict("CLOSURE_VERSION_CONFLICT");int blockers=blockers(subjectRef);
        if(blockers>0){jdbc.update("UPDATE buyer_account_closure_request SET blocker_count=?,updated_at=? WHERE closure_ref=?",blockers,ts(now),closureRef);throw new Conflict("CLOSURE_UNFINISHED_TRANSACTIONS");}
        String buyerId=String.valueOf(row.get("buyer_id"));
        jdbc.update("INSERT IGNORE INTO buyer_pii_cleanup_task(cleanup_ref,closure_ref,buyer_id,task_state,idempotency_key,attempt_count,next_attempt_at,aggregate_version,created_at,completed_at,updated_at) VALUES(?,?,?,'READY',?,0,?,1,?,NULL,?)",
                "CLEANUP-"+UUID.randomUUID(),closureRef,buyerId,"PII-"+closureRef,ts(now),ts(now),ts(now));
        String tombstone="CLOSED-"+closureRef;
        jdbc.update("DELETE FROM buyer_session WHERE buyer_id=?",buyerId);
        jdbc.update("DELETE FROM buyer_wechat_payment_identity WHERE buyer_id=?",buyerId);
        jdbc.update("UPDATE buyer_consent_state SET guest_ref=?,consent_state='CLOSURE_REQUESTED',aggregate_version=aggregate_version+1,updated_at=? WHERE buyer_id=?",tombstone,ts(now),buyerId);
        jdbc.update("UPDATE buyer_consent_acceptance SET guest_ref=? WHERE buyer_id=?",tombstone,buyerId);
        int task=leaseOwner==null
                ?jdbc.update("UPDATE buyer_pii_cleanup_task SET task_state='SUCCEEDED',completed_at=?,updated_at=?,aggregate_version=aggregate_version+1 WHERE closure_ref=? AND buyer_id=? AND task_state='READY'",ts(now),ts(now),closureRef,buyerId)
                :jdbc.update("UPDATE buyer_pii_cleanup_task SET task_state='SUCCEEDED',completed_at=?,updated_at=?,lease_owner=NULL,lease_until=NULL,last_error_code=NULL,aggregate_version=aggregate_version+1 WHERE closure_ref=? AND buyer_id=? AND task_state='LEASED' AND lease_owner=? AND aggregate_version=?",ts(now),ts(now),closureRef,buyerId,leaseOwner,taskVersion);
        if(task!=1){Integer done=jdbc.queryForObject("SELECT COUNT(*) FROM buyer_pii_cleanup_task WHERE closure_ref=? AND buyer_id=? AND task_state='SUCCEEDED'",Integer.class,closureRef,buyerId);if(done==null||done!=1)throw new Conflict("PII_CLEANUP_NOT_READY");}
        int changed=jdbc.update("UPDATE buyer_account_closure_request SET closure_state='CLOSED',blocker_count=0,aggregate_version=aggregate_version+1,closed_at=?,updated_at=? WHERE closure_ref=? AND closure_state='REQUESTED' AND aggregate_version=?",ts(now),ts(now),closureRef,expected);
        if(changed!=1)throw new Conflict("CLOSURE_VERSION_CONFLICT");
        jdbc.update("UPDATE buyer_identity SET subject_ref=?,provider_appid_digest=?,provider_subject_digest=?,status_code='CLOSED',updated_at=? WHERE buyer_id=? AND status_code='CLOSURE_REQUESTED'",
                tombstone,digest("APPID\n"+closureRef),digest("SUBJECT\n"+closureRef),ts(now),buyerId);
        return new ClosureView("BUYER_CLOSURE_V1",closureRef,"CLOSED",0,expected+1,String.valueOf(row.get("request_ref")),false);
    }

    ClosureView current(String subjectRef){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT closure_ref,closure_state,blocker_count,aggregate_version,request_ref FROM buyer_account_closure_request WHERE subject_ref=?",subjectRef);
        if(rows.size()!=1)throw new Conflict("CLOSURE_NOT_FOUND");Map<String,Object> r=rows.get(0);return new ClosureView("BUYER_CLOSURE_V1",String.valueOf(r.get("closure_ref")),String.valueOf(r.get("closure_state")),((Number)r.get("blocker_count")).intValue(),((Number)r.get("aggregate_version")).longValue(),String.valueOf(r.get("request_ref")),false);
    }
    private int blockers(String subject){
        Integer orders=jdbc.queryForObject("SELECT COUNT(*) FROM hz_release_order_snapshot s JOIN hz_order o ON o.order_ref=s.order_ref WHERE s.project_subject_ref=? AND (o.order_state NOT IN ('COMPLETED','CANCELLED','REFUNDED','FAILED') OR o.refund_state IN ('PENDING','PROCESSING','UNKNOWN'))",Integer.class,subject);
        Integer payments=jdbc.queryForObject("SELECT COUNT(*) FROM hz_payment_coordination WHERE buyer_subject_ref=? AND state_code IN ('CREATED','PENDING','PROCESSING','UNKNOWN','REFUND_PROCESSING','RECONCILIATION_REQUIRED')",Integer.class,subject);
        Integer refunds=jdbc.queryForObject("SELECT COUNT(*) FROM hz_payment_refund r JOIN hz_payment_coordination p ON p.merchant_order_ref=r.merchant_order_ref WHERE p.buyer_subject_ref=? AND r.state_code IN ('PENDING','PROCESSING','UNKNOWN')",Integer.class,subject);
        Integer topups=jdbc.queryForObject("SELECT COUNT(*) FROM hz_topup_coordination WHERE buyer_subject_ref=? AND state_code IN ('CREATED','SUBMITTING','SUBMITTED','PROCESSING','UNKNOWN','RECONCILIATION_REQUIRED')",Integer.class,subject);
        return safe(orders)+safe(payments)+safe(refunds)+safe(topups);
    }
    private ClosureView replay(String key,String requestDigest){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT closure_ref,closure_state,blocker_count,aggregate_version,request_ref,request_digest FROM buyer_account_closure_request WHERE idempotency_key=?",key);
        if(rows.isEmpty())return null;Map<String,Object> r=rows.get(0);if(!Objects.equals(requestDigest,r.get("request_digest")))throw new Conflict("CLOSURE_IDEMPOTENCY_CONFLICT");
        return new ClosureView("BUYER_CLOSURE_V1",String.valueOf(r.get("closure_ref")),String.valueOf(r.get("closure_state")),((Number)r.get("blocker_count")).intValue(),((Number)r.get("aggregate_version")).longValue(),String.valueOf(r.get("request_ref")),true);
    }
    private static int safe(Integer v){return v==null?0:v;}
    private static void validKey(String v){if(v==null||!v.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}"))throw new IllegalArgumentException("IDEMPOTENCY_KEY_INVALID");}
    private static String required(JsonNode b,String k){if(!b.path(k).isTextual()||b.path(k).textValue().isBlank())throw new IllegalArgumentException(k+" required");return b.path(k).textValue();}
    private static long version(JsonNode b){if(!b.path("expectedVersion").isIntegralNumber())throw new IllegalArgumentException("expectedVersion required");return b.path("expectedVersion").longValue();}
    private static String digest(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static Timestamp ts(Instant v){return Timestamp.from(v);}
    record ClosureView(String schemaVersion,String closureRef,String state,int blockerCount,long version,String requestRef,boolean replayed){}
    static final class Conflict extends RuntimeException{Conflict(String code){super(code);}}
}
