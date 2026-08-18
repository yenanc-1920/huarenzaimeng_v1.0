package com.huarenzaimeng.api.buyerauth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WeChatIdentityBoundaryTest {
    @Test void legacyPortRemainsAssignableToFormalBoundary(){WechatCode2SessionPort legacy=c->new WeChatIdentityPort.Unknown("FIXTURE");assertThat(legacy).isInstanceOf(WeChatIdentityPort.class);assertThat(legacy.exchange(new WeChatIdentityPort.Command("one-time","CALL-1"))).isInstanceOf(WeChatIdentityPort.Unknown.class);}
    @Test void releaseClientIsFailClosedWithoutNetworkOrCredentials(){WeChatIdentityPort port=new WechatCode2SessionClient();assertThat(port.exchange(new WeChatIdentityPort.Command("one-time","CALL-1"))).isEqualTo(new WeChatIdentityPort.Unknown("REAL_PROVIDER_ADAPTER_DISABLED"));}
}
