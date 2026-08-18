package com.huarenzaimeng.core;

import java.time.Instant;
import java.util.*;

/** Validates the V1 causal chain without asserting that an external event actually occurred. */
public final class BusinessEventLinker {
    private static final Map<Type,Set<Type>> ALLOWED_PARENTS=Map.of(
            Type.ORDER_CREATED,Set.of(),
            Type.PAYMENT_CONFIRMED,Set.of(Type.ORDER_CREATED),
            Type.TOPUP_SUBMITTED,Set.of(Type.PAYMENT_CONFIRMED),
            Type.TOPUP_DELIVERED,Set.of(Type.TOPUP_SUBMITTED),
            Type.REFUND_CONFIRMED,Set.of(Type.PAYMENT_CONFIRMED),
            Type.RECONCILIATION_OPENED,Set.of(Type.PAYMENT_CONFIRMED,Type.TOPUP_SUBMITTED,Type.TOPUP_DELIVERED,Type.REFUND_CONFIRMED),
            Type.SUPPORT_CASE_OPENED,Set.of(Type.ORDER_CREATED,Type.PAYMENT_CONFIRMED,Type.TOPUP_SUBMITTED,Type.TOPUP_DELIVERED,Type.REFUND_CONFIRMED,Type.RECONCILIATION_OPENED),
            Type.SUPPORT_CASE_UPDATED,Set.of(Type.SUPPORT_CASE_OPENED,Type.SUPPORT_CASE_UPDATED));
    public Link link(Event child,Event parent){Objects.requireNonNull(child);if(child.type()==Type.ORDER_CREATED){if(parent!=null)throw new IllegalArgumentException("ROOT_EVENT_MUST_NOT_HAVE_PARENT");return new Link(child.eventRef(),null,child.orderRef(),child.type(),null,child.occurredAt());}Objects.requireNonNull(parent,"PARENT_REQUIRED");if(!child.orderRef().equals(parent.orderRef()))throw new IllegalArgumentException("CROSS_ORDER_EVENT_LINK");if(!ALLOWED_PARENTS.get(child.type()).contains(parent.type()))throw new IllegalArgumentException("EVENT_CAUSALITY_INVALID");if(child.occurredAt().isBefore(parent.occurredAt()))throw new IllegalArgumentException("EVENT_TIME_ORDER_INVALID");return new Link(child.eventRef(),parent.eventRef(),child.orderRef(),child.type(),parent.type(),child.occurredAt());}
    public enum Type{ORDER_CREATED,PAYMENT_CONFIRMED,TOPUP_SUBMITTED,TOPUP_DELIVERED,REFUND_CONFIRMED,RECONCILIATION_OPENED,SUPPORT_CASE_OPENED,SUPPORT_CASE_UPDATED}
    public record Event(String eventRef,String orderRef,Type type,Instant occurredAt){public Event{if(eventRef==null||eventRef.isBlank()||orderRef==null||orderRef.isBlank()||type==null||occurredAt==null)throw new IllegalArgumentException("BUSINESS_EVENT_INVALID");}}
    public record Link(String eventRef,String parentEventRef,String orderRef,Type type,Type parentType,Instant occurredAt){}
}
