package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class BuyerAuthenticationBoundaryContractTest {
    @Test void releaseBuyerIdentityIsDisabledByDefaultAndRequiresCryptographicallyVerifiedIngress() throws Exception {
        String release=Files.readString(Path.of("src/main/resources/application-release-mysql.yml"));
        String controller=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/buyerauth/BuyerAuthController.java"));
        String service=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/buyerauth/BuyerAuthService.java"));
        String verifier=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/buyerauth/TrustedWechatIngressVerifier.java"));
        assertThat(release).contains("enabled: ${HZ_BUYER_AUTH_ENABLED:false}","trusted-ingress: ${HZ_BUYER_AUTH_TRUSTED_INGRESS:}",
                "expected-appid-digest: ${HZ_BUYER_AUTH_APPID_DIGEST:}","identity-pepper: ${HZ_BUYER_AUTH_IDENTITY_PEPPER:}",
                "ingress-hmac-secret: ${HZ_BUYER_AUTH_INGRESS_HMAC_SECRET:}");
        assertThat(controller).contains("ingress.verify(request)","/wechat/session").doesNotContain("request.getHeader(\"X-WX-APPID\")","request.getHeader(\"X-WX-OPENID\")");
        assertThat(verifier).contains("HmacSHA256","X-HZM-Trusted-Ingress-Signature","putIfAbsent","MAX_SKEW_SECONDS");
        assertThat(service).contains("HmacSHA256","SecureRandom","BUYER-")
                .doesNotContain("System.out","printStackTrace","LOG.");
    }
}
