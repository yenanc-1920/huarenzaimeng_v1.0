package com.huarenzaimeng.api;

import com.huarenzaimeng.api.buyerauth.BuyerSessionFilter;
import com.huarenzaimeng.api.buyerauth.BuyerSessionPrincipal;
import com.huarenzaimeng.api.payment.WeChatPayCoordinator;
import com.huarenzaimeng.api.payment.WeChatPayPort;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReleaseQuoteOrderControllerTest {
    @Test void quoteAndOrderPassOnlyAuthenticatedSubjectAndHeaderIdempotencyKey(){
        ReleaseQuoteOrderService service=mock(ReleaseQuoteOrderService.class);
        ReleaseBuyerFlowController controller=new ReleaseBuyerFlowController(mock(FlowStore.class),mock(WeChatPayCoordinator.class),mock(WeChatPayPort.class),service);
        Instant validUntil=Instant.now().plusSeconds(300);
        when(service.createQuote("BUYER-1","QUOTE-IDEMP","QUOTE-REQUEST","01712345678","PRODUCT-1"))
                .thenReturn(new ReleaseQuoteOrderService.QuoteView("QUOTE-1","QUOTE-REQUEST","a".repeat(64),"b".repeat(64),"017 **** 678","GP","PRODUCT-1","PRICE-1",new BigDecimal("12.34"),"CNY",7,validUntil,"c".repeat(64)));
        when(service.createOrder("BUYER-1","ORDER-IDEMP","ORDER-REQUEST","QUOTE-1"))
                .thenReturn(new ReleaseQuoteOrderService.OrderView("ORDER-1","QUOTE-1","ORDER-REQUEST","d".repeat(64),"e".repeat(64),"CREATED","NOT_STARTED","NOT_STARTED","NOT_REQUESTED",1,1));

        controller.quote(request("BUYER-1"),"QUOTE-IDEMP",new ReleaseBuyerFlowController.QuoteRequest("QUOTE-REQUEST","01712345678","PRODUCT-1"));
        controller.order(request("BUYER-1"),"ORDER-IDEMP",new ReleaseBuyerFlowController.OrderRequest("ORDER-REQUEST","QUOTE-1"));

        verify(service).createQuote("BUYER-1","QUOTE-IDEMP","QUOTE-REQUEST","01712345678","PRODUCT-1");
        verify(service).createOrder("BUYER-1","ORDER-IDEMP","ORDER-REQUEST","QUOTE-1");
    }

    @Test void quoteAndOrderRejectMissingBuyerSessionBeforeJdbcService(){
        ReleaseQuoteOrderService service=mock(ReleaseQuoteOrderService.class);
        ReleaseBuyerFlowController controller=new ReleaseBuyerFlowController(mock(FlowStore.class),mock(WeChatPayCoordinator.class),mock(WeChatPayPort.class),service);
        MockHttpServletRequest missing=new MockHttpServletRequest();

        assertThatThrownBy(()->controller.quote(missing,"IDEMP",new ReleaseBuyerFlowController.QuoteRequest("R","01712345678","P")))
                .isInstanceOf(FlowRejectedException.class).hasMessage("BUYER_SESSION_REQUIRED");
        assertThatThrownBy(()->controller.order(missing,"IDEMP",new ReleaseBuyerFlowController.OrderRequest("R","Q")))
                .isInstanceOf(FlowRejectedException.class).hasMessage("BUYER_SESSION_REQUIRED");
        verifyNoInteractions(service);
    }

    private static MockHttpServletRequest request(String buyer){
        MockHttpServletRequest request=new MockHttpServletRequest();
        request.setAttribute(BuyerSessionFilter.BUYER,new BuyerSessionPrincipal(buyer,"SESSION-1"));
        return request;
    }
}
