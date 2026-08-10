package com.huarenzaimeng.api.buyerauth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuyerAuthConfigurationValidatorTest {
    @Test void disabledModeIsTheFailClosedDefault() {
        assertThatCode(() -> new BuyerAuthConfigurationValidator(false,"","","","")).doesNotThrowAnyException();
    }
    @Test void enabledModeRequiresCloudIngressDigestAndPepperWithoutEchoingValues() {
        String secret="secret-value-that-must-not-be-echoed-123456";
        assertThatThrownBy(() -> new BuyerAuthConfigurationValidator(true,"PUBLIC","bad",secret,"short"))
                .isInstanceOf(IllegalStateException.class).hasMessage("BUYER_AUTH_CONFIGURATION_INCOMPLETE")
                .hasMessageNotContaining(secret);
        assertThatThrownBy(() -> new BuyerAuthConfigurationValidator(true,"WECHAT_CLOUD_HOSTING_HMAC_V1","a".repeat(64),secret,secret))
                .isInstanceOf(IllegalStateException.class).hasMessage("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
        assertThatCode(() -> new BuyerAuthConfigurationValidator(true,"WECHAT_CLOUD_HOSTING_HMAC_V1","a".repeat(64),secret,"independent-ingress-secret-value-123456789"))
                .doesNotThrowAnyException();
    }
}
