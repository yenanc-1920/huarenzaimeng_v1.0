package com.huarenzaimeng.api;
import com.huarenzaimeng.core.BusinessEventLinker;
import java.time.Duration;
import java.time.Instant;
public interface BusinessEventStore {
    void append(String orderRef, BusinessEventLinker.Type type, String eventRef, String eventDigest, Instant occurredAt);
    default void append(EventDraft draft) { append(draft.orderRef(),draft.type(),draft.eventRef(),draft.eventDigest(),draft.occurredAt()); }
    void bindSupportCase(String caseRef,String orderRef);
    default int dispatchBatch(String leaseOwner,int limit,Instant now,Duration leaseDuration) { throw new UnsupportedOperationException("OUTBOX_DISPATCH_UNAVAILABLE"); }

    record EventDraft(String orderRef, BusinessEventLinker.Type type, String eventRef, String eventDigest, Instant occurredAt) {
        public EventDraft {
            if(orderRef==null||orderRef.isBlank()||type==null||eventRef==null||eventRef.isBlank()||
                    eventDigest==null||eventDigest.isBlank()||occurredAt==null) throw new IllegalArgumentException("BUSINESS_EVENT_DRAFT_INVALID");
        }
    }
}
