package com.huarenzaimeng.api.buyerauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WeChatIdentityBoundaryTest {
    @Test void legacyPortRemainsAssignableToFormalBoundary(){WechatCode2SessionPort legacy=c->new WeChatIdentityPort.Unknown("FIXTURE");assertThat(legacy).isInstanceOf(WeChatIdentityPort.class);assertThat(legacy.exchange(new WeChatIdentityPort.Command("one-time","CALL-1"))).isInstanceOf(WeChatIdentityPort.Unknown.class);}
    @Test void releaseClientIsFailClosedWithoutNetworkOrCredentials(){
        WeChatIdentityPort port=new WechatCode2SessionClient(request->{throw new AssertionError("transport must not run");},new ObjectMapper(),false,
                WechatCode2SessionClient.OFFICIAL_ENDPOINT.toString(),"","",2000,3000);
        assertThat(port.exchange(new WeChatIdentityPort.Command("one-time","CALL-0001"))).isEqualTo(new WeChatIdentityPort.Unknown("REAL_PROVIDER_ADAPTER_DISABLED"));
    }
}
