package com.huarenzaimeng.api;

import com.huarenzaimeng.api.config.TestAccessTokenFilter;
import com.huarenzaimeng.api.config.TrustedTestSessionCookieFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

import static com.huarenzaimeng.api.P021OrderDetailDomain.Response;

@RestController
@RequestMapping("/api/v1/orders")
final class P021OrderDetailController {
    private final P021OrderDetailService service;

    P021OrderDetailController(P021OrderDetailService service) { this.service = service; }

    @GetMapping("/{orderRef}")
    ResponseEntity<Response> detail(HttpServletRequest request, @PathVariable String orderRef) throws IOException {
        Response body = hasNonEmptyBody(request) || !request.getParameterMap().isEmpty()
                ? service.rejectInvalidInput()
                : service.read(attribute(request, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                        attribute(request, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                        attribute(request, TestAccessTokenFilter.LOCAL_SESSION_REF), orderRef, session(request));
        return ResponseEntity.ok().header("X-HZM-Mock-Only", "true")
                .header("X-HZM-Mock-Semantics", "LOCAL_SYNTHETIC_ORDER_DETAIL_READ_ONLY")
                .header("Cache-Control", "no-store").body(body);
    }

    private static boolean hasNonEmptyBody(HttpServletRequest request) throws IOException {
        return request.getInputStream().read() != -1;
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name); return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked") private static SessionSnapshot session(HttpServletRequest request) {
        Object version = request.getAttribute(TrustedTestSessionCookieFilter.TRUSTED_SESSION_VERSION);
        Object refs = request.getAttribute(TrustedTestSessionCookieFilter.TRUSTED_AUTHORIZED_ORDER_REFS);
        if (!(version instanceof Long sessionVersion) || !(refs instanceof List<?>)) return null;
        return new SessionSnapshot(attribute(request, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attribute(request, TestAccessTokenFilter.LOCAL_SESSION_REF), sessionVersion,
                attribute(request, TrustedTestSessionCookieFilter.TRUSTED_AUTHORIZATION_SET_REF),
                attribute(request, TrustedTestSessionCookieFilter.TRUSTED_AUTHORIZATION_EVIDENCE_VERSION),
                (List<String>) refs);
    }
}
