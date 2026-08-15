package com.huarenzaimeng.api;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@Profile("release-mysql")
class AdminOrderSandboxService {
    private final AdminReadMapper mapper;

    AdminOrderSandboxService(AdminReadMapper mapper) { this.mapper = mapper; }

    OrderSandboxProjection read(String orderRef) {
        if (orderRef == null || !orderRef.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) return null;
        Map<String, Object> order = mapper.selectAdminOrderDetail(orderRef);
        if (order == null) return null;
        Map<String, Object> fact = mapper.selectLatestAdminSandboxFact(orderRef);
        SandboxTopupProjection sandbox = fact == null || fact.isEmpty()
                ? new SandboxTopupProjection("NOT_AVAILABLE", null, null, null)
                : new SandboxTopupProjection("OBSERVED", text(fact, "provider_fact_ref"),
                    text(fact, "fact_type"), instant(fact.get("observed_at")));
        return new OrderSandboxProjection("ADMIN_ORDER_SANDBOX_V1", text(order, "order_ref"),
                text(order, "order_state"), text(order, "payment_state"),
                text(order, "delivery_state"), text(order, "refund_state"),
                text(order, "phone_masked"), ((Number) order.get("total_amount_minor")).longValue(),
                text(order, "total_currency"), instant(order.get("updated_at")), sandbox);
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalStateException("ADMIN_ORDER_FIELD_UNAVAILABLE");
        return text;
    }

    private static Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("ADMIN_ORDER_TIME_UNAVAILABLE");
    }

    record OrderSandboxProjection(String schemaVersion, String orderRef, String stateCode,
                                  String paymentState, String deliveryState, String refundState,
                                  String maskedTarget, long totalMinor, String currency,
                                  Instant updatedAt, SandboxTopupProjection sandboxTopup) {}
    record SandboxTopupProjection(String availability, String providerTransactionRef,
                                  String providerStatusCode, Instant observedAt) {}
}
