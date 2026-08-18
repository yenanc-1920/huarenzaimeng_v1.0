package com.huarenzaimeng.api.topup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class TopupCoordinationConfigurationTest {
    private final TopupCoordinationConfiguration configuration=new TopupCoordinationConfiguration();
    private final ProviderExchangeStore store=new ProviderExchangeStore(){public SaveResult record(Attempt a){return SaveResult.CREATED;}public Original requireOriginal(String p,String o){throw new Conflict("NONE");}};
    @Test void defaultIsDisabledAndRequiresNoSecrets(){assertThat(configuration.topupProviderPort(new MockEnvironment(),store,new ObjectMapper()).available()).isFalse();}
    @Test void partialEnableFailsClosed(){MockEnvironment env=new MockEnvironment().withProperty("HZ_WINLA_ENABLED","true");assertThatThrownBy(()->configuration.topupProviderPort(env,store,new ObjectMapper())).hasMessage("WINLA_MODE_SWITCH_MISMATCH");}
    @Test void realModeRequiresUidAndApiKey(){MockEnvironment env=new MockEnvironment().withProperty("HZ_TOPUP_PROVIDER_MODE","winla").withProperty("HZ_WINLA_ENABLED","true");assertThatThrownBy(()->configuration.topupProviderPort(env,store,new ObjectMapper())).hasMessage("HZ_WINLA_UID_REQUIRED");}
    @Test void completeExplicitRealModeBuildsAvailablePortWithoutNetworkCall(){MockEnvironment env=new MockEnvironment().withProperty("HZ_TOPUP_PROVIDER_MODE","winla").withProperty("HZ_WINLA_ENABLED","true").withProperty("HZ_WINLA_UID","merchant").withProperty("HZ_WINLA_API_KEY","0123456789abcdef");assertThat(configuration.topupProviderPort(env,store,new ObjectMapper()).available()).isTrue();}
}
