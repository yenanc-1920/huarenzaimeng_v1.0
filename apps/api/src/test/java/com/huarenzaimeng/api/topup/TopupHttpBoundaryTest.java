package com.huarenzaimeng.api.topup;

import com.huarenzaimeng.api.buyerauth.BuyerSessionFilter;
import com.huarenzaimeng.api.buyerauth.BuyerSessionPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class TopupHttpBoundaryTest {
    @Test void disabledAdapterRejectsBuyerSubmitAndSupplierCallback(){
        TopupCoordinator coordinator=mock(TopupCoordinator.class);
        TopupProviderPort disabled=WinlaTopupAdapter.disabled();
        BuyerTopupController buyer=new BuyerTopupController(coordinator,disabled);
        MockHttpServletRequest request=new MockHttpServletRequest();
        request.setAttribute(BuyerSessionFilter.BUYER,new BuyerSessionPrincipal("BUYER-1","SESSION-1"));
        var buyerResponse=buyer.submit(request,"ORDER-1",new BuyerTopupController.SubmitRequest("REQUEST-1","a".repeat(64)));
        var callbackResponse=new WinlaTopupCallbackController(coordinator,disabled).receive("order_no=P1&sign=S");
        assertThat(buyerResponse.getStatusCode().value()).isEqualTo(503);
        assertThat(callbackResponse.getStatusCode().value()).isEqualTo(503);
        verifyNoInteractions(coordinator);
    }

    @Test void wrongBuyerStatusAndQueryFailBeforeAnyProviderOperation(){
        TopupCoordinator coordinator=mock(TopupCoordinator.class);
        TopupProviderPort provider=mock(TopupProviderPort.class);when(provider.available()).thenReturn(true);
        when(coordinator.statusForBuyer("ORDER-1","ATTACKER")).thenThrow(new TopupCoordinator.Conflict("TOPUP_BUYER_OWNERSHIP_CONFLICT"));
        BuyerTopupController controller=new BuyerTopupController(coordinator,provider);
        MockHttpServletRequest request=new MockHttpServletRequest();request.setAttribute(BuyerSessionFilter.BUYER,new BuyerSessionPrincipal("ATTACKER","S"));
        assertThatThrownBy(()->controller.status(request,"ORDER-1")).isInstanceOf(TopupCoordinator.Conflict.class);
        assertThatThrownBy(()->controller.query(request,"ORDER-1")).isInstanceOf(TopupCoordinator.Conflict.class);
        verify(coordinator,times(2)).statusForBuyer("ORDER-1","ATTACKER");
        verifyNoInteractions(provider);
    }

    @Test void supplierCallbackAcceptsOpaqueFormBodyAndReturnsPlainSuccessOnlyAfterCoordinatorAccepts(){
        TopupCoordinator coordinator=mock(TopupCoordinator.class);TopupProviderPort provider=mock(TopupProviderPort.class);when(provider.available()).thenReturn(true);
        String body="order_no=P1&user_order_no=ORDER1&status=200&order_money=9954&sign=abc";
        var response=new WinlaTopupCallbackController(coordinator,provider).receive(body);
        assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getBody()).isEqualTo("success");
        ArgumentCaptor<TopupProviderPort.CallbackEnvelope> envelope=ArgumentCaptor.forClass(TopupProviderPort.CallbackEnvelope.class);verify(coordinator).callback(envelope.capture());assertThat(envelope.getValue().body()).isEqualTo(body);
    }
}
