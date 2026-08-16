package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminReadControllerContractTest {
    private final AdminReadService service=mock(AdminReadService.class);
    private final AdminReadController controller=new AdminReadController(service);

    @Test void a110AllowsFinanceCsAndSuperAndEchoesTrustedRole() {
        Map<String,Object> item=Map.ofEntries(
                Map.entry("reconciliationRef","REC-1"),Map.entry("orderRef","ORDER-1"),
                Map.entry("differenceType","PAYMENT_TOPUP_PENDING"),Map.entry("amount",new BigDecimal("6.80")),
                Map.entry("currency","CNY"),Map.entry("state","OPEN"),
                Map.entry("discoveredAt",Timestamp.from(Instant.parse("2026-08-16T00:00:00Z"))),
                Map.entry("updatedAt",Timestamp.from(Instant.parse("2026-08-16T00:00:00Z"))));
        when(service.read("A110")).thenReturn(new AdminReadService.AdminProjection(
                "ADMIN_READ_V1","RECONCILIATION-1","A110","SUPER_ADMIN",List.of(item)));
        for(String role:List.of("FIN","CS","SUPER_ADMIN")) {
            MockHttpServletRequest request=new MockHttpServletRequest();
            request.setAttribute(AdminSessionFilter.TRUSTED_ROLE,role);
            var response=controller.read("A110",request);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(((AdminReadService.AdminProjection)response.getBody()).role()).isEqualTo(role);
            assertThat(((AdminReadService.AdminProjection)response.getBody()).items()).isEqualTo(List.of(item));
        }
    }

    @Test void a110RejectsContentRoleWithoutReadingService() {
        MockHttpServletRequest request=new MockHttpServletRequest();
        request.setAttribute(AdminSessionFilter.TRUSTED_ROLE,"CONTENT");
        assertThat(controller.read("A110",request).getStatusCode().value()).isEqualTo(403);
    }

    @Test void a120DirectoryReportUsesTheSameConsumableItemContract() {
        AdminReadService.A120Item report=new AdminReadService.A120Item(
                "DR-1","黄页反馈：DIR-1 / 信息不准确","黄页用户反馈","OPEN","未补充说明",
                "不直接修改公开内容","待内容运营核验","2026-08-16T00:00:00Z","核验后处理");
        when(service.read("A120")).thenReturn(new AdminReadService.AdminProjection(
                "ADMIN_READ_V1","CONTENT-V0-R1","A120","SUPER_ADMIN",List.of(report)));
        MockHttpServletRequest request=new MockHttpServletRequest();
        request.setAttribute(AdminSessionFilter.TRUSTED_ROLE,"CONTENT");
        var response=controller.read("A120",request);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        AdminReadService.AdminProjection body=(AdminReadService.AdminProjection)response.getBody();
        assertThat(body.role()).isEqualTo("CONTENT");
        assertThat((List<?>)body.items()).hasSize(1);
        assertThat(((List<?>)body.items()).get(0)).isEqualTo(report);
    }
}
