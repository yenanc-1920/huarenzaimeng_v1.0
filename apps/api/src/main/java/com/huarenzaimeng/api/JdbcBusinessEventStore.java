package com.huarenzaimeng.api;

import com.huarenzaimeng.core.BusinessEventLinker;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
@Profile("release-mysql")
public class JdbcBusinessEventStore implements BusinessEventStore {
    private final JdbcTemplate jdbc;
    private final BusinessEventLinker linker = new BusinessEventLinker();
    public JdbcBusinessEventStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void append(String orderRef, BusinessEventLinker.Type type, String eventRef, String eventDigest, Instant occurredAt) {
        EventDraft draft=new EventDraft(orderRef,type,eventRef,eventDigest,occurredAt);
        try {
            jdbc.update("INSERT INTO hz_business_event_outbox (outbox_ref,event_ref,merchant_order_ref,event_type,event_digest,occurred_at,created_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP(3))",
                    "OUT-" + UUID.randomUUID(), draft.eventRef(), draft.orderRef(), draft.type().name(), draft.eventDigest(), Timestamp.from(draft.occurredAt()));
        } catch (DuplicateKeyException duplicate) {
            Integer same = jdbc.queryForObject("SELECT COUNT(*) FROM hz_business_event_outbox WHERE event_ref=? AND merchant_order_ref=? AND event_type=? AND event_digest=? AND occurred_at=?",
                    Integer.class, draft.eventRef(), draft.orderRef(), draft.type().name(), draft.eventDigest(), Timestamp.from(draft.occurredAt()));
            if (same == null || same != 1) throw new IllegalArgumentException("BUSINESS_EVENT_IDEMPOTENCY_CONFLICT");
        }
    }

    @Override public void bindSupportCase(String caseRef, String orderRef) {
        List<String> subjects = jdbc.query("SELECT project_subject_ref FROM hz_order WHERE order_ref=?",(rs,n)->rs.getString(1), orderRef);
        if(subjects.size()!=1||subjects.get(0)==null||subjects.get(0).isBlank()) throw new IllegalArgumentException("SUPPORT_CASE_ORDER_SUBJECT_MISMATCH");
        jdbc.update("INSERT INTO hz_customer_case_subject_binding (case_ref,merchant_order_ref,order_subject_ref,created_at) VALUES (?,?,?,CURRENT_TIMESTAMP(3))",caseRef,orderRef,subjects.get(0));
    }

    @Override @Transactional public int dispatchBatch(String leaseOwner, int limit, Instant now, Duration leaseDuration) {
        if(leaseOwner==null||!leaseOwner.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,63}")||limit<1||limit>100||now==null||leaseDuration==null||leaseDuration.isNegative()||leaseDuration.isZero())
            throw new IllegalArgumentException("OUTBOX_DISPATCH_ARGUMENT_INVALID");
        Timestamp claimedAt=Timestamp.from(now), leaseUntil=Timestamp.from(now.plus(leaseDuration));
        List<String> candidates=jdbc.query("SELECT outbox_ref FROM hz_business_event_outbox WHERE dispatched_at IS NULL AND (lease_until IS NULL OR lease_until<?) ORDER BY created_at,outbox_ref LIMIT ?",(rs,n)->rs.getString(1),claimedAt,limit);
        int claimed=0;
        for(String ref:candidates) claimed+=jdbc.update("UPDATE hz_business_event_outbox SET lease_owner=?,lease_until=?,delivery_attempts=delivery_attempts+1 WHERE outbox_ref=? AND dispatched_at IS NULL AND (lease_until IS NULL OR lease_until<?)",leaseOwner,leaseUntil,ref,claimedAt);
        if(claimed==0)return 0;
        List<OutboxRow> rows=jdbc.query("SELECT outbox_ref,event_ref,merchant_order_ref,event_type,event_digest,occurred_at FROM hz_business_event_outbox WHERE lease_owner=? AND lease_until=? AND dispatched_at IS NULL ORDER BY created_at,outbox_ref",
                (rs,n)->new OutboxRow(rs.getString(1),rs.getString(2),rs.getString(3),BusinessEventLinker.Type.valueOf(rs.getString(4)),rs.getString(5),rs.getTimestamp(6).toInstant()),leaseOwner,leaseUntil);
        for(OutboxRow row:rows){
            project(row);
            if(jdbc.update("UPDATE hz_business_event_outbox SET dispatched_at=?,lease_owner=NULL,lease_until=NULL,last_error_code=NULL WHERE outbox_ref=? AND lease_owner=? AND lease_until=? AND dispatched_at IS NULL",claimedAt,row.outboxRef(),leaseOwner,leaseUntil)!=1)
                throw new IllegalStateException("OUTBOX_LEASE_LOST");
        }
        return rows.size();
    }

    private void project(OutboxRow row){
        Integer orderCount=jdbc.queryForObject("SELECT COUNT(*) FROM hz_order WHERE order_ref=?",Integer.class,row.orderRef());
        if(orderCount==null||orderCount!=1)throw new IllegalArgumentException("BUSINESS_EVENT_ORDER_NOT_FOUND");
        BusinessEventLinker.Event child=new BusinessEventLinker.Event(row.eventRef(),row.orderRef(),row.type(),row.occurredAt());
        BusinessEventLinker.Link link;
        if(row.type()==BusinessEventLinker.Type.ORDER_CREATED) link=linker.link(child,null);
        else {
            List<BusinessEventLinker.Event> candidates=jdbc.query("SELECT event_ref,merchant_order_ref,event_type,occurred_at FROM hz_business_event_link WHERE merchant_order_ref=? ORDER BY occurred_at DESC,event_ref DESC",
                    (rs,n)->new BusinessEventLinker.Event(rs.getString(1),rs.getString(2),BusinessEventLinker.Type.valueOf(rs.getString(3)),rs.getTimestamp(4).toInstant()),row.orderRef());
            link=candidates.stream().map(parent->{try{return linker.link(child,parent);}catch(IllegalArgumentException ignored){return null;}}).filter(Objects::nonNull).findFirst().orElseThrow(()->new IllegalArgumentException("BUSINESS_EVENT_PARENT_NOT_FOUND"));
        }
        try {
            jdbc.update("INSERT INTO hz_business_event_link (event_ref,parent_event_ref,merchant_order_ref,event_type,parent_event_type,event_digest,occurred_at,observed_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP(3))",
                    link.eventRef(),link.parentEventRef(),link.orderRef(),link.type().name(),link.parentType()==null?null:link.parentType().name(),row.eventDigest(),Timestamp.from(link.occurredAt()));
        } catch(DuplicateKeyException duplicate){
            Integer same=jdbc.queryForObject("SELECT COUNT(*) FROM hz_business_event_link WHERE event_ref=? AND merchant_order_ref=? AND event_type=? AND event_digest=?",Integer.class,row.eventRef(),row.orderRef(),row.type().name(),row.eventDigest());
            if(same==null||same!=1)throw new IllegalArgumentException("BUSINESS_EVENT_PROJECTION_CONFLICT");
        }
    }
    private record OutboxRow(String outboxRef,String eventRef,String orderRef,BusinessEventLinker.Type type,String eventDigest,Instant occurredAt){}
}
