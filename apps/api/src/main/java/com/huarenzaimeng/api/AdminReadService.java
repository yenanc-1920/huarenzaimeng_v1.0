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
import java.util.Set;

@Service
@Profile("release-mysql")
class AdminReadService {
    private static final Set<String> NEWS_CATEGORIES = Set.of("LIFE_REMINDER", "HOLIDAY_EXPLANATION");
    private final ContentService content;
    private final AdminReadMapper mapper;
    private final Clock clock;

    AdminReadService(ContentService content, AdminReadMapper mapper, Clock clock) {
        this.content = content; this.mapper = mapper; this.clock = clock;
    }

    AdminProjection read(String pageId) { return read(pageId,"SUPER_ADMIN"); }
    AdminProjection read(String pageId,String role) {
        return switch (pageId) {
            case "A100" -> customerCaseProjection();
            case "A110" -> reconciliationProjection();
            case "A120" -> contentProjection();
            case "A121" -> directoryProjection();
            case "A122" -> holidayNewsProjection();
            case "A130" -> productProjection(role);
            case "A140" -> orderProjection();
            default -> null;
        };
    }

    AdminProjection cities() {
        List<Map<String,Object>> rows=mapper.selectCities();
        return new AdminProjection("ADMIN_READ_V1",version("CITIES",rows),"A121-CITIES","SUPER_ADMIN",rows);
    }

    AdminProjection channels() {
        List<Map<String,Object>> rows=mapper.selectProviderChannels();
        return new AdminProjection("ADMIN_READ_V1",version("CHANNELS",rows),"A130-CHANNELS","SUPER_ADMIN",rows);
    }

    private AdminProjection customerCaseProjection() {
        List<Map<String,Object>> rows=mapper.selectCustomerCases();
        return new AdminProjection("ADMIN_READ_V1", version("CASES",rows), "A100", "SUPER_ADMIN", rows);
    }

    private AdminProjection reconciliationProjection() {
        List<Map<String,Object>> rows=mapper.selectReconciliationCases();
        return new AdminProjection("ADMIN_READ_V1", version("RECONCILIATION",rows), "A110", "SUPER_ADMIN", rows);
    }

    private AdminProjection directoryProjection() {
        List<Map<String,Object>> rows=mapper.selectDirectoryEntries();
        if (rows == null || rows.isEmpty()) return managedContentProjection("A121", false);
        return new AdminProjection("ADMIN_READ_V1", version("DIRECTORY",rows), "A121", "SUPER_ADMIN", rows);
    }

    private AdminProjection holidayNewsProjection() {
        List<Map<String,Object>> rows=mapper.selectHolidayAndNews();
        if (rows == null || rows.isEmpty()) return managedContentProjection("A122", true);
        return new AdminProjection("ADMIN_READ_V1", version("CONTENT",rows), "A122", "SUPER_ADMIN", rows);
    }

    private AdminProjection productProjection(String role) {
        List<Map<String,Object>> rows=mapper.selectPlatformProducts();
        if (rows == null) rows=List.of();
        if (!"SUPER_ADMIN".equals(role)) {
            List<A130ContentItem> contentRows=rows.stream().map(row->new A130ContentItem(
                    text(row,"productRef"),text(row,"operatorCode"),text(row,"productType"),
                    text(row,"displayName"),text(row,"benefitText"),displayText(row,"validityText"),
                    text(row,"state"),number(row,"version"))).toList();
            return new AdminProjection("ADMIN_READ_V1",version("PRODUCTS-CONTENT",rows),"A130",role,contentRows);
        }
        return new AdminProjection("ADMIN_READ_V1", version("PRODUCTS",rows), "A130", "SUPER_ADMIN", rows);
    }

    private static String version(String prefix,List<Map<String,Object>> rows) {
        return prefix + "-" + rows.size() + "-" + rows.stream().map(Object::toString).mapToInt(String::hashCode).reduce(0,(a,b)->31*a+b);
    }

    private AdminProjection managedContentProjection(String pageId, boolean news) {
        List<DirectoryContent> source = content.internalList().items().stream()
                .filter(value -> NEWS_CATEGORIES.contains(value.category()) == news).toList();
        List<ManagedContentItem> items = source.stream().map(value -> new ManagedContentItem(
                value.contentRef(), value.title(), value.summary(), value.category(), sourceLabel(value),
                reviewLabel(value), Long.toString(value.version()),
                value.verifiedAt() == null ? "尚未核验" : value.verifiedAt().toString(),
                value.validUntil() == null ? "未设置" : value.validUntil().toString(), value.updatedAt().toString())).toList();
        long version = source.stream().mapToLong(DirectoryContent::version).max().orElse(0);
        return new AdminProjection("ADMIN_READ_V1", (news ? "NEWS-V" : "DIRECTORY-V") + version, pageId, "CONTENT", items);
    }

    private AdminProjection contentProjection() {
        List<DirectoryContent> source = content.internalList().items();
        Instant now = clock.instant();
        List<Object> items = new java.util.ArrayList<>(source.stream().map(value -> (Object)new A120Item(
                value.contentRef(), value.title(), sourceLabel(value), reviewLabel(value),
                value.complaintPending() ? "存在待查投诉" : "无待查投诉", visibilityLabel(value),
                present(value.verifiedBy()) ? "核验人：" + value.verifiedBy() : "内容职责待复核",
                "历史记录 " + value.auditTrail().size() + " 条", removalLabel(value, now))).toList());
        List<Map<String,Object>> reports=mapper.selectDirectoryReports();
        if (reports != null) reports.stream().map(report -> new A120Item(
                text(report, "reportRef"),
                "黄页反馈：" + text(report, "entryRef") + " / " + reportReasonLabel(text(report, "reasonCode")),
                "黄页用户反馈", text(report, "state"),
                optionalText(report, "description", "未补充说明"),
                "不直接修改公开内容", "待内容运营核验",
                timestamp(report, "createdAt").toString(), "核验后处理")).forEach(items::add);
        List<Map<String,Object>> reviews=mapper.selectContentReviewTasks();
        if(reviews!=null) reviews.stream().map(review -> new A120Item(
                text(review,"reviewRef"),
                text(review,"objectType")+" / "+text(review,"objectRef")+" / V"+number(review,"objectVersion"),
                "正式审核任务",text(review,"state"),"提交人："+text(review,"submitterRef"),
                "APPROVED".equals(text(review,"state"))?"允许同版本发布":"不可发布",
                review.get("reviewerRef")==null?"待独立审核人":"审核人："+review.get("reviewerRef"),
                "版本绑定且不可追溯改写",optionalText(review,"decisionReason","尚无审核结论"))).forEach(items::add);
        long version = source.stream().mapToLong(DirectoryContent::version).max().orElse(0);
        return new AdminProjection("ADMIN_READ_V1", "CONTENT-V" + version + "-R" + (reports==null?0:reports.size())+"-W"+(reviews==null?0:reviews.size()), "A120", "SUPER_ADMIN", items);
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
        return new AdminProjection("ADMIN_READ_V1", "CATALOG-V" + version, "A130", "SUPER_ADMIN", items);
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
        return new AdminProjection("ADMIN_READ_V1", version, "A140", "SUPER_ADMIN", items);
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
    private static String reportReasonLabel(String reasonCode) {
        return switch (reasonCode) {
            case "INCORRECT_INFO" -> "信息不准确";
            case "PHONE_INVALID" -> "联系电话无效";
            case "ADDRESS_INVALID" -> "地址无效";
            case "CLOSED" -> "机构已关闭";
            case "OTHER" -> "其他问题";
            default -> throw new IllegalStateException("ADMIN_READ_REPORT_REASON_INVALID:" + reasonCode);
        };
    }
    private static String optionalText(Map<String, Object> row, String key, String fallback) {
        Object value = row.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
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
    private static String displayText(Map<String,Object> row,String key){Object value=row.get(key);return value==null?"":value.toString();}
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
    record ManagedContentItem(String contentRef, String title, String summary, String category, String sourceLabel,
                              String statusLabel, String versionLabel, String verifiedAtLabel,
                              String validUntilLabel, String updatedAtLabel) {}
    record A130FinanceItem(String catalogRef, String displayName, String denominationLabel, String currencyLabel,
                           String priceCostCandidateLabel, String validityLabel, String supportBatchLabel,
                           String financeReviewLabel) {}
    record A130ContentItem(String productRef,String operatorCode,String productType,String displayName,
                           String benefitText,String validityText,String state,long version) {}
    record A140SupportItem(String orderRef, String maskedPhone, String userStatusLabel, String totalLabel,
                           String updatedLabel) {}
}
