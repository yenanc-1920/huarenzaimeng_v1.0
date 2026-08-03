package com.huarenzaimeng.api;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.huarenzaimeng.api.P021OrderDetailDomain.Fixture;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory", "hz.p021.mode=test-readonly",
        "hz.test-access-token=unrelated-legacy-header-token",
        "hz.it-session.buyer-token=buyer-cookie", "hz.it-session.cs-token=cs-cookie",
        "hz.it-session.fin-token=fin-cookie", "hz.it-session.buyer-subject-ref=IT-SUBJECT-P021",
        "hz.it-session.buyer-session-ref=IT-SESSION-P021"
        ,"hz.it-session.buyer-session-version=1", "hz.it-session.buyer-authorization-set-ref=AUTHSET-P021-SYN-1",
        "hz.it-session.buyer-authorization-evidence-version=AUTH-EVIDENCE-P021-V1",
        "hz.it-session.buyer-authorized-order-refs=ORDER-P021-SYN-1"
})
@AutoConfigureMockMvc
class P021TestReadonlyIntegrationContractTest {
    @Autowired MockMvc mvc;
    @Autowired P021Store store;

    @BeforeEach void fixture() {
        store.clearForTest();
        LocalSyntheticIdentity identity = LocalSyntheticIdentity.fromToken("fixture-only");
        Fixture original = P021OrderDetailFixtureLoader.fixture(identity, UserOrderStateCode.AWAITING_PAYMENT,
                1, 1, null, List.of(), List.of());
        Fixture fixture = new Fixture(original.syntheticMarker(), original.environment(), original.realityEvidenceLevel(),
                "IT-SUBJECT-P021", original.sessionRole(), original.sessionVersion(), original.authorizationSetRef(),
                original.authorizationEvidenceVersion(), List.of(P021OrderDetailFixtureLoader.ORDER_REF),
                original.projection(), original.priceSnapshotDigest(), original.fixtureSchemaVersion(), "PENDING");
        fixture = new Fixture(fixture.syntheticMarker(), fixture.environment(), fixture.realityEvidenceLevel(),
                fixture.projectSubjectRef(), fixture.sessionRole(), fixture.sessionVersion(), fixture.authorizationSetRef(),
                fixture.authorizationEvidenceVersion(), fixture.authorizedOrderRefs(), fixture.projection(),
                fixture.priceSnapshotDigest(), fixture.fixtureSchemaVersion(), P021OrderDetailService.fixtureDigest(fixture));
        store.installForTest(fixture, "IT-SESSION-P021");
    }

    @Test void buyerCookieReadsProjectionWithoutClientRoleOrAuthorizationHeader() throws Exception {
        mvc.perform(get("/api/v1/orders/{orderRef}", P021OrderDetailFixtureLoader.ORDER_REF)
                        .cookie(new Cookie("HZM_IT_SESSION", "buyer-cookie")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.projectCode").value("ORDER_DETAIL_READ"));
        mvc.perform(get("/api/v1/orders/{orderRef}", P021OrderDetailFixtureLoader.ORDER_REF)
                        .header("Authorization", "Bearer client-asserted"))
                .andExpect(jsonPath("$.projectCode").value("ORDER_DETAIL_NOT_AVAILABLE"));
    }

    @Test void adminCsAndFinReceiveMutuallyExclusiveReadOnlyFields() throws Exception {
        mvc.perform(get("/admin-read/v1/orders/{orderRef}", P021OrderDetailFixtureLoader.ORDER_REF)
                        .cookie(new Cookie("HZM_IT_SESSION", "cs-cookie")))
                .andExpect(jsonPath("$.projectCode").value("ADMIN_ORDER_DETAIL_READ"))
                .andExpect(jsonPath("$.currentProjection.maskedTarget").exists())
                .andExpect(jsonPath("$.currentProjection.totalMinor").doesNotExist());
        mvc.perform(get("/admin-read/v1/orders/{orderRef}", P021OrderDetailFixtureLoader.ORDER_REF)
                        .cookie(new Cookie("HZM_IT_SESSION", "fin-cookie")))
                .andExpect(jsonPath("$.currentProjection.totalMinor").value(125000))
                .andExpect(jsonPath("$.currentProjection.maskedTarget").doesNotExist());
        mvc.perform(get("/admin-read/v1/orders/{orderRef}", P021OrderDetailFixtureLoader.ORDER_REF))
                .andExpect(jsonPath("$.projectCode").value("ADMIN_ORDER_DETAIL_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.currentProjection").doesNotExist());
    }

    @Test void adminValidatesCompleteProjectionBeforeRoleCropping() throws Exception {
        LocalSyntheticIdentity identity = LocalSyntheticIdentity.fromToken("fixture-only");
        Fixture valid = P021OrderDetailFixtureLoader.fixture(identity, UserOrderStateCode.AWAITING_PAYMENT,
                1, 1, null, List.of(), List.of());
        Fixture malformed = new Fixture(valid.syntheticMarker(), valid.environment(), valid.realityEvidenceLevel(),
                "IT-SUBJECT-P021", valid.sessionRole(), valid.sessionVersion(), valid.authorizationSetRef(),
                valid.authorizationEvidenceVersion(), List.of(P021OrderDetailFixtureLoader.ORDER_REF),
                valid.projection(), "MISMATCH", valid.fixtureSchemaVersion(), valid.fixtureDigest());
        store.installForTest(malformed, "IT-SESSION-P021");
        mvc.perform(get("/admin-read/v1/orders/{orderRef}", P021OrderDetailFixtureLoader.ORDER_REF)
                        .cookie(new Cookie("HZM_IT_SESSION", "cs-cookie")))
                .andExpect(jsonPath("$.projectCode").value("ADMIN_ORDER_DETAIL_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.currentProjection").doesNotExist());
    }
}
