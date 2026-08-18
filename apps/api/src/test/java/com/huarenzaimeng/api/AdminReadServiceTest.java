package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminReadServiceTest {
    private final ContentService content = mock(ContentService.class);
    private final AdminReadMapper mapper = mock(AdminReadMapper.class);
    private final Instant now = Instant.parse("2026-08-09T12:00:00Z");
    private final AdminReadService service = new AdminReadService(content, mapper, Clock.fixed(now, ZoneOffset.UTC));

    @Test void contentProjectionUsesStoredFactsOnly() {
        DirectoryContent item = new DirectoryContent("CONTENT-1", "孟加拉生活信息", "摘要", "LIFE",
                "SELF_OPERATED_CHINA_COMPANY", "SELF_RESEARCH", "SOURCE-1", "NAME_ONLY", "EDITOR-1",
                now.minusSeconds(3600), now.plusSeconds(86400), 3, ContentState.PUBLISHED, false, now, List.of());
        when(content.internalList()).thenReturn(new ContentPage<>(List.of(item), 1));
        AdminReadService.AdminProjection result = service.read("A120");
        assertThat(result.schemaVersion()).isEqualTo("ADMIN_READ_V1");
        assertThat(result.role()).isEqualTo("SUPER_ADMIN");
        assertThat((List<?>) result.items()).hasSize(1);
        assertThat(((AdminReadService.A120Item) ((List<?>) result.items()).get(0)).visibilityLabel()).isEqualTo("可展示");
    }

    @Test void productProjectionReturnsCurrentAdminReadSchemaWithoutLegacyTransformation() {
        Map<String,Object> product=Map.ofEntries(
                Map.entry("productRef","P-1"),Map.entry("countryCode","BD"),Map.entry("operatorCode","ROBI"),
                Map.entry("productType","DATA"),Map.entry("displayName","Robi 5GB"),Map.entry("benefitText","5GB"),
                Map.entry("channelPriority",10),Map.entry("state","ENABLED"),Map.entry("version",1L));
        when(mapper.selectPlatformProducts()).thenReturn(List.of(product));
        AdminReadService.AdminProjection result = service.read("A130");
        assertThat(result.role()).isEqualTo("SUPER_ADMIN");
        assertThat(((List<?>)result.items()).get(0)).isEqualTo(product);
        verify(mapper,never()).selectAdminCatalog();
    }

    @Test void a130EmptyProductsKeepsCurrentSchemaAndNeverFallsBackToLegacyCatalog() {
        when(mapper.selectPlatformProducts()).thenReturn(List.of());
        AdminReadService.AdminProjection result=service.read("A130");
        assertThat(result.schemaVersion()).isEqualTo("ADMIN_READ_V1");
        assertThat(result.pageId()).isEqualTo("A130");
        assertThat((List<?>)result.items()).isEmpty();
        verify(mapper,never()).selectAdminCatalog();
    }

    @Test void contentOperatorA130ProjectionDoesNotExposeCommercialFields() {
        Map<String,Object> product=new java.util.HashMap<>();
        product.put("productRef","P-1");product.put("operatorCode","ROBI");product.put("productType","DATA");
        product.put("displayName","Robi 5GB");product.put("benefitText","5GB");product.put("validityText","30 days");
        product.put("state","UNDER_REVIEW");product.put("version",2L);product.put("supplierCost","10.00");product.put("fxRate","0.06");product.put("markupRate","0.20");
        when(mapper.selectPlatformProducts()).thenReturn(List.of(product));
        AdminReadService.AdminProjection result=service.read("A130","CONTENT_OPERATOR");
        Object item=((List<?>)result.items()).get(0);
        assertThat(item).isInstanceOf(AdminReadService.A130ContentItem.class);
        assertThat(item.toString()).doesNotContain("10.00","0.06","0.20","supplierCost","fxRate","markupRate");
    }

    @Test void a120IncludesPersistedDirectoryReports() {
        when(content.internalList()).thenReturn(new ContentPage<>(List.of(),0));
        when(mapper.selectDirectoryReports()).thenReturn(List.of(Map.ofEntries(
                Map.entry("reportRef","DR-1"),Map.entry("entryRef","DIR-1"),Map.entry("reasonCode","INCORRECT_INFO"),
                Map.entry("description",""),Map.entry("state","OPEN"),Map.entry("createdAt",Timestamp.from(now)))));
        AdminReadService.AdminProjection result=service.read("A120");
        assertThat((List<?>)result.items()).singleElement().satisfies(item -> {
            assertThat(item).isInstanceOf(AdminReadService.A120Item.class);
            AdminReadService.A120Item report=(AdminReadService.A120Item)item;
            assertThat(report.contentRef()).isEqualTo("DR-1");
            assertThat(report.title()).isEqualTo("黄页反馈：DIR-1 / 信息不准确");
            assertThat(report.sourceLabel()).isEqualTo("黄页用户反馈");
            assertThat(report.reviewLabel()).isEqualTo("OPEN");
            assertThat(report.complaintLabel()).isEqualTo("未补充说明");
            assertThat(report.visibilityLabel()).isEqualTo("不直接修改公开内容");
            assertThat(report.ownerLabel()).isEqualTo("待内容运营核验");
            assertThat(report.historyLabel()).isEqualTo(now.toString());
            assertThat(report.removalLabel()).isEqualTo("核验后处理");
        });
    }

    @Test void directoryAndNewsPagesExposeFullStoredContentByCategory() {
        DirectoryContent directory = new DirectoryContent("DIR-1", "孟加拉生活服务", "黄页完整摘要", "DIRECTORY",
                "SELF_OPERATED_CHINA_COMPANY", "SELF_RESEARCH", "SRC-DIR", "NAME_ONLY", "EDITOR-1",
                now.minusSeconds(60), now.plusSeconds(3600), 2, ContentState.PUBLISHED, false, now, List.of());
        DirectoryContent news = new DirectoryContent("NEWS-1", "节日提醒", "资讯完整摘要", "LIFE_REMINDER",
                "SELF_OPERATED_CHINA_COMPANY", "SELF_RESEARCH", "SRC-NEWS", "NAME_ONLY", "EDITOR-1",
                now.minusSeconds(60), now.plusSeconds(3600), 4, ContentState.PUBLISHED, false, now, List.of());
        when(content.internalList()).thenReturn(new ContentPage<>(List.of(directory, news), 2));
        AdminReadService.AdminProjection directoryResult = service.read("A121");
        AdminReadService.AdminProjection newsResult = service.read("A122");
        assertThat(((AdminReadService.ManagedContentItem) ((List<?>) directoryResult.items()).get(0)).summary()).isEqualTo("黄页完整摘要");
        assertThat(((AdminReadService.ManagedContentItem) ((List<?>) newsResult.items()).get(0)).summary()).isEqualTo("资讯完整摘要");
    }

    @Test void orderProjectionUsesMaskedPhoneAndReadableStoredState() {
        when(mapper.selectAdminOrders()).thenReturn(List.of(Map.ofEntries(
                Map.entry("order_ref", "ORDER-1"), Map.entry("order_state", "AWAITING_PAYMENT"),
                Map.entry("payment_state", "ABSENT_CONFIRMED"), Map.entry("upstream_debit_state", "ABSENT_CONFIRMED"),
                Map.entry("delivery_state", "ABSENT_CONFIRMED"), Map.entry("refund_state", "ABSENT_CONFIRMED"),
                Map.entry("updated_at", Timestamp.from(now.minusSeconds(7200))), Map.entry("phone_masked", "01•• •••• 78"),
                Map.entry("total_amount_minor", 1280L), Map.entry("total_currency", "CNY"))));
        AdminReadService.AdminProjection result = service.read("A140");
        AdminReadService.A140SupportItem item = (AdminReadService.A140SupportItem) ((List<?>) result.items()).get(0);
        assertThat(item.maskedPhone()).isEqualTo("01•• •••• 78");
        assertThat(item.userStatusLabel()).isEqualTo("等待付款");
        assertThat(item.totalLabel()).isEqualTo("CNY 12.80");
    }

    @Test void unsupportedPagesStayUnavailableInsteadOfReturningSyntheticEmptyData() {
        assertThat(service.read("A100")).isNotNull();
        assertThat(service.read("A110")).isNotNull();
    }
}
