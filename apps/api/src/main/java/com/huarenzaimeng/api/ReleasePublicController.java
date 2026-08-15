package com.huarenzaimeng.api;

import com.huarenzaimeng.core.ProjectEnvelope;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Profile("release-mysql")
@RequestMapping("/api/v1")
final class ReleasePublicController {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final CatalogService catalog;
    private final ContentService content;
    private final Clock clock;

    ReleasePublicController(CatalogService catalog, ContentService content, Clock clock) {
        this.catalog = catalog; this.content = content; this.clock = clock;
    }

    @GetMapping("/catalog") ResponseEntity<ProjectEnvelope<CatalogView>> catalog(@RequestParam String operatorCode) {
        if (operatorCode.isBlank()) return ResponseEntity.badRequest()
                .body(new ProjectEnvelope<>("REJECTED","OPERATOR_CODE_REQUIRED",null));
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ProjectEnvelope.accepted(catalog.publicCatalog(operatorCode)));
    }

    @GetMapping("/eligibility") ResponseEntity<?> eligibility(@RequestParam String phone) {
        String normalized = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        boolean eligible = normalized.matches("\\+?[0-9]{6,20}");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of(
                "outcome", eligible ? "ELIGIBLE" : "REJECTED",
                "projectCode", eligible ? "RECHARGE_ELIGIBLE" : "RECHARGE_INPUT_INVALID",
                "eligible", eligible));
    }

    @GetMapping("/home/temporal-overview") ResponseEntity<TemporalOverviewResponse> overview() {
        Instant now = clock.instant();
        ZoneId dhaka = ZoneId.of("Asia/Dhaka"), beijing = ZoneId.of("Asia/Shanghai");
        Map<String,TemporalClockView> clocks = new LinkedHashMap<>();
        clocks.put("dhaka", clockView("DHAKA", "Dhaka", dhaka, now));
        clocks.put("beijing", clockView("BEIJING", "Beijing", beijing, now));
        Map<String,TemporalHolidayView> holidays = new LinkedHashMap<>();
        holidays.put("china", holiday("CN", beijing, now));
        holidays.put("bangladesh", holiday("BD", dhaka, now));
        TemporalOverviewResponse body = new TemporalOverviewResponse("HOME-" + now.toEpochMilli(),
                "TEMPORAL_OVERVIEW_READ_ERROR", "TEMPORAL_OVERVIEW_V1", now, now,
                "JAVA-TZDB", 60L, "BOTH_AVAILABLE", Map.copyOf(clocks), "NOT_CONFIGURED",
                Map.copyOf(holidays), "USER_INITIATED_READ_ONLY");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }

    @GetMapping("/content/life-items") ResponseEntity<LifeContentListResponse> lifeList() {
        Instant now = clock.instant();
        List<LifeContentSummary> items = content.publicList().items().stream().map(item -> new LifeContentSummary(
                item.contentRef(), String.valueOf(item.contentVersion()), category(item.category()), item.title(),
                item.summary(), "SELF_RESEARCH", "BD", "HUAREN_IN_BANGLADESH", item.updatedAt(), item.updatedAt(),
                item.updatedAt(), item.validUntil(), "CURRENT", "NOT_CONFIGURED", null)).toList();
        String state = items.isEmpty() ? "EMPTY" : "READY";
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(new LifeContentListResponse(
                "LIFE-" + now.toEpochMilli(), state, "LIFE_CONTENT_LIST_" + state,
                "LIFE_CONTENT_READ_V1", "RELEASE-CONTENT-V1", items, "NONE", null));
    }

    @GetMapping("/content/life-items/{contentRef}") ResponseEntity<LifeContentDetailResponse> lifeDetail(
            @PathVariable String contentRef, @RequestParam String contentVersion) {
        Instant now = clock.instant();
        try {
            long version = Long.parseLong(contentVersion);
            PublicContentProjection item = content.publicDetail(contentRef, version);
            LifeContentDetail detail = new LifeContentDetail(item.contentRef(), contentVersion,
                    category(item.category()), item.title(), item.summary(), "SELF_RESEARCH", "BD",
                    "HUAREN_IN_BANGLADESH", item.updatedAt(), item.updatedAt(), item.updatedAt(), item.validUntil(),
                    "CURRENT", "NOT_CONFIGURED", null, item.summary());
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(new LifeContentDetailResponse(
                    "LIFE-" + now.toEpochMilli(), "READY", "LIFE_CONTENT_DETAIL_READY",
                    "LIFE_CONTENT_READ_V1", "RELEASE-CONTENT-V1", contentRef, contentVersion,
                    detail, "NONE", null));
        } catch (RuntimeException error) {
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(new LifeContentDetailResponse(
                    "LIFE-" + now.toEpochMilli(), "REMOVED", "LIFE_CONTENT_DETAIL_REMOVED",
                    "LIFE_CONTENT_READ_V1", "RELEASE-CONTENT-V1", contentRef, contentVersion,
                    null, "NONE", null));
        }
    }

    private static TemporalClockView clockView(String code,String name,ZoneId zone,Instant now) {
        var local=now.atZone(zone); return new TemporalClockView(code,name,zone.getId(),local.toLocalDate(),TIME.format(local),"AVAILABLE");
    }
    private static TemporalHolidayView holiday(String code,ZoneId zone,Instant now) {
        return new TemporalHolidayView(code,now.atZone(zone).toLocalDate(),"READ_ERROR",null,null,null,null,null,null,null,null);
    }
    private static String category(String value) { return "HOLIDAY_EXPLANATION".equals(value) ? value : "LIFE_REMINDER"; }
}
