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
            @Value("${hz.buyer-auth.wechat.endpoint:https://api.weixin.qq.com/sns/jscode2session}") String endpoint,
            @Value("${hz.buyer-auth.wechat.transport-mode:official-https}") String transportMode,
            @Value("${hz.environment.name:}") String environmentName,
            @Value("${TCB_ENV_ID:}") String cloudBaseEnvironmentId,
            @Value("${hz.buyer-auth.wechat.app-id:}") String appId,
            @Value("${hz.buyer-auth.wechat.app-secret:}") String appSecret) {
        if (!enabled) return;
        boolean fake = "fake-only".equals(providerMode) && !wechatEnabled;
        boolean officialHttps = WechatCode2SessionClient.OFFICIAL_HTTPS.equals(transportMode)
                && WechatCode2SessionClient.OFFICIAL_ENDPOINT.toString().equals(endpoint);
        boolean cloudBaseSafeLink = WechatCode2SessionClient.CLOUDBASE_SAFELINK_HTTP.equals(transportMode)
                && WechatCode2SessionClient.CLOUDBASE_SAFELINK_ENDPOINT.toString().equals(endpoint)
                && "dev".equals(environmentName)
                && WechatCode2SessionClient.CLOUDBASE_ENVIRONMENT_ID.equals(cloudBaseEnvironmentId);
        boolean real = "wechat-code2session".equals(providerMode) && wechatEnabled
                && (officialHttps || cloudBaseSafeLink)
                && appId != null && appId.equals(expectedAppId)
                && appId.matches("[A-Za-z0-9_-]{8,64}")
                && appSecret != null && appSecret.matches("[A-Za-z0-9_-]{16,128}");
        if ((!fake && !real) || pepper.length() < 32 || codePepper.length() < 32 || codePepper.equals(pepper)) {
            throw new IllegalStateException("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
        }
    }
}
