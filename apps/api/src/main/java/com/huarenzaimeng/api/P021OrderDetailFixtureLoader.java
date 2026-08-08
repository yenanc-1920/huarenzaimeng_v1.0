package com.huarenzaimeng.api;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.huarenzaimeng.api.P021OrderDetailDomain.*;

@Component
@Profile({"mock", "test"})
@ConditionalOnProperty(name = "hz.p021.mode", havingValue = "local-synthetic")
final class P021OrderDetailFixtureLoader {
    static final String ORDER_REF = "ORDER-P021-SYN-1";
    static final String AUTHORIZATION_SET_REF = "AUTHSET-P021-SYN-1";
    static final String AUTHORIZATION_EVIDENCE_VERSION = "AUTH-EVIDENCE-P021-V1";
    static final long SESSION_VERSION = 1L;

    private final String token;
    private final String fixtureMode;
    private final LocalSyntheticOrderRecoveryService recovery;
    private final P021OrderDetailService service;

    P021OrderDetailFixtureLoader(@Value("${hz.test-access-token:}") String token,
                                 @Value("${hz.p021.fixture-mode:no-default}") String fixtureMode,
                                 LocalSyntheticOrderRecoveryService recovery,
                                 P021OrderDetailService service) {
        this.token = token; this.fixtureMode = fixtureMode; this.recovery = recovery; this.service = service;
    }

    @PostConstruct
    void load() {
        if (!"no-default".equals(fixtureMode)) throw new IllegalStateException("P021 fixture mode must be no-default");
        if (token == null || token.isBlank()) throw new IllegalStateException("P021 trusted test token is required");
        LocalSyntheticIdentity identity = LocalSyntheticIdentity.fromToken(token);
        Fixture fixture = fixture(identity, UserOrderStateCode.AWAITING_PAYMENT, 2, 2, null,
                List.of("PAYMENT_CONFIRMATION"), List.of("TOPUP_RESULT"));
        recovery.installLocalSyntheticBuyerAuthorization(identity.projectSubjectRef(), identity.sessionRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF, AUTHORIZATION_EVIDENCE_VERSION, List.of(ORDER_REF));
        service.installLocalSyntheticFixture(fixture);
    }

    static Fixture fixture(LocalSyntheticIdentity identity, UserOrderStateCode state, long aggregateVersion,
                           long projectionVersion, String requestedSupportRef,
                           List<String> confirmedItems, List<String> unknownItems) {
        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        String supportRef = supports(state) ? requestedSupportRef : null;
        PriceSnapshotSummary price = new PriceSnapshotSummary("PRICE-P021-SYN-1", 125000, "BDT", "DISPLAY-V1",
                "******1234", "SYN Operator", "SYN Package", "1000 BDT", "BDT", now.plusSeconds(3600));
        TimelineItem item = new TimelineItem("TIMELINE-P021-" + projectionVersion, 1, projectionVersion, state,
                now, "ORDER_STATUS_" + state.name());
        List<AllowedAction> actions = new ArrayList<>();
        actions.add(new AllowedAction("REFRESH_ORDER_DETAIL", true, projectionVersion, null));
        if (supportRef != null && !supportRef.isBlank()) {
            actions.add(new AllowedAction("OPEN_SUPPORT", true, projectionVersion, supportRef));
        }
        actions.add(new AllowedAction("SAFE_BACK", true, projectionVersion, null));
        Projection projection = new Projection(ORDER_REF, aggregateVersion, projectionVersion, state, price,
                List.copyOf(confirmedItems), List.copyOf(unknownItems), responsibility(state), now, null,
                List.of(item), List.copyOf(actions), supportRef);
        String snapshotDigest = P021OrderDetailService.snapshotDigest(price);
        Fixture draft = new Fixture(true, ENVIRONMENT, REALITY_LEVEL, identity.projectSubjectRef(),
                ProjectSessionRole.BUYER, SESSION_VERSION, AUTHORIZATION_SET_REF,
                AUTHORIZATION_EVIDENCE_VERSION, List.of(ORDER_REF), projection, snapshotDigest,
                "P021-FIXTURE-V1", "PENDING");
        return new Fixture(draft.syntheticMarker(), draft.environment(), draft.realityEvidenceLevel(),
                draft.projectSubjectRef(), draft.sessionRole(), draft.sessionVersion(), draft.authorizationSetRef(),
                draft.authorizationEvidenceVersion(), draft.authorizedOrderRefs(), draft.projection(),
                draft.priceSnapshotDigest(), draft.fixtureSchemaVersion(), P021OrderDetailService.fixtureDigest(draft));
    }

    private static boolean supports(UserOrderStateCode state) {
        return state == UserOrderStateCode.TOPUP_RESULT_UNKNOWN
                || state == UserOrderStateCode.CONFIRMED_NOT_DELIVERED
                || state == UserOrderStateCode.DELIVERY_REFUND_CONFLICT_REVIEW
                || state == UserOrderStateCode.SUPPORT_REVIEW;
    }

    private static String responsibility(UserOrderStateCode state) {
        return supports(state) ? "SUPPORT_REVIEW"
                : state == UserOrderStateCode.AWAITING_PAYMENT ? "USER_PAYMENT" : "SYSTEM_RECHECK";
    }
}
