package com.huarenzaimeng.api;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Profile("release-mysql")
class AdminReadService {
    private final ContentService content;
    private final AdminReadMapper mapper;
    private final Clock clock;

    AdminReadService(ContentService content, AdminReadMapper mapper, Clock clock) {
        this.content = content; this.mapper = mapper; this.clock = clock;
    }

    AdminProjection read(String pageId) {
        return switch (pageId) {
            case "A120" -> contentProjection();
            case "A130" -> catalogProjection();
            case "A140" -> orderProjection();
            default -> null;
        };
    }

    private AdminProjection contentProjection() {
        List<DirectoryContent> source = content.internalList().items();
        Instant now = clock.instant();
        List<A120Item> items = source.stream().map(value -> new A120Item(
                value.contentRef(), value.title(), sourceLabel(value), reviewLabel(value),
                value.complaintPending() ? "存在待查投诉" : "无待查投诉", visibilityLabel(value),
                present(value.verifiedBy()) ? "核验人：" + value.verifiedBy() : "内容职责待复核",
                "历史记录 " + value.auditTrail().size() + " 条", removalLabel(value, now))).toList();
        long version = source.stream().mapToLong(DirectoryContent::version).max().orElse(0);
        return new AdminProjection("ADMIN_READ_V1", "CONTENT-V" + version, "A120", "CONTENT", items);
    }

    private AdminProjection catalogProjection() {
        List<Map<String, Object>> source = mapper.selectAdminCatalog();
        List<A130FinanceItem> items = source.stream().map(row -> {
            String currency = text(row, "currency");
            String amount = money(number(row, "amount_minor"), currency);
            String state = text(row, "item_state");
            return new A130FinanceItem(text(row, "catalog_ref") + "/" + text(row, "product_ref") + "/" + text(row, "denomination_ref"),
                    text(row, "product_ref"), amount, currency, "当前目录价格 " + amount,
                    "有效至 " + instant(row, "expires_at"), "支持批次 " + text(row, "batch_ref"),
                    "ACTIVE".equals(state) && bool(row, "qualification_known") ? "当前有效" : "暂不可用");
        }).toList();
        long version = source.stream().mapToLong(row -> number(row, "catalog_version")).max().orElse(0);
        return new AdminProjection("ADMIN_READ_V1", "CATALOG-V" + version, "A130", "FIN", items);
    }

    private AdminProjection orderProjection() {
        List<Map<String, Object>> source = mapper.selectAdminOrders();
        Instant now = clock.instant();
        List<A140SupportItem> items = source.stream().map(row -> {
            Instant updated = timestamp(row, "updated_at");
            return new A140SupportItem(text(row, "order_ref"), text(row, "phone_masked"),
                    orderStateLabel(text(row, "order_state")), money(number(row, "total_amount_minor"), text(row, "total_currency")),
                    ageLabel(updated, now));
        }).toList();
        String version = source.isEmpty() ? "ORDERS-EMPTY" : "ORDERS-" + timestamp(source.get(0), "updated_at").toEpochMilli();
        return new AdminProjection("ADMIN_READ_V1", version, "A140", "CS", items);
    }

    private static String sourceLabel(DirectoryContent value) {
        if (!present(value.sourceCategory()) || !present(value.sourceRef())) return "来源待确认";
        return value.sourceCategory() + " / " + value.sourceRef();
    }
    private static String reviewLabel(DirectoryContent value) {
        return switch (value.state()) {
            case DRAFT -> "等待复核"; case VERIFIED -> "已核验"; case PUBLISHED -> "已发布";
            case UNPUBLISHED -> "已下架"; case UNDER_REVIEW -> "复核中";
        };
    }
    private static String visibilityLabel(DirectoryContent value) {
        return value.state() == ContentState.PUBLISHED && !value.complaintPending() ? "可展示" : "暂不可展示";
    }
    private static String removalLabel(DirectoryContent value, Instant now) {
        if (value.complaintPending()) return "投诉核验期间停止展示";
        if (value.state() == ContentState.UNPUBLISHED) return "内容已下架";
        if (value.validUntil() != null && value.validUntil().isBefore(now)) return "核验有效期已结束";
        return "尚无下架原因";
    }
    private static String orderStateLabel(String state) {
        return switch (state) {
            case "AWAITING_PAYMENT" -> "等待付款"; case "PAYMENT_CONFIRMED" -> "付款已确认";
            case "TOPUP_PROCESSING" -> "充值处理中"; case "COMPLETED" -> "已完成";
            case "FAILED" -> "处理失败"; case "REFUNDED" -> "已退款"; default -> "状态待核对";
        };
    }
    private static String money(long minor, String currency) {
        return currency + " " + BigDecimal.valueOf(minor, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
    private static String ageLabel(Instant updated, Instant now) {
        long hours = Math.max(0, Duration.between(updated, now).toHours());
        return hours < 24 ? hours + "小时内更新" : (hours / 24) + "天前更新";
    }
    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key); if (value == null || value.toString().isBlank()) throw new IllegalStateException("ADMIN_READ_FIELD_MISSING:" + key);
        return value.toString();
    }
    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key); if (!(value instanceof Number number)) throw new IllegalStateException("ADMIN_READ_NUMBER_MISSING:" + key);
        return number.longValue();
    }
    private static boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key); return value instanceof Boolean flag ? flag : value instanceof Number number && number.intValue() != 0;
    }
    private static Instant timestamp(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.LocalDateTime local) return local.atZone(java.time.ZoneOffset.UTC).toInstant();
        throw new IllegalStateException("ADMIN_READ_TIMESTAMP_MISSING:" + key);
    }
    private static String instant(Map<String, Object> row, String key) { return timestamp(row, key).toString(); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }

    record AdminProjection(String schemaVersion, String projectionVersion, String pageId, String role, Object items) {}
    record A120Item(String contentRef, String title, String sourceLabel, String reviewLabel, String complaintLabel,
                    String visibilityLabel, String ownerLabel, String historyLabel, String removalLabel) {}
    record A130FinanceItem(String catalogRef, String displayName, String denominationLabel, String currencyLabel,
                           String priceCostCandidateLabel, String validityLabel, String supportBatchLabel,
                           String financeReviewLabel) {}
    record A140SupportItem(String orderRef, String maskedPhone, String userStatusLabel, String totalLabel,
                           String updatedLabel) {}
}
