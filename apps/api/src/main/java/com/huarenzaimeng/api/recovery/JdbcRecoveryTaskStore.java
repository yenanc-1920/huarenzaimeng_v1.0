package com.huarenzaimeng.api.recovery;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository @Profile("release-mysql")
public class JdbcRecoveryTaskStore implements RecoveryTaskStore {
    private final JdbcTemplate jdbc;
    private final RecoveryManualReviewStore manualReview;
    public JdbcRecoveryTaskStore(JdbcTemplate jdbc){this(jdbc,RecoveryManualReviewStore.NOOP);}
    @org.springframework.beans.factory.annotation.Autowired
    JdbcRecoveryTaskStore(JdbcTemplate jdbc,RecoveryManualReviewStore manualReview){this.jdbc=jdbc;this.manualReview=manualReview;}

    @Override @Transactional public void schedule(String aggregateRef,String operation,long aggregateVersion,int budget,Instant deadline,Instant next){
        requireSchedule(aggregateRef,operation,aggregateVersion,budget,deadline,next);
        try{jdbc.update("INSERT INTO hz_unknown_recovery_task(task_ref,aggregate_ref,operation_code,expected_aggregate_version,state_code,budget_remaining,deadline,next_attempt_at,task_version,created_at,updated_at) VALUES (?,?,?,?,'READY',?,?,?,1,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))","REC-"+UUID.randomUUID(),aggregateRef,operation,aggregateVersion,budget,Timestamp.from(deadline),Timestamp.from(next));}
        catch(DuplicateKeyException duplicate){
            Task old=jdbc.query("SELECT "+columns()+" FROM hz_unknown_recovery_task WHERE aggregate_ref=? AND operation_code=? FOR UPDATE",(rs,n)->map(rs),aggregateRef,operation).stream().findFirst().orElseThrow();
            if(old.expectedAggregateVersion()>aggregateVersion)throw new IllegalStateException("RECOVERY_AGGREGATE_VERSION_REGRESSION");
            if(old.expectedAggregateVersion()==aggregateVersion)return;
            int changed=jdbc.update("UPDATE hz_unknown_recovery_task SET expected_aggregate_version=?,state_code='READY',budget_remaining=?,deadline=?,next_attempt_at=?,lease_owner=NULL,lease_until=NULL,last_error_code=NULL,completed_by_owner=NULL,completion_lease_version=NULL,task_version=task_version+1,updated_at=CURRENT_TIMESTAMP(3) WHERE task_ref=? AND task_version=?",aggregateVersion,budget,Timestamp.from(deadline),Timestamp.from(next),old.taskRef(),old.taskVersion());
            if(changed!=1)throw new IllegalStateException("RECOVERY_SCHEDULE_CAS_CONFLICT");
        }
    }

    @Override @Transactional public List<Task> claimBatch(String owner,int limit,Instant now,Duration leaseDuration){
        if(owner==null||!owner.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,63}")||limit<1||limit>100||now==null||leaseDuration==null||leaseDuration.isNegative()||leaseDuration.isZero())throw new IllegalArgumentException("RECOVERY_CLAIM_INVALID");
        Timestamp at=Timestamp.from(now);
        List<Task> exhausted=jdbc.query("SELECT "+columns()+" FROM hz_unknown_recovery_task WHERE state_code IN ('READY','LEASED') AND (deadline<? OR budget_remaining=0) FOR UPDATE",(rs,n)->map(rs),at);
        for(Task task:exhausted) terminalize(task,"SYSTEM",task.taskVersion(),at,"RECOVERY_WINDOW_EXHAUSTED",false);
        List<Task> candidates=jdbc.query("SELECT "+columns()+" FROM hz_unknown_recovery_task WHERE ((state_code='READY' AND next_attempt_at<=?) OR (state_code='LEASED' AND lease_until<?)) AND deadline>=? AND budget_remaining>0 ORDER BY next_attempt_at,task_ref LIMIT ?",(rs,n)->map(rs),at,at,at,limit);
        List<Task> claimed=new ArrayList<>();
        for(Task task:candidates){int changed=jdbc.update("UPDATE hz_unknown_recovery_task SET state_code='LEASED',lease_owner=?,lease_until=?,task_version=task_version+1,updated_at=? WHERE task_ref=? AND task_version=? AND ((state_code='READY' AND next_attempt_at<=?) OR (state_code='LEASED' AND lease_until<?)) AND deadline>=? AND budget_remaining>0",owner,Timestamp.from(now.plus(leaseDuration)),at,task.taskRef(),task.taskVersion(),at,at,at);if(changed==1)claimed.add(require(task.taskRef()));}
        return List.copyOf(claimed);
    }

    @Override @Transactional public Optional<Task> consumePermit(String ref,String owner,long version,Instant now){
        Timestamp at=Timestamp.from(now);
        int changed=jdbc.update("UPDATE hz_unknown_recovery_task SET budget_remaining=budget_remaining-1,task_version=task_version+1,updated_at=? WHERE task_ref=? AND state_code='LEASED' AND lease_owner=? AND task_version=? AND lease_until>=? AND deadline>=? AND budget_remaining>0",at,ref,owner,version,at,at);
        if(changed==1)return Optional.of(require(ref));
        List<Task> exhausted=jdbc.query("SELECT "+columns()+" FROM hz_unknown_recovery_task WHERE task_ref=? AND state_code='LEASED' AND lease_owner=? AND task_version=? AND (deadline<? OR budget_remaining=0) FOR UPDATE",(rs,n)->map(rs),ref,owner,version,at);
        if(exhausted.size()==1)terminalize(exhausted.get(0),owner,version,at,"RECOVERY_WINDOW_EXHAUSTED",true);
        return Optional.empty();
    }

    @Override @Transactional public Completion succeed(String ref,String owner,long version,Instant now){return finish(ref,owner,version,now,true,null,null);}
    @Override @Transactional public Completion retry(String ref,String owner,long version,Instant now,Instant next,String error){
        if(next==null||error==null||error.isBlank())throw new IllegalArgumentException("RECOVERY_RETRY_INVALID");
        return finish(ref,owner,version,now,false,next,error);
    }
    @Override @Transactional public Completion dead(String ref,String owner,long version,Instant now,String error){
        if(error==null||error.isBlank())throw new IllegalArgumentException("RECOVERY_DEAD_INVALID");Timestamp at=Timestamp.from(now);
        List<Task> owned=jdbc.query("SELECT "+columns()+" FROM hz_unknown_recovery_task WHERE task_ref=? AND state_code='LEASED' AND lease_owner=? AND task_version=? AND lease_until>=? FOR UPDATE",(rs,n)->map(rs),ref,owner,version,at);
        if(owned.isEmpty())return completionDisposition(ref,owner,version);
        Task task=owned.get(0);String reviewRef=manualReview.open(task,error);
        int changed=jdbc.update("UPDATE hz_unknown_recovery_task SET state_code='DEAD',completed_by_owner=?,completion_lease_version=?,manual_review_ref=?,lease_owner=NULL,lease_until=NULL,last_error_code=?,task_version=task_version+1,updated_at=? WHERE task_ref=? AND state_code='LEASED' AND lease_owner=? AND task_version=? AND lease_until>=?",owner,version,reviewRef,error,at,ref,owner,version,at);
        if(changed==1)return Completion.COMPLETED;return completionDisposition(ref,owner,version);
    }
    private Completion finish(String ref,String owner,long version,Instant now,boolean success,Instant next,String error){
        Timestamp at=Timestamp.from(now);String target=success?"SUCCEEDED":"READY";
        int changed=jdbc.update("UPDATE hz_unknown_recovery_task SET state_code=?,next_attempt_at=COALESCE(?,next_attempt_at),completed_by_owner=?,completion_lease_version=?,lease_owner=NULL,lease_until=NULL,last_error_code=?,task_version=task_version+1,updated_at=? WHERE task_ref=? AND state_code='LEASED' AND lease_owner=? AND task_version=? AND lease_until>=?",target,next==null?null:Timestamp.from(next),success?owner:null,success?version:null,error,at,ref,owner,version,at);
        if(changed==1){if(!success){Task task=require(ref);if(task.budgetRemaining()==0||task.deadline().isBefore(now))terminalize(task,owner,task.taskVersion(),at,"RECOVERY_WINDOW_EXHAUSTED",false);}return Completion.COMPLETED;}
        return completionDisposition(ref,owner,version);
    }
    private Completion completionDisposition(String ref,String owner,long version){return jdbc.query("SELECT state_code,completed_by_owner,completion_lease_version FROM hz_unknown_recovery_task WHERE task_ref=?",(rs,n)->(State.valueOf(rs.getString(1))==State.SUCCEEDED||State.valueOf(rs.getString(1))==State.DEAD)&&owner.equals(rs.getString(2))&&rs.getLong(3)==version?Completion.REPLAY:Completion.REJECTED,ref).stream().findFirst().orElseThrow(()->new IllegalArgumentException("RECOVERY_TASK_NOT_FOUND"));}
    @Override public Task require(String ref){return jdbc.query("SELECT "+columns()+" FROM hz_unknown_recovery_task WHERE task_ref=?",(rs,n)->map(rs),ref).stream().findFirst().orElseThrow(()->new IllegalArgumentException("RECOVERY_TASK_NOT_FOUND"));}
    private void terminalize(Task task,String owner,long completionVersion,Timestamp at,String reason,boolean requireOwner){String reviewRef=manualReview.open(task,reason);String sql="UPDATE hz_unknown_recovery_task SET state_code='DEAD',completed_by_owner=?,completion_lease_version=?,manual_review_ref=?,lease_owner=NULL,lease_until=NULL,last_error_code=?,task_version=task_version+1,updated_at=? WHERE task_ref=? AND task_version=? AND state_code IN ('READY','LEASED')"+(requireOwner?" AND lease_owner=?":"");int changed=requireOwner?jdbc.update(sql,owner,completionVersion,reviewRef,reason,at,task.taskRef(),task.taskVersion(),owner):jdbc.update(sql,owner,completionVersion,reviewRef,reason,at,task.taskRef(),task.taskVersion());if(changed!=1)throw new IllegalStateException("RECOVERY_DEAD_CAS_CONFLICT");}
    private static String columns(){return "task_ref,aggregate_ref,operation_code,expected_aggregate_version,state_code,budget_remaining,deadline,next_attempt_at,lease_owner,lease_until,task_version,last_error_code,manual_review_ref";}
    private static Task map(ResultSet rs)throws SQLException{return new Task(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),State.valueOf(rs.getString(5)),rs.getInt(6),rs.getTimestamp(7).toInstant(),rs.getTimestamp(8).toInstant(),rs.getString(9),rs.getTimestamp(10)==null?null:rs.getTimestamp(10).toInstant(),rs.getLong(11),rs.getString(12),rs.getString(13));}
    private static void requireSchedule(String aggregate,String operation,long version,int budget,Instant deadline,Instant next){if(aggregate==null||aggregate.isBlank()||operation==null||!operation.matches("[A-Z][A-Z0-9_]{2,39}")||version<1||budget<0||budget>100||deadline==null||next==null||next.isAfter(deadline))throw new IllegalArgumentException("RECOVERY_SCHEDULE_INVALID");}
}
