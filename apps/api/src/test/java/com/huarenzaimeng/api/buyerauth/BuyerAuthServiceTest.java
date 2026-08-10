package com.huarenzaimeng.api.buyerauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuyerAuthServiceTest {
    private static final String PEPPER = "synthetic-pepper-value-with-at-least-32-characters";
    private static final String APPID = "wx_appid_12345678";

    @Test void trustedWechatIdentityCreatesServerOwnedSubjectAndOpaqueSession() {
        CapturingStore store = new CapturingStore();
        BuyerAuthService service = new BuyerAuthService(store, true, hmac(APPID), PEPPER, 8);

        var result = service.establish(APPID, "provider_subject_12345678", "REQ-1");

        assertThat(result.subjectRef()).startsWith("BUYER-").doesNotContain("provider_subject");
        assertThat(result.token()).hasSize(43);
        assertThat(store.tokenDigest).matches("[0-9a-f]{64}").doesNotContain(result.token());
        assertThat(store.subjectDigest).matches("[0-9a-f]{64}");
        assertThat(store.audit.subjectFingerprint()).isEqualTo(store.subjectDigest);
        assertThat(store.audit.sessionFingerprint()).isEqualTo(store.tokenDigest);
        assertThat(store.expiresAt).isAfter(store.issuedAt);
    }

    @Test void disabledMissingOrWrongAppIdentityFailsClosedWithoutDatabaseWrite() {
        CapturingStore store = new CapturingStore();
        assertThatThrownBy(() -> new BuyerAuthService(store, false, "", "", 8)
                .establish(APPID, "provider_subject_12345678", null)).isInstanceOf(BuyerAuthService.Rejected.class);
        assertThatThrownBy(() -> new BuyerAuthService(store, true, hmac(APPID), PEPPER, 8)
                .establish("wx_wrong_12345678", "provider_subject_12345678", null)).isInstanceOf(BuyerAuthService.Rejected.class);
        assertThat(store.writeCount).isZero();
    }

    @Test void untrustedRequestIdentifierIsNotPersistedInAudit() {
        CapturingStore store = new CapturingStore();
        BuyerAuthService service = new BuyerAuthService(store, true, hmac(APPID), PEPPER, 8);

        service.establish(APPID, "provider_subject_12345678", "openid_12345678\nsecret");

        assertThat(store.audit.requestId()).matches("[0-9a-f-]{36}");
        assertThat(store.audit.requestId()).doesNotContain("openid", "secret");
    }

    @Test void disabledModeRejectsPreloadedValidSessionBeforeStoreReadAndBuyerProjection() throws Exception {
        CapturingStore store = new CapturingStore();
        store.activeBuyer = Optional.of(new BuyerAuthStore.AuthenticatedBuyer("buyer-id", "BUYER-synthetic"));
        BuyerAuthService service = new BuyerAuthService(store, false, "", "", 8);

        assertThat(service.authenticate("synthetic-valid-old-token")).isEmpty();
        assertThat(store.readCount).isZero();
        assertThat(store.writeCount).isZero();

        BuyerSessionFilter filter = new BuyerSessionFilter(service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/buyer-api/v1/orders");
        request.addHeader("Authorization", "Bearer synthetic-valid-old-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        int[] downstreamCalls = {0};
        FilterChain downstream = (ignoredRequest, ignoredResponse) -> downstreamCalls[0]++;

        filter.doFilter(request, response, downstream);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getAttribute(BuyerSessionFilter.BUYER)).isNull();
        assertThat(downstreamCalls[0]).isZero();
        assertThat(store.readCount).isZero();
        assertThat(store.writeCount).isZero();
    }

    @Test void enabledModeStillAuthenticatesPreloadedValidSession() {
        CapturingStore store = new CapturingStore();
        BuyerAuthStore.AuthenticatedBuyer buyer = new BuyerAuthStore.AuthenticatedBuyer("buyer-id", "BUYER-synthetic");
        store.activeBuyer = Optional.of(buyer);
        BuyerAuthService service = new BuyerAuthService(store, true, hmac(APPID), PEPPER, 8);

        assertThat(service.authenticate("synthetic-valid-old-token")).contains(buyer);
        assertThat(store.readCount).isOne();
        assertThat(store.writeCount).isZero();
    }

    private static String hmac(String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(PEPPER.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static final class CapturingStore implements BuyerAuthStore {
        int readCount; int writeCount; String subjectDigest; String tokenDigest; Instant issuedAt; Instant expiresAt; Audit audit;
        Optional<AuthenticatedBuyer> activeBuyer = Optional.empty();
        @Override public Identity establishIdentityAndSession(String appidDigest,String subjectDigest,String subjectRef,
                String sessionId,String tokenDigest,Instant issuedAt,Instant expiresAt,Audit audit) {
            writeCount++; this.subjectDigest=subjectDigest; this.tokenDigest=tokenDigest; this.issuedAt=issuedAt;this.expiresAt=expiresAt;this.audit=audit;
            return new Identity("buyer-id",subjectRef);
        }
        @Override public Optional<AuthenticatedBuyer> findActiveSession(String tokenDigest,Instant now){readCount++; return activeBuyer;}
    }
}
