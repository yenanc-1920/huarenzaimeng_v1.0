package com.huarenzaimeng.api;

import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.OrderState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockFlowServiceTest {
    private final MockFlowService service = new MockFlowService();

    @Test
    void completesSyntheticHappyPathAndRecoversProjection() {
        var quote = service.createQuote("8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.CONFIRMED);
        var order = service.createOrder(quote.quoteRef(), "CMD-ORDER-1");
        var paid = service.confirmMockPayment(order.orderRef(), "CMD-PAY-1");
        var completed = service.completeMockTopup(order.orderRef(), MnpState.CONFIRMED);

        assertThat(paid.paymentState()).isEqualTo("CONFIRMED");
        assertThat(completed.orderState()).isEqualTo(OrderState.COMPLETED);
        assertThat(service.getOrder(order.orderRef())).isEqualTo(completed);
    }

    @Test
    void prepaymentUnknownDoesNotCreateQuote() {
        assertThatThrownBy(() -> service.createQuote("8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.UNKNOWN))
                .hasMessage("PREPAY_MNP_NOT_CONFIRMED");
    }

    @Test
    void postPaymentUnknownDoesNotAutoTopup() {
        var quote = service.createQuote("8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.CONFIRMED);
        var order = service.createOrder(quote.quoteRef(), "CMD-ORDER-2");
        service.confirmMockPayment(order.orderRef(), "CMD-PAY-2");
        var review = service.completeMockTopup(order.orderRef(), MnpState.UNKNOWN);

        assertThat(review.orderState()).isEqualTo(OrderState.TOPUP_REVIEW);
        assertThat(review.upstreamDebitState()).isEqualTo("UNKNOWN");
        assertThat(review.deliveryState()).isEqualTo("UNKNOWN");
    }

    @Test
    void duplicateOrderCommandReturnsSameOrder() {
        var quote = service.createQuote("8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.CONFIRMED);
        var first = service.createOrder(quote.quoteRef(), "CMD-SAME");
        var second = service.createOrder(quote.quoteRef(), "CMD-SAME");
        assertThat(second).isEqualTo(first);
    }
}
