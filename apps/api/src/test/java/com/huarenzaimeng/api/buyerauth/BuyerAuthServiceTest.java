package com.huarenzaimeng.api.buyerauth;

import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

    private static String hmac(String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(PEPPER.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static final class CapturingStore implements BuyerAuthStore {
        int writeCount; String subjectDigest; String tokenDigest; Instant issuedAt; Instant expiresAt; Audit audit;
        @Override public Identity establishIdentityAndSession(String appidDigest,String subjectDigest,String subjectRef,
                String sessionId,String tokenDigest,Instant issuedAt,Instant expiresAt,Audit audit) {
            writeCount++; this.subjectDigest=subjectDigest; this.tokenDigest=tokenDigest; this.issuedAt=issuedAt;this.expiresAt=expiresAt;this.audit=audit;
            return new Identity("buyer-id",subjectRef);
        }
        @Override public Optional<AuthenticatedBuyer> findActiveSession(String tokenDigest,Instant now){return Optional.empty();}
    }
}
