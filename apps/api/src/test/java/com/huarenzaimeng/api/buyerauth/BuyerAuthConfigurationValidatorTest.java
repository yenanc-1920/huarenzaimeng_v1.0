package com.huarenzaimeng.api.buyerauth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BuyerAuthConfigurationValidatorTest {
    private static final String A="a".repeat(32),B="b".repeat(32);
    private static final String APP="wx1234567890abcdef",SECRET="s".repeat(32);
    @Test void disabledNeedsNoSecretAndEnabledRequiresOneExplicitCompleteMode(){
        assertThatCode(()->validator(false,"","","disabled","",false,"","")).doesNotThrowAnyException();
        assertThatCode(()->validator(true,A,B,"fake-only","",false,"","")).doesNotThrowAnyException();
        assertThatCode(()->validator(true,A,B,"wechat-code2session",APP,true,APP,SECRET)).doesNotThrowAnyException();
        assertThatThrownBy(()->validator(true,A,B,"disabled","",false,"","")).hasMessage("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
        assertThatThrownBy(()->validator(true,A,A,"fake-only","",false,"","")).hasMessage("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
        assertThatThrownBy(()->validator(true,A,B,"wechat-code2session",APP,false,APP,SECRET)).hasMessage("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
        assertThatThrownBy(()->validator(true,A,B,"wechat-code2session",APP,true,APP,"")).hasMessage("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
        assertThatThrownBy(()->validator(true,A,B,"wechat-code2session","wx_other_app",true,APP,SECRET)).hasMessage("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
    }
    @Test void cloudBaseSafeLinkModeRequiresExactDevEnvironmentAndFixedEndpoint(){
        assertThatCode(()->new BuyerAuthConfigurationValidator(true,A,B,"wechat-code2session",APP,true,
                WechatCode2SessionClient.CLOUDBASE_SAFELINK_ENDPOINT.toString(),
                WechatCode2SessionClient.CLOUDBASE_SAFELINK_HTTP,"dev",
                WechatCode2SessionClient.CLOUDBASE_ENVIRONMENT_ID,APP,SECRET)).doesNotThrowAnyException();
        assertThatThrownBy(()->new BuyerAuthConfigurationValidator(true,A,B,"wechat-code2session",APP,true,
                WechatCode2SessionClient.CLOUDBASE_SAFELINK_ENDPOINT.toString(),
                WechatCode2SessionClient.CLOUDBASE_SAFELINK_HTTP,"test",
                WechatCode2SessionClient.CLOUDBASE_ENVIRONMENT_ID,APP,SECRET))
                .hasMessage("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
        assertThatThrownBy(()->new BuyerAuthConfigurationValidator(true,A,B,"wechat-code2session",APP,true,
                "http://example.invalid/sns/jscode2session", WechatCode2SessionClient.CLOUDBASE_SAFELINK_HTTP,
                "dev",WechatCode2SessionClient.CLOUDBASE_ENVIRONMENT_ID,APP,SECRET))
                .hasMessage("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
    }
    private static BuyerAuthConfigurationValidator validator(boolean enabled,String pepper,String codePepper,
            String mode,String expectedAppId,boolean wechatEnabled,String appId,String secret){
        return new BuyerAuthConfigurationValidator(enabled,pepper,codePepper,mode,expectedAppId,wechatEnabled,
                WechatCode2SessionClient.OFFICIAL_ENDPOINT.toString(), WechatCode2SessionClient.OFFICIAL_HTTPS,
                "", "", appId,secret);
    }
}
