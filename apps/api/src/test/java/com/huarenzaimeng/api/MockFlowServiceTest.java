package com.huarenzaimeng.api;

import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.OrderState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockFlowServiceTest {
    private static final String SUBJECT = "SUBJECT-TEST";
    private final MockFlowService service = new MockFlowService(new InMemoryFlowStore());

    @Test
    void completesSyntheticHappyPathAndRecoversProjection() {
        var quote = service.createQuote(SUBJECT, "8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.CONFIRMED);
        var order = service.createOrder(SUBJECT, quote.quoteRef(), "CMD-ORDER-1", "IDEM-ORDER-1");
        var paid = service.confirmMockPayment(SUBJECT, order.orderRef(), "CMD-PAY-1", "IDEM-PAY-1", 1L, 1L);
        var completed = service.completeMockTopup(SUBJECT, order.orderRef(), "CMD-TOPUP-1", "IDEM-TOPUP-1",
                2L, 2L, MnpState.CONFIRMED);

        assertThat(paid.facts().payment()).isEqualTo("CONFIRMED");
        assertThat(completed.orderState()).isEqualTo(OrderState.COMPLETED);
        assertThat(service.getOrder(SUBJECT, order.orderRef())).isEqualTo(completed);
    }

    @Test
    void prepaymentUnknownDoesNotCreateQuote() {
        assertThatThrownBy(() -> service.createQuote(SUBJECT, "8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.UNKNOWN))
                .hasMessage("PREPAY_MNP_NOT_CONFIRMED");
    }

    @Test
    void postPaymentUnknownDoesNotAutoTopup() {
        var quote = service.createQuote(SUBJECT, "8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.CONFIRMED);
        var order = service.createOrder(SUBJECT, quote.quoteRef(), "CMD-ORDER-2", "IDEM-ORDER-2");
        service.confirmMockPayment(SUBJECT, order.orderRef(), "CMD-PAY-2", "IDEM-PAY-2", 1L, 1L);
        var review = service.completeMockTopup(SUBJECT, order.orderRef(), "CMD-TOPUP-2", "IDEM-TOPUP-2",
                2L, 2L, MnpState.UNKNOWN);

        assertThat(review.orderState()).isEqualTo(OrderState.TOPUP_REVIEW);
        assertThat(review.facts().upstreamDebit()).isEqualTo("UNKNOWN");
        assertThat(review.facts().delivery()).isEqualTo("UNKNOWN");
    }

    @Test
    void duplicateOrderCommandReturnsSameOrder() {
        var quote = service.createQuote(SUBJECT, "8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.CONFIRMED);
        var first = service.createOrder(SUBJECT, quote.quoteRef(), "CMD-SAME", "IDEM-SAME");
        var second = service.createOrder(SUBJECT, quote.quoteRef(), "CMD-SAME", "IDEM-SAME");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void sameBusinessKeyAndFingerprintWithDifferentCommandReplaysOriginal() {
        var quote = service.createQuote(SUBJECT, "8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.CONFIRMED);
        var first = service.createOrder(SUBJECT, quote.quoteRef(), "CMD-A", "IDEM-A");
        var replay = service.createOrder(SUBJECT, quote.quoteRef(), "CMD-B", "IDEM-B");
        assertThat(replay.orderRef()).isEqualTo(first.orderRef());
        assertThat(replay.aggregateVersion()).isEqualTo(1);
    }

    @Test
    void staleProjectionVersionDoesNotAdvancePayment() {
        var quote = service.createQuote(SUBJECT, "8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.CONFIRMED);
        var order = service.createOrder(SUBJECT, quote.quoteRef(), "CMD-ORDER-V", "IDEM-ORDER-V");
        assertThatThrownBy(() -> service.confirmMockPayment(SUBJECT, order.orderRef(), "CMD-PAY-V", "IDEM-PAY-V", 0L, 1L))
                .hasMessage("PROJECTION_VERSION_CONFLICT");
        assertThat(service.getOrder(SUBJECT, order.orderRef()).facts().payment()).isEqualTo("ABSENT_CONFIRMED");
    }
}
