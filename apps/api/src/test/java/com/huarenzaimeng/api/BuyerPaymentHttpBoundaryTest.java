package com.huarenzaimeng.api;

import com.huarenzaimeng.api.buyerauth.BuyerSessionFilter;
import com.huarenzaimeng.api.buyerauth.BuyerSessionPrincipal;
import com.huarenzaimeng.api.payment.DisabledWeChatPayAdapter;
import com.huarenzaimeng.api.payment.WeChatPayCoordinator;
import com.huarenzaimeng.api.payment.WeChatPayPort;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.*;

class BuyerPaymentHttpBoundaryTest {
    private static final String DIGEST="a".repeat(64);

    @Test void disabledAdapterFailsClosedWithoutCallingCoordinator(){
        WeChatPayCoordinator coordinator=mock(WeChatPayCoordinator.class);
        ReleaseBuyerFlowController controller=new ReleaseBuyerFlowController(mock(FlowStore.class),coordinator,new DisabledWeChatPayAdapter());
        var response=controller.payment(request("BUYER-1"),"ORDER-1",paymentRequest(DIGEST));
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        verifyNoInteractions(coordinator);
    }

    @Test void repeatedHttpCommandPassesSameDigestAndTrustedSubjectToCoordinator(){
        WeChatPayCoordinator coordinator=mock(WeChatPayCoordinator.class);
        WeChatPayPort provider=mock(WeChatPayPort.class);
        when(provider.available()).thenReturn(true);
        var view=new WeChatPayCoordinator.View("ORDER-1",DIGEST,"BUYER-1","QUOTE-1","b".repeat(64),1000,"CNY",null,WeChatPayCoordinator.State.UNKNOWN,"OFF",2,Instant.now().plusSeconds(60),0,1,Instant.now());
        when(coordinator.createForBuyer(any(),eq("BUYER-1"))).thenReturn(view);
        ReleaseBuyerFlowController controller=new ReleaseBuyerFlowController(mock(FlowStore.class),coordinator,provider);
        controller.payment(request("BUYER-1"),"ORDER-1",paymentRequest(DIGEST));
        controller.payment(request("BUYER-1"),"ORDER-1",paymentRequest(DIGEST));
        verify(coordinator,times(2)).createForBuyer(new WeChatPayCoordinator.CreateCommand("ORDER-1",DIGEST),"BUYER-1");
    }

    @Test void wrongBuyerIsRejectedForRefundStatusAndProviderQueryIsNotInvoked(){
        WeChatPayCoordinator coordinator=mock(WeChatPayCoordinator.class);
        WeChatPayPort provider=mock(WeChatPayPort.class); when(provider.available()).thenReturn(true);
        when(coordinator.refundStatusForBuyer("ORDER-1","REFUND-1","ATTACKER"))
                .thenThrow(new WeChatPayCoordinator.Conflict("PAYMENT_REFUND_OWNERSHIP_CONFLICT"));
        ReleaseBuyerFlowController controller=new ReleaseBuyerFlowController(mock(FlowStore.class),coordinator,provider);
        var response=controller.paymentConflict(catchThrowableOfType(()->controller.refundStatus(request("ATTACKER"),"ORDER-1","REFUND-1"),WeChatPayCoordinator.Conflict.class));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        verify(coordinator).refundStatusForBuyer("ORDER-1","REFUND-1","ATTACKER");
        verifyNoMoreInteractions(provider);
    }

    @Test void wrongBuyerRefundAndProviderQueriesFailBeforeProviderAvailabilityCheck(){
        WeChatPayCoordinator coordinator=mock(WeChatPayCoordinator.class);
        WeChatPayPort provider=mock(WeChatPayPort.class);
        when(coordinator.statusForBuyer("ORDER-1","ATTACKER"))
                .thenThrow(new WeChatPayCoordinator.Conflict("PAYMENT_ORDER_OWNERSHIP_CONFLICT"));
        when(coordinator.refundStatusForBuyer("ORDER-1","REFUND-1","ATTACKER"))
                .thenThrow(new WeChatPayCoordinator.Conflict("PAYMENT_REFUND_OWNERSHIP_CONFLICT"));
        ReleaseBuyerFlowController controller=new ReleaseBuyerFlowController(mock(FlowStore.class),coordinator,provider);

        assertThat(catchThrowableOfType(()->controller.queryPayment(request("ATTACKER"),"ORDER-1"),WeChatPayCoordinator.Conflict.class).getMessage())
                .isEqualTo("PAYMENT_ORDER_OWNERSHIP_CONFLICT");
        assertThat(catchThrowableOfType(()->controller.refund(request("ATTACKER"),"ORDER-1",new ReleaseBuyerFlowController.RefundRequest("REFUND-1",100,DIGEST)),WeChatPayCoordinator.Conflict.class).getMessage())
                .isEqualTo("PAYMENT_ORDER_OWNERSHIP_CONFLICT");
        assertThat(catchThrowableOfType(()->controller.queryRefund(request("ATTACKER"),"ORDER-1","REFUND-1"),WeChatPayCoordinator.Conflict.class).getMessage())
                .isEqualTo("PAYMENT_REFUND_OWNERSHIP_CONFLICT");
        verifyNoInteractions(provider);
    }

    private static MockHttpServletRequest request(String buyer){MockHttpServletRequest r=new MockHttpServletRequest();r.setAttribute(BuyerSessionFilter.BUYER,new BuyerSessionPrincipal(buyer,"SESSION-1"));return r;}
    private static ReleaseBuyerFlowController.PaymentRequest paymentRequest(String digest){return new ReleaseBuyerFlowController.PaymentRequest("CMD","IDEMP","READY",1,"AUTH",1,1,digest);}
}
