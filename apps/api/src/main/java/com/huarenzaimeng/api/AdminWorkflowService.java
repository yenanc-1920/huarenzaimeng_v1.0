package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.huarenzaimeng.core.BusinessEventLinker;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@Profile("release-mysql")
class AdminWorkflowService {
    private static final String DETAIL_SCHEMA_VERSION="1.0";
    private static final String DETAIL_PROJECTION_VERSION="workflow-detail-v1";
    private static final Set<String> CASE_EVENTS=Set.of("FOLLOW_UP","CLAIM","ASSIGN","ESCALATE","RESOLVE","CLOSE");
    private static final Set<String> RECON_ACTIONS=Set.of("CLAIM","QUERY_WECHAT","QUERY_PROVIDER","NOTE","TRANSFER_CS","TRANSFER_REVIEW","RESOLVE","CLOSE");
    private final JdbcTemplate jdbc;
    private final BusinessEventStore events;
    private final AdminSelfApprovalPolicy selfApproval;
    @Autowired AdminWorkflowService(JdbcTemplate jdbc,BusinessEventStore events,AdminSelfApprovalPolicy selfApproval){this.jdbc=jdbc;this.events=events;this.selfApproval=selfApproval;}
    AdminWorkflowService(JdbcTemplate jdbc,BusinessEventStore events){this(jdbc,events,null);}

    @Transactional
    Map<String,Object> createCase(String key,JsonNode body,String actor){
        validateKey(key);String ref=required(body,"caseRef");String description=required(body,"description");
        String orderRef=required(body,"relatedOrderRef");
        String requestDigest=digest(body);
        Map<String,Object> prior=replayCaseEventIfPresent(key,ref,requestDigest);if(prior!=null)return prior;
        validateRef(ref);max(description,1000,"DESCRIPTION_TOO_LONG");
        try{
            jdbc.update("INSERT INTO hz_customer_case(case_ref,source_type,issue_type,related_order_ref,priority_code,owner_ref,case_state,description,data_origin,aggregate_version,created_at,updated_at) VALUES(?,?,?,?,?,?,'OPEN',?,'ADMIN',1,?,?)",
                    ref,required(body,"sourceType"),required(body,"issueType"),orderRef,required(body,"priorityCode"),optional(body,"ownerRef"),description,now(),now());
            jdbc.update("INSERT INTO hz_customer_case_event(event_ref,case_ref,idempotency_key,request_digest,event_type,note_text,evidence_ref,from_state,to_state,actor_ref,created_at) VALUES(?,?,?,?,?,?,NULL,'NONE','OPEN',?,?)",
                    "EVT-"+UUID.randomUUID(),ref,key,requestDigest,"CREATE",description,actor,now());
            events.bindSupportCase(ref,orderRef);
            events.append(orderRef,BusinessEventLinker.Type.SUPPORT_CASE_OPENED,"CASE-OPEN-"+requestDigest.substring(0,24),requestDigest,Instant.now());
        }catch(DuplicateKeyException duplicate){return replayCaseEvent(key,ref,requestDigest);}
        return Map.of("caseRef",ref,"state","OPEN","version",1,"replayed",false);
    }

    @Transactional
    Map<String,Object> appendCaseEvent(String ref,String key,JsonNode body,String actor){
        validateRef(ref);validateKey(key);String type=required(body,"eventType");if(!CASE_EVENTS.contains(type))throw new IllegalArgumentException("CASE_EVENT_INVALID");
        String note=required(body,"note");max(note,1000,"NOTE_TOO_LONG");long expected=positiveVersion(body);
        String owner=optional(body,"ownerRef");if(Set.of("CLAIM","ASSIGN").contains(type)&&owner==null)throw new IllegalArgumentException("OWNER_REQUIRED");
        String requestDigest=digest(body);
        Map<String,Object> prior=replayCaseEventIfPresent(key,ref,requestDigest);if(prior!=null)return prior;
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT case_state,aggregate_version FROM hz_customer_case WHERE case_ref=? FOR UPDATE",ref);
        if(rows.isEmpty())throw new WorkflowConflict("CASE_NOT_FOUND");String before=text(rows.get(0),"case_state");
        if(number(rows.get(0),"aggregate_version")!=expected)throw new WorkflowConflict("VERSION_CONFLICT");String after=caseState(before,type);
        String eventRef="EVT-"+UUID.randomUUID();
        try{jdbc.update("INSERT INTO hz_customer_case_event(event_ref,case_ref,idempotency_key,request_digest,event_type,note_text,evidence_ref,from_state,to_state,actor_ref,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                eventRef,ref,key,requestDigest,type,note,optional(body,"evidenceRef"),before,after,actor,now());}
        catch(DuplicateKeyException duplicate){return replayCaseEvent(key,ref,requestDigest);}
        if(jdbc.update("UPDATE hz_customer_case SET case_state=?,owner_ref=COALESCE(?,owner_ref),aggregate_version=aggregate_version+1,updated_at=? WHERE case_ref=? AND aggregate_version=?",after,owner,now(),ref,expected)!=1)
            throw new WorkflowConflict("VERSION_CONFLICT");
        String relatedOrderRef=jdbc.queryForObject("SELECT related_order_ref FROM hz_customer_case WHERE case_ref=?",String.class,ref);if(relatedOrderRef==null||relatedOrderRef.isBlank())throw new WorkflowConflict("CASE_ORDER_REQUIRED");
        events.append(relatedOrderRef,BusinessEventLinker.Type.SUPPORT_CASE_UPDATED,eventRef,requestDigest,Instant.now());
        return Map.of("caseRef",ref,"state",after,"version",expected+1,"replayed",false);
    }

    Map<String,Object> appendReconciliationEvent(String ref,String key,JsonNode body,String actor){
        validateRef(ref);validateKey(key);String action=required(body,"actionType");if(!RECON_ACTIONS.contains(action))throw new IllegalArgumentException("RECONCILIATION_ACTION_INVALID");
        String note=required(body,"note");max(note,1000,"NOTE_TOO_LONG");long expected=positiveVersion(body);
        String owner=optional(body,"ownerRef");if(("CLAIM".equals(action)||action.startsWith("TRANSFER_"))&&owner==null)throw new IllegalArgumentException("OWNER_REQUIRED");
        String requestDigest=digest(body);
        Map<String,Object> prior=replayReconciliationEventIfPresent(key,ref,requestDigest);if(prior!=null)return prior;
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT case_state,aggregate_version FROM hz_reconciliation_case WHERE reconciliation_ref=? FOR UPDATE",ref);
        if(rows.isEmpty())throw new WorkflowConflict("RECONCILIATION_NOT_FOUND");String before=text(rows.get(0),"case_state");
        if(number(rows.get(0),"aggregate_version")!=expected)throw new WorkflowConflict("VERSION_CONFLICT");String after=reconciliationState(before,action);
        String evidence=optional(body,"evidenceRef");if(Set.of("RESOLVE","CLOSE").contains(action)&&(evidence==null||evidence.isBlank()))throw new IllegalArgumentException("EVIDENCE_REQUIRED");
        try{jdbc.update("INSERT INTO hz_reconciliation_event(event_ref,reconciliation_ref,idempotency_key,request_digest,action_type,evidence_ref,note_text,from_state,to_state,actor_ref,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                "REV-"+UUID.randomUUID(),ref,key,requestDigest,action,evidence,note,before,after,actor,now());}
        catch(DuplicateKeyException duplicate){return replayReconciliationEvent(key,ref,requestDigest);}
        if(jdbc.update("UPDATE hz_reconciliation_case SET case_state=?,owner_ref=COALESCE(?,owner_ref),aggregate_version=aggregate_version+1,updated_at=? WHERE reconciliation_ref=? AND aggregate_version=?",after,owner,now(),ref,expected)!=1)
            throw new WorkflowConflict("VERSION_CONFLICT");
        return Map.of("reconciliationRef",ref,"state",after,"version",expected+1,"replayed",false);
    }

    Map<String,Object> submitReview(String key,JsonNode body,String actor){
        validateKey(key);String ref="REVIEW-"+UUID.randomUUID();String objectType=required(body,"objectType"),objectRef=required(body,"objectRef");long version=positiveVersion(body);
        String requestDigest=digest(body);Map<String,Object> prior=replayReviewSubmissionIfPresent(key,requestDigest);if(prior!=null)return prior;
        validateRef(objectRef);requireReviewObjectUnderReview(objectType,objectRef,version);
        try{jdbc.update("INSERT INTO hz_content_review_task(review_ref,idempotency_key,request_digest,object_type,object_ref,object_version,submitter_ref,priority_code,review_state,created_at) VALUES(?,?,?,?,?,?,?,?,'PENDING',?)",
                ref,key,requestDigest,objectType,objectRef,version,actor,required(body,"priorityCode"),now());}
        catch(DuplicateKeyException duplicate){
            List<Map<String,Object>> replay=jdbc.queryForList("SELECT review_ref,review_state,object_version,request_digest FROM hz_content_review_task WHERE idempotency_key=?",key);
            if(replay.isEmpty())throw new WorkflowConflict("REVIEW_ALREADY_SUBMITTED");
            requireSameDigest(replay.get(0),requestDigest);
            return Map.of("reviewRef",text(replay.get(0),"review_ref"),"state",text(replay.get(0),"review_state"),"objectVersion",number(replay.get(0),"object_version"),"replayed",true);
        }
        return Map.of("reviewRef",ref,"state","PENDING","objectVersion",version);
    }

    Map<String,Object> decideReview(String ref,String key,JsonNode body,String actor){
        validateRef(ref);validateKey(key);String decision=required(body,"decision");if(!Set.of("APPROVED","REJECTED","WITHDRAWN").contains(decision))throw new IllegalArgumentException("REVIEW_DECISION_INVALID");
        String reason=required(body,"reason");max(reason,500,"REASON_TOO_LONG");
        String requestDigest=digest(body);
        Map<String,Object> prior=replayReviewDecisionIfPresent(ref,key,requestDigest);if(prior!=null)return prior;
        List<Map<String,Object>> pending=jdbc.queryForList("SELECT submitter_ref,review_state FROM hz_content_review_task WHERE review_ref=? FOR UPDATE",ref);
        if(pending.size()!=1)throw new WorkflowConflict("REVIEW_STATE_CONFLICT");boolean same=actor.equals(String.valueOf(pending.get(0).get("submitter_ref")));
        AdminSelfApprovalPolicy.Decision exception;
        try{exception=same?(selfApproval==null?AdminSelfApprovalPolicy.Decision.separated():selfApproval.decide(actor,true)):AdminSelfApprovalPolicy.Decision.separated();}
        catch(AdminSelfApprovalPolicy.PolicyConflict denied){throw new WorkflowConflict(denied.getMessage());}
        if(same&&!exception.selfApproved())throw new WorkflowConflict("REVIEW_DUTY_SEPARATION_REQUIRED");
        int changed;
        try{changed=jdbc.update("UPDATE hz_content_review_task SET review_state=?,reviewer_ref=?,decision_idempotency_key=?,decision_request_digest=?,decision_reason=?,self_approved=?,exception_policy_version=?,decided_at=? WHERE review_ref=? AND review_state='PENDING'",decision,actor,key,requestDigest,reason,exception.selfApproved()?1:0,exception.exceptionPolicyVersion(),now(),ref);}
        catch(DuplicateKeyException duplicate){return replayReviewDecision(ref,key,requestDigest);}
        if(changed!=1){
            List<Map<String,Object>> replay=jdbc.queryForList("SELECT review_state,submitter_ref,decision_idempotency_key,decision_request_digest FROM hz_content_review_task WHERE review_ref=?",ref);
            if(replay.size()==1&&key.equals(replay.get(0).get("decision_idempotency_key"))){requireDecisionDigest(replay.get(0),requestDigest);return Map.of("reviewRef",ref,"state",text(replay.get(0),"review_state"),"decisionKey",key,"replayed",true);}
            throw new WorkflowConflict("REVIEW_STATE_CONFLICT");
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("reviewRef",ref);result.put("state",decision);result.put("decisionKey",key);result.put("replayed",false);result.put("selfApproved",exception.selfApproved());result.put("exceptionPolicyVersion",exception.exceptionPolicyVersion());return result;
    }

    WorkflowDetail<CustomerCaseView,CustomerCaseEventView> caseDetail(String ref){
        List<CustomerCaseView> items=jdbc.query("SELECT case_ref,source_type,issue_type,related_order_ref,priority_code,owner_ref,case_state,description,data_origin,aggregate_version,created_at,updated_at FROM hz_customer_case WHERE case_ref=?",(rs,n)->new CustomerCaseView(
                rs.getString("case_ref"),rs.getString("source_type"),rs.getString("issue_type"),rs.getString("related_order_ref"),rs.getString("priority_code"),rs.getString("owner_ref"),rs.getString("case_state"),rs.getString("description"),rs.getString("data_origin"),rs.getLong("aggregate_version"),rs.getTimestamp("created_at"),rs.getTimestamp("updated_at")),ref);
        if(items.size()!=1)throw new WorkflowConflict("CASE_NOT_FOUND");
        List<CustomerCaseEventView> history=jdbc.query("SELECT event_ref,event_type,note_text,evidence_ref,from_state,to_state,actor_ref,created_at FROM hz_customer_case_event WHERE case_ref=? ORDER BY created_at,event_ref",(rs,n)->new CustomerCaseEventView(
                rs.getString("event_ref"),rs.getString("event_type"),rs.getString("note_text"),rs.getString("evidence_ref"),rs.getString("from_state"),rs.getString("to_state"),rs.getString("actor_ref"),rs.getTimestamp("created_at")),ref);
        return new WorkflowDetail<>(DETAIL_SCHEMA_VERSION,DETAIL_PROJECTION_VERSION,items.get(0),history);
    }
    WorkflowDetail<ReconciliationView,ReconciliationEventView> reconciliationDetail(String ref){
        List<ReconciliationView> items=jdbc.query("SELECT reconciliation_ref,order_ref,difference_type,amount,currency,case_state,owner_ref,data_origin,aggregate_version,discovered_at,updated_at FROM hz_reconciliation_case WHERE reconciliation_ref=?",(rs,n)->new ReconciliationView(
                rs.getString("reconciliation_ref"),rs.getString("order_ref"),rs.getString("difference_type"),rs.getBigDecimal("amount"),rs.getString("currency"),rs.getString("case_state"),rs.getString("owner_ref"),rs.getString("data_origin"),rs.getLong("aggregate_version"),rs.getTimestamp("discovered_at"),rs.getTimestamp("updated_at")),ref);
        if(items.size()!=1)throw new WorkflowConflict("RECONCILIATION_NOT_FOUND");
        List<ReconciliationEventView> history=jdbc.query("SELECT event_ref,action_type,evidence_ref,note_text,from_state,to_state,actor_ref,created_at FROM hz_reconciliation_event WHERE reconciliation_ref=? ORDER BY created_at,event_ref",(rs,n)->new ReconciliationEventView(
                rs.getString("event_ref"),rs.getString("action_type"),rs.getString("evidence_ref"),rs.getString("note_text"),rs.getString("from_state"),rs.getString("to_state"),rs.getString("actor_ref"),rs.getTimestamp("created_at")),ref);
        return new WorkflowDetail<>(DETAIL_SCHEMA_VERSION,DETAIL_PROJECTION_VERSION,items.get(0),history);
    }
    List<Map<String,Object>> reviews(){return jdbc.queryForList("SELECT review_ref AS reviewRef,object_type AS objectType,object_ref AS objectRef,object_version AS objectVersion,priority_code AS priorityCode,review_state AS state,submitter_ref AS submitterRef,reviewer_ref AS reviewerRef,decision_reason AS decisionReason,created_at AS createdAt,decided_at AS decidedAt FROM hz_content_review_task ORDER BY created_at DESC,review_ref LIMIT 200");}
    List<Map<String,Object>> contentHistory(String type,String ref){validateRef(ref);if(!Set.of("directory-entries","holidays","news","products").contains(type))throw new IllegalArgumentException("CONTENT_HISTORY_TYPE_INVALID");return jdbc.queryForList("SELECT history_ref AS historyRef,object_type AS objectType,object_ref AS objectRef,object_version AS objectVersion,snapshot_json AS snapshotJson,action_type AS actionType,actor_ref AS actorRef,reason,created_at AS createdAt FROM hz_content_version_history WHERE object_type=? AND object_ref=? ORDER BY object_version DESC",type,ref);}
    List<Map<String,Object>> catalogBatches(){return jdbc.queryForList("SELECT batch_ref AS batchRef,provider_code AS providerCode,source_kind AS sourceKind,source_digest AS sourceDigest,item_count AS itemCount,batch_state AS state,captured_at AS capturedAt,actor_ref AS actorRef FROM hz_supplier_catalog_batch_snapshot ORDER BY captured_at DESC,batch_ref LIMIT 200");}
    List<Map<String,Object>> fxSnapshots(){return jdbc.queryForList("SELECT fx_snapshot_ref AS fxSnapshotRef,source_code AS sourceCode,base_currency AS baseCurrency,quote_currency AS quoteCurrency,rate_value AS rateValue,conversion_path AS conversionPath,observed_at AS observedAt,valid_until AS validUntil,source_digest AS sourceDigest FROM hz_fx_rate_snapshot ORDER BY observed_at DESC,fx_snapshot_ref LIMIT 200");}
    record WorkflowDetail<I,H>(String schemaVersion,String projectionVersion,I item,List<H> history){}
    record CustomerCaseView(String caseRef,String sourceType,String issueType,String relatedOrderRef,String priorityCode,String ownerRef,String state,String description,String dataOrigin,long version,Timestamp createdAt,Timestamp updatedAt){}
    record CustomerCaseEventView(String eventRef,String eventType,String note,String evidenceRef,String fromState,String toState,String actorRef,Timestamp createdAt){}
    record ReconciliationView(String reconciliationRef,String orderRef,String differenceType,BigDecimal amount,String currency,String state,String ownerRef,String dataOrigin,long version,Timestamp discoveredAt,Timestamp updatedAt){}
    record ReconciliationEventView(String eventRef,String actionType,String evidenceRef,String note,String fromState,String toState,String actorRef,Timestamp createdAt){}
    private Map<String,Object> replayCaseEventIfPresent(String key,String ref,String requestDigest){List<Map<String,Object>> rows=jdbc.queryForList("SELECT case_ref,to_state,request_digest FROM hz_customer_case_event WHERE idempotency_key=?",key);if(rows.isEmpty())return null;if(rows.size()!=1||!ref.equals(String.valueOf(rows.get(0).get("case_ref"))))throw new WorkflowConflict("IDEMPOTENCY_CONFLICT");requireSameDigest(rows.get(0),requestDigest);return Map.of("caseRef",ref,"state",text(rows.get(0),"to_state"),"replayed",true);}
    private Map<String,Object> replayCaseEvent(String key,String ref,String requestDigest){Map<String,Object> replay=replayCaseEventIfPresent(key,ref,requestDigest);if(replay==null)throw new WorkflowConflict("IDEMPOTENCY_CONFLICT");return replay;}
    private Map<String,Object> replayReconciliationEventIfPresent(String key,String ref,String requestDigest){List<Map<String,Object>> rows=jdbc.queryForList("SELECT reconciliation_ref,to_state,request_digest FROM hz_reconciliation_event WHERE idempotency_key=?",key);if(rows.isEmpty())return null;if(rows.size()!=1||!ref.equals(String.valueOf(rows.get(0).get("reconciliation_ref"))))throw new WorkflowConflict("IDEMPOTENCY_CONFLICT");requireSameDigest(rows.get(0),requestDigest);return Map.of("reconciliationRef",ref,"state",text(rows.get(0),"to_state"),"replayed",true);}
    private Map<String,Object> replayReconciliationEvent(String key,String ref,String requestDigest){Map<String,Object> replay=replayReconciliationEventIfPresent(key,ref,requestDigest);if(replay==null)throw new WorkflowConflict("IDEMPOTENCY_CONFLICT");return replay;}
    private Map<String,Object> replayReviewSubmissionIfPresent(String key,String requestDigest){List<Map<String,Object>> rows=jdbc.queryForList("SELECT review_ref,review_state,object_version,request_digest FROM hz_content_review_task WHERE idempotency_key=?",key);if(rows.isEmpty())return null;if(rows.size()!=1)throw new WorkflowConflict("IDEMPOTENCY_CONFLICT");requireSameDigest(rows.get(0),requestDigest);return Map.of("reviewRef",text(rows.get(0),"review_ref"),"state",text(rows.get(0),"review_state"),"objectVersion",number(rows.get(0),"object_version"),"replayed",true);}
    private Map<String,Object> replayReviewDecisionIfPresent(String ref,String key,String requestDigest){List<Map<String,Object>> rows=jdbc.queryForList("SELECT review_ref,review_state,decision_request_digest FROM hz_content_review_task WHERE decision_idempotency_key=?",key);if(rows.isEmpty())return null;if(rows.size()!=1||!ref.equals(String.valueOf(rows.get(0).get("review_ref"))))throw new WorkflowConflict("IDEMPOTENCY_CONFLICT");requireDecisionDigest(rows.get(0),requestDigest);return Map.of("reviewRef",ref,"state",text(rows.get(0),"review_state"),"decisionKey",key,"replayed",true);}
    private Map<String,Object> replayReviewDecision(String ref,String key,String requestDigest){List<Map<String,Object>> rows=jdbc.queryForList("SELECT review_ref,review_state,decision_request_digest FROM hz_content_review_task WHERE decision_idempotency_key=?",key);if(rows.size()!=1||!ref.equals(String.valueOf(rows.get(0).get("review_ref"))))throw new WorkflowConflict("IDEMPOTENCY_CONFLICT");requireDecisionDigest(rows.get(0),requestDigest);return Map.of("reviewRef",ref,"state",text(rows.get(0),"review_state"),"decisionKey",key,"replayed",true);}
    private void requireReviewObjectUnderReview(String type,String ref,long version){
        String sql=switch(type){
            case"directory-entries"->"SELECT aggregate_version,publish_state FROM hz_directory_entry WHERE entry_ref=? FOR UPDATE";
            case"holidays"->"SELECT aggregate_version,publish_state FROM hz_holiday_rule WHERE rule_ref=? FOR UPDATE";
            case"news"->"SELECT aggregate_version,publish_state FROM hz_news_article WHERE article_ref=? FOR UPDATE";
            case"products"->"SELECT aggregate_version,enable_state AS publish_state FROM hz_platform_product WHERE platform_product_ref=? FOR UPDATE";
            default->throw new IllegalArgumentException("REVIEW_OBJECT_INVALID");};
        List<Map<String,Object>> rows=jdbc.queryForList(sql,ref);
        if(rows.isEmpty())throw new WorkflowConflict("REVIEW_OBJECT_NOT_FOUND");
        if(number(rows.get(0),"aggregate_version")!=version)throw new WorkflowConflict("REVIEW_OBJECT_VERSION_CONFLICT");
        if(!"UNDER_REVIEW".equals(text(rows.get(0),"publish_state")))throw new WorkflowConflict("REVIEW_OBJECT_STATE_CONFLICT");
    }
    private static void requireSameDigest(Map<String,Object> row,String expected){if(!expected.equals(String.valueOf(row.get("request_digest"))))throw new WorkflowConflict("IDEMPOTENCY_CONFLICT");}
    private static void requireDecisionDigest(Map<String,Object> row,String expected){if(!expected.equals(String.valueOf(row.get("decision_request_digest"))))throw new WorkflowConflict("IDEMPOTENCY_CONFLICT");}
    static String caseState(String before,String event){return switch(event){case"FOLLOW_UP","CLAIM","ASSIGN","ESCALATE"->Set.of("OPEN","IN_PROGRESS").contains(before)?"IN_PROGRESS":illegal("CASE_STATE_CONFLICT");case"RESOLVE"->"IN_PROGRESS".equals(before)?"RESOLVED":illegal("CASE_STATE_CONFLICT");case"CLOSE"->"RESOLVED".equals(before)?"CLOSED":illegal("CASE_STATE_CONFLICT");default->illegal("CASE_EVENT_INVALID");};}
    static String reconciliationState(String before,String action){return switch(action){case"CLAIM","QUERY_WECHAT","QUERY_PROVIDER","NOTE","TRANSFER_CS","TRANSFER_REVIEW"->Set.of("OPEN","IN_REVIEW").contains(before)?"IN_REVIEW":illegal("RECONCILIATION_STATE_CONFLICT");case"RESOLVE"->"IN_REVIEW".equals(before)?"RESOLVED":illegal("RECONCILIATION_STATE_CONFLICT");case"CLOSE"->"RESOLVED".equals(before)?"CLOSED":illegal("RECONCILIATION_STATE_CONFLICT");default->illegal("RECONCILIATION_ACTION_INVALID");};}
    private static String illegal(String code){throw new WorkflowConflict(code);}
    private static Timestamp now(){return Timestamp.from(Instant.now());}
    private static String required(JsonNode body,String field){if(!body.path(field).isTextual()||body.path(field).textValue().isBlank())throw new IllegalArgumentException(field+"_REQUIRED");return body.path(field).textValue();}
    private static String optional(JsonNode body,String field){return body.path(field).isTextual()&&!body.path(field).textValue().isBlank()?body.path(field).textValue():null;}
    private static long positiveVersion(JsonNode body){if(!body.path("expectedVersion").canConvertToLong()||body.path("expectedVersion").longValue()<1)throw new IllegalArgumentException("EXPECTED_VERSION_INVALID");return body.path("expectedVersion").longValue();}
    private static void validateKey(String key){if(key==null||!key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,57}"))throw new IllegalArgumentException("IDEMPOTENCY_KEY_INVALID");}
    private static void validateRef(String ref){if(ref==null||!ref.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}"))throw new IllegalArgumentException("REFERENCE_INVALID");}
    private static void max(String text,int max,String code){if(text.length()>max)throw new IllegalArgumentException(code);}
    private static String text(Map<String,Object> row,String key){Object value=row.get(key);if(value==null)throw new WorkflowConflict("WORKFLOW_DATA_INVALID");return value.toString();}
    private static long number(Map<String,Object> row,String key){Object value=row.get(key);if(!(value instanceof Number n))throw new WorkflowConflict("WORKFLOW_DATA_INVALID");return n.longValue();}
    private static String digest(JsonNode body){try{return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(body.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    static final class WorkflowConflict extends RuntimeException{WorkflowConflict(String code){super(code);}}
}
