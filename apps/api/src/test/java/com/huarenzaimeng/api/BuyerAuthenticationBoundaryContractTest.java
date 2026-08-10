package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class BuyerAuthenticationBoundaryContractTest {
    @Test void releaseUsesOnlyCodeEntryAndKeepsRealProviderDisabled() throws Exception {
        Path root=Path.of("src/main/java/com/huarenzaimeng/api/buyerauth");
        String release=Files.readString(Path.of("src/main/resources/application-release-mysql.yml"));
        String controller=Files.readString(root.resolve("BuyerAuthController.java"));
        String client=Files.readString(root.resolve("WechatCode2SessionClient.java"));
        assertThat(release).contains("enabled: ${HZ_BUYER_AUTH_ENABLED:false}","provider-mode: disabled","code-pepper: ${HZ_BUYER_AUTH_CODE_PEPPER:}")
                .doesNotContain("trusted-ingress","ingress-hmac-secret","expected-appid-digest");
        assertThat(controller).contains("Set.of(\"code\",\"requestRef\")","/wechat/session")
                .doesNotContain("X-WX-APPID","X-WX-OPENID","openid","session_key","idleExpiresAt");
        String store=Files.readString(root.resolve("BuyerAuthStore.java"));
        assertThat(store).contains("enum Eligibility { ELIGIBLE }","record BuyerPrincipal(Eligibility eligibility, String subjectRef, String sessionRef)")
                .doesNotContain("record BuyerPrincipal(boolean","enum Eligibility { UNKNOWN","Instant eligibility","record AuthenticatedBuyer");
        assertThat(client).contains("REAL_PROVIDER_ADAPTER_DISABLED").doesNotContain("HttpClient","RestTemplate","WebClient");
        assertThat(Files.exists(root.resolve("TrustedWechatIngressVerifier.java"))).isFalse();
    }

    @Test void sourceAndReleaseConfigContainNoLegacyIdentityHeadersOrSecretValues() throws Exception {
        String all=Files.walk(Path.of("src/main/java/com/huarenzaimeng/api/buyerauth")).filter(Files::isRegularFile)
                .map(p->{try{return Files.readString(p);}catch(Exception e){throw new IllegalStateException(e);}}).reduce("",String::concat);
        assertThat(all).doesNotContain("X-WX-OPENID","X-WX-APPID","X-HZM-Trusted-Ingress-Signature","SET last_seen_at","last_seen_at=?");
    }
}
