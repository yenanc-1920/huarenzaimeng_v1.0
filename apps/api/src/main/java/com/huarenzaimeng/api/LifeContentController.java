package com.huarenzaimeng.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/content/life-items")
@ConditionalOnProperty(name = "hz.life-content.mode", havingValue = "local-synthetic")
class LifeContentController {
    private static final String MOCK_HEADER = "X-HZM-Mock-Only";
    private static final String SEMANTICS_HEADER = "X-HZM-Mock-Semantics";
    private final LifeContentReadService service;

    LifeContentController(LifeContentReadService service) { this.service = service; }

    @GetMapping
    ResponseEntity<LifeContentListResponse> list(HttpServletRequest request) {
        LifeContentListResponse response = request.getParameterMap().isEmpty()
                ? service.list() : service.invalidListRequest();
        return localSynthetic(response);
    }

    @GetMapping("/{contentRef}")
    ResponseEntity<LifeContentDetailResponse> detail(HttpServletRequest request,
            @PathVariable String contentRef,
            @RequestParam(name = "contentVersion", required = false) String contentVersion) {
        boolean invalidShape = request.getParameterMap().entrySet().stream()
                .anyMatch(entry -> !Set.of("contentVersion").contains(entry.getKey())
                        || entry.getValue() == null || entry.getValue().length != 1);
        LifeContentDetailResponse response = invalidShape
                ? service.detail(contentRef, null) : service.detail(contentRef, contentVersion);
        return localSynthetic(response);
    }

    private static <T> ResponseEntity<T> localSynthetic(T response) {
        return ResponseEntity.ok().header(MOCK_HEADER, "true")
                .header(SEMANTICS_HEADER, "LOCAL_SYNTHETIC_READ_ONLY_NO_EXTERNAL_FACTS")
                .header("Cache-Control", "no-store").body(response);
    }
}
