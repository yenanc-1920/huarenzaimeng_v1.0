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
    private final V1DevelopmentDataService developmentData;

    ReleasePublicController(CatalogService catalog, ContentService content, Clock clock,
                            V1DevelopmentDataService developmentData) {
        this.catalog = catalog; this.content = content; this.clock = clock; this.developmentData = developmentData;
    }

    @GetMapping("/catalog") ResponseEntity<?> catalog(@RequestParam String operatorCode,
                                                       @RequestParam(required=false) String productType) {
        if (operatorCode.isBlank()) return ResponseEntity.badRequest()
                .body(new ProjectEnvelope<>("REJECTED","OPERATOR_CODE_REQUIRED",null));
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ProjectEnvelope.accepted(developmentData.catalog(operatorCode, productType)));
    }

    @GetMapping("/eligibility") ResponseEntity<?> eligibility(@RequestParam String phone) {
        String normalized;
        try { normalized = ReleaseQuoteOrderService.normalizeBangladeshPhone(phone); }
        catch (FlowRejectedException invalid) {
            return ResponseEntity.badRequest().body(new ProjectEnvelope<>("REJECTED","RECHARGE_INPUT_INVALID",null));
        }
        String local=normalized.replaceFirst("^\\+880","0");
        String operator=operatorCode(local);
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("outcome","UNKNOWN");
        data.put("maskedPhone",local.substring(0,3)+"****"+local.substring(local.length()-3));
        data.put("operatorCode",operator);
        data.put("operatorName",operator==null?null:V1DevelopmentDataService.operatorName(operator));
        data.put("projectCode","OPERATOR_OR_CATALOG_SELECTION_REQUIRED");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ProjectEnvelope.accepted(data));
    }

    @GetMapping("/home/temporal-overview") ResponseEntity<?> overview() {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ProjectEnvelope.accepted(developmentData.temporalOverview()));
    }

    @GetMapping("/content/life-items") ResponseEntity<?> lifeList(
            @RequestParam(required=false) String category) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ProjectEnvelope.accepted(developmentData.news(category)));
    }

    @GetMapping("/content/life-items/{contentRef}") ResponseEntity<?> lifeDetail(
            @PathVariable String contentRef, @RequestParam(required=false) String contentVersion) {
        try {
            return ResponseEntity.ok().header("Cache-Control", "no-store")
                    .body(ProjectEnvelope.accepted(developmentData.newsDetail(contentRef)));
        } catch (RuntimeException error) {
            return ResponseEntity.notFound().build();
        }
    }

    private static TemporalClockView clockView(String code,String name,ZoneId zone,Instant now) {
        var local=now.atZone(zone); return new TemporalClockView(code,name,zone.getId(),local.toLocalDate(),TIME.format(local),"AVAILABLE");
    }
    private static TemporalHolidayView holiday(String code,ZoneId zone,Instant now) {
        return new TemporalHolidayView(code,now.atZone(zone).toLocalDate(),"READ_ERROR",null,null,null,null,null,null,null,null);
    }
    private static String category(String value) { return "HOLIDAY_EXPLANATION".equals(value) ? value : "LIFE_REMINDER"; }
    private static String operatorCode(String phone){return switch(phone.substring(0,3)){
        case"013","017"->"GRAMEENPHONE";case"018"->"ROBI";case"019"->"BANGLALINK";case"016"->"AIRTEL";case"015"->"TELETALK";default->null;};}
}
