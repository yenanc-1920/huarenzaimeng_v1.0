package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOrderSandboxContractTest {
    private final AdminReadMapper mapper = mock(AdminReadMapper.class);
    private final AdminOrderSandboxService service = new AdminOrderSandboxService(mapper);

    @Test void storedOrderWithoutSandboxFactIsExplicitlyUnavailable() {
        when(mapper.selectAdminOrderDetail("ORDER-1")).thenReturn(order());
        AdminOrderSandboxService.OrderSandboxProjection result = service.read("ORDER-1");
        assertThat(result.sandboxTopup().availability()).isEqualTo("NOT_AVAILABLE");
        assertThat(result.sandboxTopup().providerTransactionRef()).isNull();
    }

    @Test void storedSandboxFactIsReturnedWithoutEvidenceOrSecretFields() {
        when(mapper.selectAdminOrderDetail("ORDER-1")).thenReturn(order());
        when(mapper.selectLatestAdminSandboxFact("ORDER-1")).thenReturn(Map.of(
                "provider_fact_ref", "TX-1", "fact_type", "TOPUP_ACCEPTED",
                "observed_at", Timestamp.from(Instant.parse("2026-08-15T01:00:00Z"))));
        AdminOrderSandboxService.OrderSandboxProjection result = service.read("ORDER-1");
        assertThat(result.sandboxTopup().availability()).isEqualTo("OBSERVED");
        assertThat(result.sandboxTopup().providerStatusCode()).isEqualTo("TOPUP_ACCEPTED");
    }

    @Test void controllerRequiresTrustedSuperAdminBeforeReading() {
        AdminOrderSandboxController controller = new AdminOrderSandboxController(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ResponseEntity<?> response = controller.read("ORDER-1", request);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(mapper, never()).selectAdminOrderDetail("ORDER-1");
    }

    @Test void trustedReadIsGetOnlyAndNoStore() {
        when(mapper.selectAdminOrderDetail("ORDER-1")).thenReturn(order());
        AdminOrderSandboxController controller = new AdminOrderSandboxController(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminSessionFilter.TRUSTED_ROLE, "SUPER_ADMIN");
        ResponseEntity<?> response = controller.read("ORDER-1", request);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test void mysqlLocalDateTimeIsAcceptedForOrderAndSandboxObservation() {
        Map<String, Object> order = new java.util.HashMap<>(order());
        order.put("updated_at", LocalDateTime.of(2026, 8, 15, 0, 0));
        when(mapper.selectAdminOrderDetail("ORDER-1")).thenReturn(order);
        when(mapper.selectLatestAdminSandboxFact("ORDER-1")).thenReturn(Map.of(
                "provider_fact_ref", "TX-1", "fact_type", "TOPUP_ACCEPTED",
                "observed_at", LocalDateTime.of(2026, 8, 15, 1, 0)));

        AdminOrderSandboxService.OrderSandboxProjection result = service.read("ORDER-1");

        assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-08-15T00:00:00Z"));
        assertThat(result.sandboxTopup().observedAt()).isEqualTo(Instant.parse("2026-08-15T01:00:00Z"));
    }

    private static Map<String, Object> order() {
        return Map.ofEntries(Map.entry("order_ref", "ORDER-1"), Map.entry("order_state", "SUBMITTED"),
                Map.entry("payment_state", "CONFIRMED"), Map.entry("delivery_state", "PENDING"),
                Map.entry("refund_state", "ABSENT_CONFIRMED"), Map.entry("phone_masked", "01•• •••• 78"),
                Map.entry("total_amount_minor", 1280L), Map.entry("total_currency", "BDT"),
                Map.entry("updated_at", Timestamp.from(Instant.parse("2026-08-15T00:00:00Z"))));
    }
}
