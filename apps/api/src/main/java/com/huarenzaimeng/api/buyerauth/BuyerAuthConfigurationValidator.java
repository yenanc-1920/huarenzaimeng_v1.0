package com.huarenzaimeng.api.buyerauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("release-mysql")
final class BuyerAuthConfigurationValidator {
    BuyerAuthConfigurationValidator(
            @Value("${hz.buyer-auth.enabled:false}") boolean enabled,
            @Value("${hz.buyer-auth.trusted-ingress:}") String ingress,
            @Value("${hz.buyer-auth.expected-appid-digest:}") String appidDigest,
            @Value("${hz.buyer-auth.identity-pepper:}") String pepper,
            @Value("${hz.buyer-auth.ingress-hmac-secret:}") String ingressSecret) {
        if (!enabled) return;
        if (!"WECHAT_CLOUD_HOSTING_HMAC_V1".equals(ingress)
                || !appidDigest.matches("[0-9a-f]{64}") || pepper.length() < 32
                || ingressSecret.length() < 32 || ingressSecret.equals(pepper)) {
            throw new IllegalStateException("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
        }
    }
}
