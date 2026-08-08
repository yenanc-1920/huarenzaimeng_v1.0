package com.huarenzaimeng.api;

import com.huarenzaimeng.api.config.TestAccessTokenFilter;
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
@RequestMapping("/api/v1/admin/reconciliations")
@ConditionalOnProperty(name = "hz.a110.mode", havingValue = "local-synthetic")
class A110ReconciliationController {
    private static final Set<String> PROHIBITED_HEADERS = Set.of(
            "X-Role", "X-Actor-Ref", "X-Authorization-Ref", "X-Field-Scope", "X-Object-Set",
            "X-Account-Subject-Ref", "X-Role-Binding-Version", "X-Authorization-Decision-Version");
    private final A110ReconciliationService service;

    A110ReconciliationController(A110ReconciliationService service) { this.service = service; }

    @GetMapping
    ResponseEntity<A110ReconciliationResponse> read(HttpServletRequest request) {
        boolean body = request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null;
        boolean prohibited = PROHIBITED_HEADERS.stream().anyMatch(name -> request.getHeader(name) != null);
        A110ReconciliationResponse response;
        if (!request.getParameterMap().isEmpty() || body || prohibited) {
            response = service.invalidRequest();
        } else {
            response = service.read(attribute(request, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                    attribute(request, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF));
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-HZM-Mock-Only", "true")
                .body(response);
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String text ? text : null;
    }
}
