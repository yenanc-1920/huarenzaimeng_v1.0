package com.huarenzaimeng.api.buyerauth;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BuyerSessionFilterBoundaryTest {
    @Test void paymentAndTopupRoutesRejectMissingBearerBeforeController() throws Exception {
        BuyerAuthService auth=mock(BuyerAuthService.class);
        BuyerSessionFilter filter=new BuyerSessionFilter(auth);
        for(String path:new String[]{"/buyer-api/v1/orders/O-1/payment-intents","/buyer-api/v1/orders/O-1/topup"}){
            MockHttpServletRequest request=new MockHttpServletRequest("POST",path);
            MockHttpServletResponse response=new MockHttpServletResponse();
            FilterChain chain=mock(FilterChain.class);
            when(auth.authenticate(null)).thenReturn(Optional.empty());
            filter.doFilter(request,response,chain);
            assertThat(response.getStatus()).isEqualTo(401);
            verifyNoInteractions(chain);
        }
    }

    @Test void validBearerCreatesOnlyTrustedPrincipalAttribute() throws Exception {
        BuyerAuthService auth=mock(BuyerAuthService.class);
        when(auth.authenticate("TOKEN")).thenReturn(Optional.of(new BuyerAuthStore.BuyerPrincipal(
                BuyerAuthStore.Eligibility.ELIGIBLE,"BUYER-1","SESSION-1")));
        BuyerSessionFilter filter=new BuyerSessionFilter(auth);
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/buyer-api/v1/orders/O-1/payment-intents/result");
        request.addHeader("Authorization","Bearer TOKEN");
        MockHttpServletResponse response=new MockHttpServletResponse();
        FilterChain chain=mock(FilterChain.class);
        filter.doFilter(request,response,chain);
        assertThat(request.getAttribute(BuyerSessionFilter.BUYER)).isEqualTo(new BuyerSessionPrincipal("BUYER-1","SESSION-1"));
        verify(chain).doFilter(request,response);
    }
}
