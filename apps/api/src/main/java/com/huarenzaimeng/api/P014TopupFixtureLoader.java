package com.huarenzaimeng.api;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

import static com.huarenzaimeng.api.P014TopupDomain.*;

/** Explicit positive sample loader. Default and release modes load no P014 fixture. */
@Component
@Profile({"mock", "test"})
@ConditionalOnProperty(name = "hz.p014.fixture-mode", havingValue = "explicit-local-synthetic-sample")
final class P014TopupFixtureLoader {
    static final String ORDER_REF = "ORDER-P014-SYN-001";
    private final P014TopupService service;
    private final String testToken;

    P014TopupFixtureLoader(P014TopupService service, @Value("${hz.test-access-token:}") String testToken) {
        this.service = service; this.testToken = testToken;
    }
    @PostConstruct void load() {
        if (testToken == null || testToken.isBlank()) throw new IllegalStateException("explicit P014 fixture requires test token");
        service.installFixtureForTest(explicitFixture(LocalSyntheticIdentity.fromToken(testToken)));
    }
    static Fixture explicitFixture(LocalSyntheticIdentity identity) {
        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        PriceSnapshotSummary price = new PriceSnapshotSummary("PS-P014-SYN-001", 125000, "BDT", "DISPLAY-V1",
                "******1234", "SYN Operator", "SYN Package", 100000, "BDT", now.plusSeconds(3600));
        return new Fixture(ENVIRONMENT, REALITY_LEVEL, ORDER_REF, identity.projectSubjectRef(), identity.sessionRef(),
                "BUYER", 1, "AUTHSET-P014-SYN-001", "AUTH-EVIDENCE-V1", List.of(ORDER_REF), ORDER_STATE,
                1, 1, price, P014TopupService.snapshotDigest(price), "PAYMENT-DECISION-P014-SYN-001", "PAYMENT-RULE-V1",
                "CONFIRMED", "MNP-DECISION-P014-SYN-001", "MNP-RULE-V1", "ELIGIBLE", "CATALOG-V1",
                "SUPPORTED-SET-V1", true, true, true, "NOT_OBSERVED", "UNKNOWN", "NOT_OBSERVED",
                UnknownAgeDecision.WITHIN_LOCAL_WINDOW, "SUPPORT-P014-SYN-001", false, ResultState.FOUND, null, now);
    }
}
