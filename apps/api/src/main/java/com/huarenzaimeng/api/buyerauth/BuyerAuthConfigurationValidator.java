package com.huarenzaimeng.api.buyerauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("release-mysql")
final class BuyerAuthConfigurationValidator {
    BuyerAuthConfigurationValidator(
            @Value("${hz.buyer-auth.enabled:false}") boolean enabled,
            @Value("${hz.buyer-auth.identity-pepper:}") String pepper,
            @Value("${hz.buyer-auth.code-pepper:}") String codePepper,
            @Value("${hz.buyer-auth.provider-mode:disabled}") String providerMode,
            @Value("${hz.buyer-auth.expected-app-id-ref:}") String expectedAppId,
            @Value("${hz.buyer-auth.wechat.enabled:false}") boolean wechatEnabled,
            @Value("${hz.buyer-auth.wechat.app-id:}") String appId,
            @Value("${hz.buyer-auth.wechat.app-secret:}") String appSecret) {
        if (!enabled) return;
        boolean fake = "fake-only".equals(providerMode) && !wechatEnabled;
        boolean real = "wechat-code2session".equals(providerMode) && wechatEnabled
                && appId != null && appId.equals(expectedAppId)
                && appId.matches("[A-Za-z0-9_-]{8,64}")
                && appSecret != null && appSecret.matches("[A-Za-z0-9_-]{16,128}");
        if ((!fake && !real) || pepper.length() < 32 || codePepper.length() < 32 || codePepper.equals(pepper)) {
            throw new IllegalStateException("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
        }
    }
}
