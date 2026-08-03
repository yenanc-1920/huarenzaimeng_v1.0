package com.huarenzaimeng.api;

import com.huarenzaimeng.api.config.TrustedTestSessionCookieFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.huarenzaimeng.api.P021OrderDetailDomain.*;

@RestController
@RequestMapping("/admin-read/v1/orders")
@ConditionalOnProperty(name = "hz.p021.mode", havingValue = "test-readonly")
final class P021AdminOrderController {
    record AdminResponse(String outcome, String projectCode, Object currentProjection) {}
    record CsProjection(String orderRef, UserOrderStateCode stateCode, String maskedTarget,
                        List<String> confirmedItems, List<String> unknownItems, String responsibilityCode,
                        java.time.Instant updatedAt, java.time.Instant nextReviewPoint, List<TimelineItem> timeline) {}
    record FinProjection(String orderRef, UserOrderStateCode stateCode, long totalMinor, String currency,
                         String targetValueDisplay, String targetCurrency, List<String> confirmedItems,
                         List<String> unknownItems, String responsibilityCode, java.time.Instant updatedAt,
                         java.time.Instant nextReviewPoint, List<TimelineItem> timeline) {}

    private final P021Store store;
    private final P021OrderDetailService validator;
    private final String subjectRef;
    private final String sessionRef;
    private final SessionSnapshot trustedSession;
    P021AdminOrderController(P021Store store, P021OrderDetailService validator,
                             @Value("${hz.it-session.buyer-subject-ref:}") String subjectRef,
                             @Value("${hz.it-session.buyer-session-ref:}") String sessionRef,
                             @Value("${hz.it-session.buyer-session-version:0}") long sessionVersion,
                             @Value("${hz.it-session.buyer-authorization-set-ref:}") String authorizationSetRef,
                             @Value("${hz.it-session.buyer-authorization-evidence-version:}") String evidenceVersion,
                             @Value("${hz.it-session.buyer-authorized-order-refs:}") String authorizedOrderRefs) {
        this.store = store; this.validator = validator; this.subjectRef = subjectRef; this.sessionRef = sessionRef;
        this.trustedSession = new SessionSnapshot(subjectRef, sessionRef, sessionVersion, authorizationSetRef,
                evidenceVersion, authorizedOrderRefs.isBlank() ? List.of()
                : java.util.Arrays.stream(authorizedOrderRefs.split(",", -1)).map(String::trim).toList());
    }

    @GetMapping("/{orderRef}") ResponseEntity<AdminResponse> read(HttpServletRequest request,
                                                                  @PathVariable String orderRef) {
        if (request.getContentLengthLong() > 0 || !request.getParameterMap().isEmpty()) return unavailable();
        String role = attribute(request, TrustedTestSessionCookieFilter.TRUSTED_ADMIN_ROLE);
        if (!"CS".equals(role) && !"FIN".equals(role)) return unavailable();
        Fixture fixture = store.findAuthorized(orderRef, trustedSession).orElse(null);
        if (fixture == null || !validator.isStrictStoredFixture(fixture, orderRef)) return unavailable();
        Projection p = fixture.projection();
        Object projection = "CS".equals(role)
                ? new CsProjection(p.orderRef(), p.stateCode(), p.priceSnapshotSummary().maskedTarget(),
                    p.confirmedItems(), p.unknownItems(), p.responsibilityCode(), p.updatedAt(),
                    p.nextReviewPoint(), p.timeline())
                : new FinProjection(p.orderRef(), p.stateCode(), p.priceSnapshotSummary().totalMinor(),
                    p.priceSnapshotSummary().currency(), p.priceSnapshotSummary().targetValueDisplay(),
                    p.priceSnapshotSummary().targetCurrency(), p.confirmedItems(), p.unknownItems(),
                    p.responsibilityCode(), p.updatedAt(), p.nextReviewPoint(), p.timeline());
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(new AdminResponse("ACCEPTED", "ADMIN_ORDER_DETAIL_READ", projection));
    }

    private static ResponseEntity<AdminResponse> unavailable() {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(new AdminResponse("REJECTED", "ADMIN_ORDER_DETAIL_NOT_AVAILABLE", null));
    }
    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name); return value instanceof String text ? text : null;
    }
}
