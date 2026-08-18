package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
@Profile("release-mysql")
class AdminA140DetailService {
    static final String SCHEMA_VERSION = "ADMIN_READ_V1";
    static final String PROJECTION_VERSION = "A140-DETAIL-1";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    AdminA140DetailService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    Optional<A140Detail> read(String orderRef, String role) {
        OrderFact order;
        try {
            order = jdbc.queryForObject("""
                    SELECT s.order_ref,s.quote_ref,o.order_state,s.final_amount_minor,s.currency,
                           q.phone_masked,q.operator_code,q.platform_product_ref,s.price_version_ref,
                           s.entitlement_snapshot,s.created_at,o.updated_at
                      FROM hz_release_order_snapshot s
                      JOIN hz_release_quote_snapshot q ON q.quote_ref=s.quote_ref
                      LEFT JOIN hz_order o ON o.order_ref=s.order_ref
                     WHERE s.order_ref=?
                    """, (rs, row) -> new OrderFact(
                    rs.getString("order_ref"), rs.getString("quote_ref"), unknown(rs.getString("order_state")),
                    rs.getLong("final_amount_minor"), rs.getString("currency"), rs.getString("phone_masked"),
                    rs.getString("operator_code"), rs.getString("platform_product_ref"), rs.getString("price_version_ref"),
                    parseEntitlement(rs.getString("entitlement_snapshot")), instant(rs.getTimestamp("created_at")),
                    instant(rs.getTimestamp("updated_at"))), orderRef);
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }

        PaymentFact payment = jdbc.query("""
                SELECT state_code,provider_ref,amount_minor,currency,refunded_minor,aggregate_version,updated_at
                  FROM hz_payment_coordination WHERE merchant_order_ref=?
                """, rs -> rs.next() ? new PaymentFact(unknown(rs.getString(1)), rs.getString(2),
                rs.getLong(3), rs.getString(4), rs.getLong(5), rs.getLong(6), instant(rs.getTimestamp(7)))
                : PaymentFact.unknown(), orderRef);
        TopupFact topup = jdbc.query("""
                SELECT state_code,provider_ref,aggregate_version,updated_at
                  FROM hz_topup_coordination WHERE merchant_order_ref=?
                """, rs -> rs.next() ? new TopupFact(unknown(rs.getString(1)), rs.getString(2), rs.getLong(3),
                instant(rs.getTimestamp(4))) : TopupFact.unknown(), orderRef);
        List<RefundFact> refunds = jdbc.query("""
                SELECT refund_ref,state_code,amount_minor,created_at,updated_at
                  FROM hz_payment_refund WHERE merchant_order_ref=? ORDER BY created_at,refund_ref
                """, (rs,row) -> new RefundFact(rs.getString(1),unknown(rs.getString(2)),rs.getLong(3),
                order.currency(),instant(rs.getTimestamp(4)),instant(rs.getTimestamp(5))), orderRef);

        LinkedHashMap<String,TimelineFact> timeline = new LinkedHashMap<>();
        jdbc.query("""
                SELECT event_ref,event_type,occurred_at,observed_at FROM hz_business_event_link
                 WHERE merchant_order_ref=? ORDER BY occurred_at,event_ref
                """, rs -> { while(rs.next()) timeline.put(rs.getString(1),new TimelineFact(rs.getString(1),rs.getString(2),
                "PROJECTED",instant(rs.getTimestamp(3)),instant(rs.getTimestamp(4)))); return null; }, orderRef);
        jdbc.query("""
                SELECT event_ref,event_type,occurred_at,dispatched_at FROM hz_business_event_outbox
                 WHERE merchant_order_ref=? ORDER BY occurred_at,event_ref
                """, rs -> { while(rs.next()) timeline.putIfAbsent(rs.getString(1),new TimelineFact(rs.getString(1),rs.getString(2),
                "OUTBOX",instant(rs.getTimestamp(3)),instant(rs.getTimestamp(4)))); return null; }, orderRef);
        ArrayList<TimelineFact> orderedTimeline=new ArrayList<>(timeline.values());
        orderedTimeline.sort(Comparator.comparing(TimelineFact::occurredAt).thenComparing(TimelineFact::eventRef));

        LinkedHashSet<String> caseRefs=new LinkedHashSet<>(jdbc.query(
                "SELECT case_ref FROM hz_customer_case WHERE related_order_ref=? ORDER BY case_ref",
                (rs,row)->rs.getString(1),orderRef));
        caseRefs.addAll(jdbc.query("SELECT case_ref FROM hz_customer_case_subject_binding WHERE merchant_order_ref=? ORDER BY case_ref",
                (rs,row)->rs.getString(1),orderRef));
        List<String> reconciliationRefs=jdbc.query(
                "SELECT reconciliation_ref FROM hz_reconciliation_case WHERE order_ref=? ORDER BY reconciliation_ref",
                (rs,row)->rs.getString(1),orderRef);
        return Optional.of(new A140Detail(SCHEMA_VERSION,PROJECTION_VERSION,"A140",role,order,payment,topup,
                List.copyOf(refunds),List.copyOf(orderedTimeline),List.copyOf(reconciliationRefs),List.copyOf(caseRefs)));
    }

    private EntitlementFact parseEntitlement(String value) {
        try {
            if (value == null) return null;
            var node=json.readTree(value);
            if (node != null && node.isTextual()) node=json.readTree(node.asText());
            if (node == null || !node.isObject()
                    || !text(node,"productRef") || !text(node,"productType")
                    || !text(node,"displayName") || !text(node,"benefitText")) return null;
            return new EntitlementFact(node.get("productRef").textValue(),node.get("productType").textValue(),
                    node.get("displayName").textValue(),node.get("benefitText").textValue(),
                    decimal(node,"denominationBdt"),longValue(node,"dataAllowanceMb"),
                    integer(node,"voiceMinutes"),integer(node,"smsCount"),optionalText(node,"validityText"));
        }
        catch (Exception invalid) { return null; }
    }
    private static boolean text(com.fasterxml.jackson.databind.JsonNode node,String field) {
        return node.has(field) && node.get(field).isTextual() && !node.get(field).textValue().isBlank();
    }
    private static String optionalText(com.fasterxml.jackson.databind.JsonNode node,String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        if (!node.get(field).isTextual()) throw new IllegalArgumentException("invalid entitlement field");
        return node.get(field).textValue();
    }
    private static BigDecimal decimal(com.fasterxml.jackson.databind.JsonNode node,String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        if (!node.get(field).isNumber()) throw new IllegalArgumentException("invalid entitlement field");
        return node.get(field).decimalValue();
    }
    private static Long longValue(com.fasterxml.jackson.databind.JsonNode node,String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        if (!node.get(field).canConvertToLong()) throw new IllegalArgumentException("invalid entitlement field");
        return node.get(field).longValue();
    }
    private static Integer integer(com.fasterxml.jackson.databind.JsonNode node,String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        if (!node.get(field).canConvertToInt()) throw new IllegalArgumentException("invalid entitlement field");
        return node.get(field).intValue();
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static String unknown(String value) { return value == null || value.isBlank() ? "UNKNOWN" : value; }

    record A140Detail(String schemaVersion,String projectionVersion,String pageId,String role,OrderFact order,
                      PaymentFact payment,TopupFact topup,List<RefundFact> refunds,List<TimelineFact> timeline,
                      List<String> reconciliationRefs,List<String> customerCaseRefs) {}
    record OrderFact(String orderRef,String quoteRef,String state,long amountMinor,String currency,String phoneMasked,
                     String operatorCode,String productRef,String priceVersionRef,EntitlementFact entitlement,
                     Instant createdAt,Instant updatedAt) {}
    record EntitlementFact(String productRef,String productType,String displayName,String benefitText,
                           BigDecimal denominationBdt,Long dataAllowanceMb,Integer voiceMinutes,Integer smsCount,
                           String validityText) {}
    record PaymentFact(String state,String providerRef,Long amountMinor,String currency,Long refundedMinor,Long version,Instant updatedAt) {
        static PaymentFact unknown(){ return new PaymentFact("UNKNOWN",null,null,null,null,null,null); }
    }
    record TopupFact(String state,String providerRef,Long version,Instant updatedAt) {
        static TopupFact unknown(){ return new TopupFact("UNKNOWN",null,null,null); }
    }
    record RefundFact(String refundRef,String state,long amountMinor,String currency,Instant createdAt,Instant updatedAt) {}
    record TimelineFact(String eventRef,String eventType,String source,Instant occurredAt,Instant projectedOrDispatchedAt) {}
}
