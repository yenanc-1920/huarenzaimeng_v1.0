package com.huarenzaimeng.api.payment;

import com.huarenzaimeng.api.BusinessEventStore;
import com.huarenzaimeng.core.BusinessEventLinker;
import com.huarenzaimeng.api.recovery.RecoveryTaskStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;

@Repository @Profile("release-mysql")
class JdbcWeChatPayStore implements WeChatPayCoordinator.Store {
    private final JdbcTemplate jdbc;
    private final BusinessEventStore events;
    private final RecoveryTaskStore recovery;
    JdbcWeChatPayStore(JdbcTemplate jdbc){this(jdbc,null,null);}
    JdbcWeChatPayStore(JdbcTemplate jdbc,BusinessEventStore events){this(jdbc,events,null);}
    @Autowired JdbcWeChatPayStore(JdbcTemplate jdbc,BusinessEventStore events,RecoveryTaskStore recovery){this.jdbc=jdbc;this.events=events;this.recovery=recovery;}
    @Transactional public WeChatPayCoordinator.Begin begin(PaymentOrderSnapshotPort.Snapshot s,String digest,int budget,Instant deadline){
        try{jdbc.update("INSERT INTO hz_payment_coordination (merchant_order_ref,request_digest,buyer_subject_ref,quote_ref,price_snapshot_digest,amount_minor,currency,state_code,query_budget_remaining,query_deadline,refunded_minor,aggregate_version,updated_at) VALUES (?,?,?,?,?,?,?,'NEW',?,?,0,1,CURRENT_TIMESTAMP(3))",s.merchantOrderRef(),digest,s.buyerSubjectRef(),s.quoteRef(),s.priceSnapshotDigest(),s.amountMinor(),s.currency(),budget,Timestamp.from(deadline));append(s.merchantOrderRef(),BusinessEventLinker.Type.ORDER_CREATED,"PAY-CREATE-"+digest.substring(0,24),digest,Instant.now());return WeChatPayCoordinator.Begin.CREATED;}
        catch(DuplicateKeyException duplicate){var row=require(s.merchantOrderRef());return row.requestDigest().equals(digest)&&row.buyerSubjectRef().equals(s.buyerSubjectRef())&&row.quoteRef().equals(s.quoteRef())&&row.priceSnapshotDigest().equals(s.priceSnapshotDigest())&&row.amountMinor()==s.amountMinor()&&row.currency().equals(s.currency())?WeChatPayCoordinator.Begin.REPLAY:WeChatPayCoordinator.Begin.CONFLICT;}
    }
    public WeChatPayCoordinator.View require(String ref){return jdbc.query("SELECT merchant_order_ref,request_digest,buyer_subject_ref,quote_ref,price_snapshot_digest,amount_minor,currency,provider_ref,state_code,evidence_ref,query_budget_remaining,query_deadline,refunded_minor,aggregate_version,updated_at FROM hz_payment_coordination WHERE merchant_order_ref=?",(rs,n)->new WeChatPayCoordinator.View(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getLong(6),rs.getString(7),rs.getString(8),WeChatPayCoordinator.State.valueOf(rs.getString(9)),rs.getString(10),rs.getInt(11),rs.getTimestamp(12).toInstant(),rs.getLong(13),rs.getLong(14),rs.getTimestamp(15).toInstant()),ref).stream().findFirst().orElseThrow(()->new WeChatPayCoordinator.Conflict("PAYMENT_NOT_FOUND"));}
    @Transactional public void providerResult(String ref,String provider,WeChatPayCoordinator.State state,String evidence){
        boolean changed=advance(ref,provider,state,evidence);
        if(changed&&state==WeChatPayCoordinator.State.PAID)appendPaymentConfirmed(ref);
        if(changed&&state==WeChatPayCoordinator.State.UNKNOWN)schedulePayment(ref);
    }
    @Transactional public void observation(String ref,WeChatPayCoordinator.State state,String evidence){
        boolean changed=advance(ref,null,state,evidence);
        if(changed&&state==WeChatPayCoordinator.State.PAID)appendPaymentConfirmed(ref);
        if(changed&&state==WeChatPayCoordinator.State.UNKNOWN)schedulePayment(ref);
    }
    @Transactional public WeChatPayCoordinator.NotificationDisposition receiveNotification(WeChatPayPort.VerifiedNotification n,WeChatPayCoordinator.State state){
        try{jdbc.update("INSERT INTO hz_payment_notification (notification_id,merchant_order_ref,provider_ref,notification_digest,app_id,mch_id,certificate_serial,amount_minor,currency,state_code,occurred_at,observed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(3))",n.notificationId(),n.merchantOrderRef(),n.providerRef(),n.notificationDigest(),n.appId(),n.merchantId(),n.certificateSerial(),n.amountMinor(),n.currency(),state.name(),Timestamp.from(n.occurredAt()));}
        catch(DuplicateKeyException duplicate){String digest=jdbc.queryForObject("SELECT notification_digest FROM hz_payment_notification WHERE notification_id=?",String.class,n.notificationId());return n.notificationDigest().equals(digest)?WeChatPayCoordinator.NotificationDisposition.REPLAY:WeChatPayCoordinator.NotificationDisposition.CONFLICT;}
        boolean changed=advance(n.merchantOrderRef(),n.providerRef(),state,n.evidenceRef());
        if(changed&&state==WeChatPayCoordinator.State.PAID)append(n.merchantOrderRef(),BusinessEventLinker.Type.PAYMENT_CONFIRMED,"PAY-NOTICE-"+n.notificationId(),n.notificationDigest(),n.occurredAt());
        return WeChatPayCoordinator.NotificationDisposition.ACCEPTED;
    }
    public void consumeQueryBudget(String ref){if(jdbc.update("UPDATE hz_payment_coordination SET query_budget_remaining=query_budget_remaining-1,aggregate_version=aggregate_version+1,updated_at=CURRENT_TIMESTAMP(3) WHERE merchant_order_ref=? AND query_budget_remaining>0 AND query_deadline>=CURRENT_TIMESTAMP(3)",ref)!=1)throw new WeChatPayCoordinator.Conflict("PAYMENT_QUERY_BUDGET_EXHAUSTED");}
    @Transactional public WeChatPayCoordinator.Begin beginRefund(String ref,String refund,String digest,long amount,int queryBudget,Instant queryDeadline){
        var current=jdbc.query("SELECT amount_minor,refunded_minor,state_code FROM hz_payment_coordination WHERE merchant_order_ref=? FOR UPDATE",(rs,n)->new Object[]{rs.getLong(1),rs.getLong(2),rs.getString(3)},ref).stream().findFirst().orElseThrow(()->new WeChatPayCoordinator.Conflict("PAYMENT_NOT_FOUND"));
        try{String original=(String)current[2];if(!"PAID".equals(original)||amount<=0||queryBudget<=0||(long)current[1]+amount>(long)current[0])return WeChatPayCoordinator.Begin.CONFLICT;jdbc.update("INSERT INTO hz_payment_refund (refund_ref,merchant_order_ref,request_digest,amount_minor,state_code,original_payment_state,refund_query_budget_remaining,refund_query_deadline,created_at,updated_at) VALUES (?,?,?,?,'PENDING',?,?,?,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))",refund,ref,digest,amount,original,queryBudget,Timestamp.from(queryDeadline));jdbc.update("UPDATE hz_payment_coordination SET refunded_minor=refunded_minor+?,state_code='REFUND_PROCESSING',aggregate_version=aggregate_version+1,updated_at=CURRENT_TIMESTAMP(3) WHERE merchant_order_ref=?",amount,ref);return WeChatPayCoordinator.Begin.CREATED;}
        catch(DuplicateKeyException duplicate){WeChatPayCoordinator.RefundView old=requireRefund(refund);return digest.equals(old.requestDigest())&&ref.equals(old.merchantOrderRef())&&amount==old.amountMinor()?WeChatPayCoordinator.Begin.REPLAY:WeChatPayCoordinator.Begin.CONFLICT;}
    }
    @Transactional public WeChatPayCoordinator.RefundView refundResult(String order,String refund,WeChatPayPort.Result result){
        WeChatPayCoordinator.RefundView current=requireRefundForUpdate(refund);
        if(current.state()!=WeChatPayCoordinator.RefundState.PENDING&&current.state()!=WeChatPayCoordinator.RefundState.UNKNOWN)return current;
        String state=result instanceof WeChatPayPort.Accepted?"SUCCEEDED":result instanceof WeChatPayPort.Rejected?"REJECTED":"UNKNOWN";
        int changed=jdbc.update("UPDATE hz_payment_refund SET state_code=?,updated_at=CURRENT_TIMESTAMP(3) WHERE refund_ref=? AND merchant_order_ref=? AND state_code IN ('PENDING','UNKNOWN')",state,refund,order);
        if(changed==0)return requireRefund(refund);
        if("REJECTED".equals(state)){
            jdbc.update("UPDATE hz_payment_coordination SET refunded_minor=refunded_minor-?,state_code=?,aggregate_version=aggregate_version+1,updated_at=CURRENT_TIMESTAMP(3) WHERE merchant_order_ref=? AND state_code='REFUND_PROCESSING' AND refunded_minor>=?",current.amountMinor(),current.originalPaymentState().name(),order,current.amountMinor());
        }else if("SUCCEEDED".equals(state)){
            Long remaining=jdbc.queryForObject("SELECT amount_minor-refunded_minor FROM hz_payment_coordination WHERE merchant_order_ref=? FOR UPDATE",Long.class,order);
            settleSuccessfulRefund(order,remaining!=null&&remaining==0,"REFUND-"+refund);
            append(order,BusinessEventLinker.Type.REFUND_CONFIRMED,"PAY-REFUND-"+refund,current.requestDigest(),Instant.now());
        }else if(recovery!=null){recovery.schedule(refund,"WECHAT_REFUND_QUERY",require(order).version(),current.queryBudgetRemaining(),current.queryDeadline(),Instant.now());
        }
        return requireRefund(refund);
    }
    @Transactional public WeChatPayCoordinator.RefundView refundObservation(String refund,WeChatPayCoordinator.RefundState observed,String evidence){
        WeChatPayCoordinator.RefundView current=requireRefundForUpdate(refund);
        if(current.state()!=WeChatPayCoordinator.RefundState.UNKNOWN||observed==WeChatPayCoordinator.RefundState.UNKNOWN)return current;
        if(observed!=WeChatPayCoordinator.RefundState.SUCCEEDED&&observed!=WeChatPayCoordinator.RefundState.REJECTED)throw new WeChatPayCoordinator.Conflict("PAYMENT_REFUND_QUERY_STATE_INVALID");
        int changed=jdbc.update("UPDATE hz_payment_refund SET state_code=?,updated_at=CURRENT_TIMESTAMP(3) WHERE refund_ref=? AND state_code='UNKNOWN'",observed.name(),refund);
        if(changed==0)return requireRefund(refund);
        if(observed==WeChatPayCoordinator.RefundState.REJECTED){jdbc.update("UPDATE hz_payment_coordination SET refunded_minor=refunded_minor-?,state_code=?,evidence_ref=?,aggregate_version=aggregate_version+1,updated_at=CURRENT_TIMESTAMP(3) WHERE merchant_order_ref=? AND state_code IN ('REFUND_PROCESSING','UNKNOWN') AND refunded_minor>=?",current.amountMinor(),current.originalPaymentState().name(),evidence,current.merchantOrderRef(),current.amountMinor());}
        else{Long remaining=jdbc.queryForObject("SELECT amount_minor-refunded_minor FROM hz_payment_coordination WHERE merchant_order_ref=? FOR UPDATE",Long.class,current.merchantOrderRef());settleSuccessfulRefund(current.merchantOrderRef(),remaining!=null&&remaining==0,evidence);append(current.merchantOrderRef(),BusinessEventLinker.Type.REFUND_CONFIRMED,"PAY-REFUND-"+refund,current.requestDigest(),Instant.now());}
        return requireRefund(refund);
    }
    public void consumeRefundQueryBudget(String refund){if(jdbc.update("UPDATE hz_payment_refund SET refund_query_budget_remaining=refund_query_budget_remaining-1,updated_at=CURRENT_TIMESTAMP(3) WHERE refund_ref=? AND state_code='UNKNOWN' AND refund_query_budget_remaining>0 AND refund_query_deadline>=CURRENT_TIMESTAMP(3)",refund)!=1)throw new WeChatPayCoordinator.Conflict("PAYMENT_REFUND_QUERY_BUDGET_EXHAUSTED");}
    public WeChatPayCoordinator.RefundView requireRefund(String refund){return jdbc.query("SELECT refund_ref,merchant_order_ref,request_digest,amount_minor,state_code,original_payment_state,refund_query_budget_remaining,refund_query_deadline,updated_at FROM hz_payment_refund WHERE refund_ref=?",(rs,n)->new WeChatPayCoordinator.RefundView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),WeChatPayCoordinator.RefundState.valueOf(rs.getString(5)),WeChatPayCoordinator.State.valueOf(rs.getString(6)),rs.getInt(7),rs.getTimestamp(8).toInstant(),rs.getTimestamp(9).toInstant()),refund).stream().findFirst().orElseThrow(()->new WeChatPayCoordinator.Conflict("PAYMENT_REFUND_NOT_FOUND"));}
    private WeChatPayCoordinator.RefundView requireRefundForUpdate(String refund){return jdbc.query("SELECT refund_ref,merchant_order_ref,request_digest,amount_minor,state_code,original_payment_state,refund_query_budget_remaining,refund_query_deadline,updated_at FROM hz_payment_refund WHERE refund_ref=? FOR UPDATE",(rs,n)->new WeChatPayCoordinator.RefundView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),WeChatPayCoordinator.RefundState.valueOf(rs.getString(5)),WeChatPayCoordinator.State.valueOf(rs.getString(6)),rs.getInt(7),rs.getTimestamp(8).toInstant(),rs.getTimestamp(9).toInstant()),refund).stream().findFirst().orElseThrow(()->new WeChatPayCoordinator.Conflict("PAYMENT_REFUND_NOT_FOUND"));}
    private boolean advance(String ref,String provider,WeChatPayCoordinator.State next,String evidence){
        String allowedSources=switch(next){
            case NEW->"'NEW'";
            case PREPAY_CREATED->"'NEW','PREPAY_CREATED'";
            case PROCESSING->"'NEW','PREPAY_CREATED','PROCESSING','UNKNOWN'";
            case UNKNOWN->"'NEW','PREPAY_CREATED','PROCESSING','UNKNOWN'";
            case PAID->"'PREPAY_CREATED','PROCESSING','UNKNOWN'";
            case CLOSED->"'NEW','PREPAY_CREATED','PROCESSING','UNKNOWN'";
            case REFUND_PROCESSING->"'PAID','REFUND_PROCESSING'";
            case REFUNDED->"'REFUND_PROCESSING'";
            case REJECTED->"'NEW','PREPAY_CREATED','PROCESSING','UNKNOWN'";
        };
        return jdbc.update("UPDATE hz_payment_coordination SET provider_ref=COALESCE(?,provider_ref),state_code=?,evidence_ref=?,aggregate_version=aggregate_version+1,updated_at=CURRENT_TIMESTAMP(3) WHERE merchant_order_ref=? AND state_code IN ("+allowedSources+")",provider,next.name(),evidence,ref)==1;
    }
    private boolean settleSuccessfulRefund(String ref,boolean fullyRefunded,String evidence){
        return jdbc.update("UPDATE hz_payment_coordination SET state_code=?,evidence_ref=?,aggregate_version=aggregate_version+1,updated_at=CURRENT_TIMESTAMP(3) WHERE merchant_order_ref=? AND state_code='REFUND_PROCESSING'",fullyRefunded?"REFUNDED":"PAID",evidence,ref)==1;
    }
    private void appendPaymentConfirmed(String ref){
        WeChatPayCoordinator.View current=require(ref);
        append(ref,BusinessEventLinker.Type.PAYMENT_CONFIRMED,"PAY-CONFIRMED-"+current.requestDigest().substring(0,24),current.requestDigest(),Instant.now());
    }
    private void schedulePayment(String ref){if(recovery!=null){WeChatPayCoordinator.View current=require(ref);recovery.schedule(ref,"WECHAT_PAYMENT_QUERY",current.version(),current.queryBudgetRemaining(),current.queryDeadline(),Instant.now());}}
    private void append(String orderRef,BusinessEventLinker.Type type,String eventRef,String digest,Instant at){if(events!=null)events.append(new BusinessEventStore.EventDraft(orderRef,type,eventRef,digest,at));}
    static boolean allowed(WeChatPayCoordinator.State from,WeChatPayCoordinator.State to){if(from==to)return true;return switch(from){case NEW->java.util.Set.of(WeChatPayCoordinator.State.PREPAY_CREATED,WeChatPayCoordinator.State.PROCESSING,WeChatPayCoordinator.State.UNKNOWN,WeChatPayCoordinator.State.REJECTED,WeChatPayCoordinator.State.CLOSED).contains(to);case PREPAY_CREATED,PROCESSING,UNKNOWN->java.util.Set.of(WeChatPayCoordinator.State.PROCESSING,WeChatPayCoordinator.State.UNKNOWN,WeChatPayCoordinator.State.PAID,WeChatPayCoordinator.State.REJECTED,WeChatPayCoordinator.State.CLOSED).contains(to);case PAID->to==WeChatPayCoordinator.State.REFUND_PROCESSING;case REFUND_PROCESSING->java.util.Set.of(WeChatPayCoordinator.State.UNKNOWN,WeChatPayCoordinator.State.PAID,WeChatPayCoordinator.State.REFUNDED).contains(to);case CLOSED,REJECTED,REFUNDED->false;};}
}
