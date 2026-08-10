package com.huarenzaimeng.api.buyerauth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BuyerAuthConfigurationValidatorTest {
    private static final String A="a".repeat(32),B="b".repeat(32);
    @Test void disabledNeedsNoSecretAndEnabledOnlyAllowsExplicitFakeMode(){
        assertThatCode(()->new BuyerAuthConfigurationValidator(false,"","","disabled")).doesNotThrowAnyException();
        assertThatCode(()->new BuyerAuthConfigurationValidator(true,A,B,"fake-only")).doesNotThrowAnyException();
        assertThatThrownBy(()->new BuyerAuthConfigurationValidator(true,A,B,"disabled")).hasMessage("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
        assertThatThrownBy(()->new BuyerAuthConfigurationValidator(true,A,A,"fake-only")).hasMessage("BUYER_AUTH_CONFIGURATION_INCOMPLETE");
    }
}
