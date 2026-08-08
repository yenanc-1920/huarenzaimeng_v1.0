package com.huarenzaimeng.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@Profile({"mock", "test"})
@RequestMapping("/api/v1/home/temporal-overview")
@ConditionalOnProperty(name = "hz.temporal-overview.mode", havingValue = "local-synthetic")
class TemporalOverviewController {
    private static final Set<String> PROHIBITED_REQUEST_HEADERS = Set.of(
            "X-Project-Subject-Ref", "X-Actor-Ref", "X-HZM-Test-Access-Token",
            "X-Phone", "X-OpenId", "X-Profile", "X-Device-Id", "X-Device-TimeZone",
            "X-Client-Time", "X-Client-Reference-Instant", "X-Country-Override", "X-City-Override",
            "X-Source-Override", "X-Qualification-Override");
    private final TemporalOverviewService service;

    TemporalOverviewController(TemporalOverviewService service) { this.service = service; }

    @GetMapping
    ResponseEntity<TemporalOverviewResponse> read(HttpServletRequest request) {
        boolean hasBody = request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null;
        boolean hasProhibitedHeader = PROHIBITED_REQUEST_HEADERS.stream()
                .anyMatch(name -> request.getHeader(name) != null);
        TemporalOverviewResponse response = request.getParameterMap().isEmpty() && !hasBody && !hasProhibitedHeader
                ? service.read() : service.invalidRequest();
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-HZM-Mock-Only", "true")
                .header("X-HZM-Mock-Semantics", "LOCAL_SYNTHETIC_READ_ONLY_NO_EXTERNAL_FACTS")
                .body(response);
    }
}
