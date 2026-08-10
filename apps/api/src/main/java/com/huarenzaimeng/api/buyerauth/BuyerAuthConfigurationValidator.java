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
            @Value("${hz.buyer-auth.provider-mode:disabled}") String providerMode) {
        if (!enabled) return;
        if (!"fake-only".equals(providerMode) || pepper.length() < 32 || codePepper.length() < 32 || codePepper.equals(pepper)) {
            throw new IllegalStateException("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
        }
    }
}
