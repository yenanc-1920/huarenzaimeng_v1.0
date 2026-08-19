package com.huarenzaimeng.api.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WeChatPayCallbackHttpBoundaryTest {
    @Test void disabledAdapterRejectsOpaqueNotificationBeforeCoordinator(){
        WeChatPayCoordinator coordinator=mock(WeChatPayCoordinator.class);
        WeChatPayNotificationController controller=new WeChatPayNotificationController(coordinator,new DisabledWeChatPayAdapter());
        var response=controller.receive("1","NONCE","SIGNATURE","SERIAL","N1","CIPHERTEXT");
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        verifyNoInteractions(coordinator);
    }
}
