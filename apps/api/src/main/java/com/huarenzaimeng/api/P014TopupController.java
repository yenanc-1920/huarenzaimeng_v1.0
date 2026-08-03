package com.huarenzaimeng.api;

import com.huarenzaimeng.api.config.TestAccessTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.huarenzaimeng.api.P014TopupDomain.*;

@RestController
@Profile({"mock", "test"})
@ConditionalOnProperty(name="hz.p014.mode",havingValue="local-synthetic")
@RequestMapping("/api/v1/orders/{orderRef}")
final class P014TopupController {
    private final P014TopupService service;
    P014TopupController(P014TopupService service) { this.service = service; }

    @PostMapping("/topup-intents")
    ResponseEntity<Response> create(HttpServletRequest servlet, @PathVariable String orderRef,
                                    @Valid @RequestBody CreateRequest request) {
        Response body = service.create(attr(servlet, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                attr(servlet, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attr(servlet, TestAccessTokenFilter.LOCAL_SESSION_REF), orderRef, request);
        return response(service.strictOrFail(body, "TOPUP_NOT_AVAILABLE"), "LOCAL_SYNTHETIC_WRITE_ZERO_EXTERNAL");
    }

    @GetMapping("/topup-intents/result")
    ResponseEntity<Response> result(HttpServletRequest servlet, @PathVariable String orderRef) throws IOException {
        if (hasNonEmptyBody(servlet)) return inputUnavailable("TOPUP_INTENT_QUERY_NOT_AVAILABLE",
                "LOCAL_SYNTHETIC_READ_ONLY_RESULT");
        Set<String> allowed = Set.of("commandId", "idempotencyKey", "sessionVersion", "authorizationSetRef");
        Map<String, String[]> parameters = servlet.getParameterMap();
        boolean invalid = parameters.size() != allowed.size() || !parameters.keySet().equals(allowed)
                || parameters.values().stream().anyMatch(P014TopupController::missingRepeatedOrBlank);
        Long sessionVersion = invalid ? null : positiveLong(parameters.get("sessionVersion")[0]);
        if (invalid || sessionVersion == null) return inputUnavailable("TOPUP_INTENT_QUERY_NOT_AVAILABLE",
                "LOCAL_SYNTHETIC_READ_ONLY_RESULT");
        String commandId = parameters.get("commandId")[0];
        String idempotencyKey = parameters.get("idempotencyKey")[0];
        String authorizationSetRef = parameters.get("authorizationSetRef")[0];
        Response body = service.result(attr(servlet, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                attr(servlet, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attr(servlet, TestAccessTokenFilter.LOCAL_SESSION_REF), orderRef, commandId, idempotencyKey,
                sessionVersion, authorizationSetRef, false);
        return response(service.strictOrFail(body, "TOPUP_INTENT_QUERY_NOT_AVAILABLE"), "LOCAL_SYNTHETIC_READ_ONLY_RESULT");
    }

    @GetMapping("/projection")
    ResponseEntity<Response> projection(HttpServletRequest servlet, @PathVariable String orderRef) throws IOException {
        if (hasNonEmptyBody(servlet) || !servlet.getParameterMap().isEmpty()) {
            return inputUnavailable("TOPUP_PROGRESS_NOT_AVAILABLE", "LOCAL_SYNTHETIC_READ_ONLY_PROJECTION");
        }
        Response body = service.projection(attr(servlet, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                attr(servlet, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attr(servlet, TestAccessTokenFilter.LOCAL_SESSION_REF), orderRef);
        return response(service.strictOrFail(body, "TOPUP_PROGRESS_NOT_AVAILABLE"), "LOCAL_SYNTHETIC_READ_ONLY_PROJECTION");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Response> invalidShape() {
        return response(new Response(null, "REJECTED", "TOPUP_NOT_AVAILABLE", null,
                null, null, "NONE", null), "LOCAL_SYNTHETIC_FAIL_CLOSED");
    }

    private static ResponseEntity<Response> response(Response body, String semantics) {
        return ResponseEntity.ok().header("X-HZM-Mock-Only", "true")
                .header("X-HZM-Mock-Semantics", semantics).header("Cache-Control", "no-store").body(body);
    }
    private static String attr(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name); return value == null ? null : value.toString();
    }
    private static boolean hasNonEmptyBody(HttpServletRequest request) throws IOException {
        return request.getInputStream().read() != -1;
    }
    private static boolean missingRepeatedOrBlank(String[] values) {
        return values == null || values.length != 1 || values[0] == null || values[0].isBlank();
    }
    private static Long positiveLong(String value) {
        if (!value.matches("[1-9][0-9]*")) return null;
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return null; }
    }
    private static ResponseEntity<Response> inputUnavailable(String projectCode, String semantics) {
        return response(new Response(null, "REJECTED", projectCode, null,
                null, null, "NONE", null), semantics);
    }
}
