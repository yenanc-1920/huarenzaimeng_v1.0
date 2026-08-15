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
        assertThat(result.role()).isEqualTo("CONTENT");
        assertThat((List<?>) result.items()).hasSize(1);
        assertThat(((AdminReadService.A120Item) ((List<?>) result.items()).get(0)).visibilityLabel()).isEqualTo("可展示");
    }

    @Test void catalogProjectionIsFinanceReadOnlyAndBounded() {
        when(mapper.selectAdminCatalog()).thenReturn(List.of(Map.ofEntries(
                Map.entry("catalog_version", 7L), Map.entry("operator_code", "BD-OP"), Map.entry("product_ref", "P-1"),
                Map.entry("denomination_ref", "D-100"), Map.entry("item_kind", "PRESET"), Map.entry("amount_minor", 10000L),
                Map.entry("currency", "BDT"), Map.entry("item_state", "ACTIVE"), Map.entry("catalog_ref", "CAT-7"),
                Map.entry("catalog_state", "ACTIVE"), Map.entry("expires_at", Timestamp.from(now.plusSeconds(86400))),
                Map.entry("batch_ref", "BATCH-7"), Map.entry("batch_state", "ACTIVE"), Map.entry("qualification_known", 1))));
        AdminReadService.AdminProjection result = service.read("A130");
        assertThat(result.role()).isEqualTo("FIN");
        assertThat(((AdminReadService.A130FinanceItem) ((List<?>) result.items()).get(0)).denominationLabel()).isEqualTo("BDT 100.00");
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
        assertThat(service.read("A100")).isNull();
        assertThat(service.read("A110")).isNull();
    }
}
