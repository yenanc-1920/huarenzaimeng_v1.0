package com.huarenzaimeng.api;

import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.OrderState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockFlowQualityGateTest {
    private static final String SUBJECT = "SUBJECT-QUALITY";
    private final MockFlowService service = new MockFlowService(new InMemoryFlowStore());

    @Test
    void samePaymentCommandReplaysExactlyOneProjection() {
        var order = newOrder("PAY-REPLAY");

        var first = service.confirmMockPayment(SUBJECT, order.orderRef(), "CMD-PAY-R", "IDEM-PAY-R", 1L, 1L);
        var replay = service.confirmMockPayment(SUBJECT, order.orderRef(), "CMD-PAY-R", "IDEM-PAY-R", 1L, 1L);

        assertThat(replay).isEqualTo(first);
        assertThat(service.getOrder(SUBJECT, order.orderRef()).projectionVersion()).isEqualTo(2L);
    }

    @Test
    void sameCommandWithDifferentTopupParametersIsRejectedWithoutAdvancing() {
        var order = paidOrder("TOPUP-CONFLICT");
        var unknown = service.completeMockTopup(SUBJECT, order.orderRef(), "CMD-TOP-C", "IDEM-TOP-C",
                2L, 2L, MnpState.UNKNOWN);

        assertThatThrownBy(() -> service.completeMockTopup(SUBJECT, order.orderRef(), "CMD-TOP-C", "IDEM-TOP-C",
                3L, 3L, MnpState.CONFIRMED)).hasMessage("IDEMPOTENCY_CONFLICT");
        assertThat(service.getOrder(SUBJECT, order.orderRef())).isEqualTo(unknown);
    }

    @Test
    void unknownCannotBeBlindlyReplayedWithFreshKeys() {
        var order = paidOrder("UNKNOWN-NO-REPLAY");
        var unknown = service.completeMockTopup(SUBJECT, order.orderRef(), "CMD-TOP-U1", "IDEM-TOP-U1",
                2L, 2L, MnpState.UNKNOWN);

        assertThatThrownBy(() -> service.completeMockTopup(SUBJECT, order.orderRef(), "CMD-TOP-U2", "IDEM-TOP-U2",
                unknown.projectionVersion(), unknown.aggregateVersion(), MnpState.UNKNOWN))
                .hasMessage("IDEMPOTENCY_CONFLICT");
        assertThat(service.getOrder(SUBJECT, order.orderRef())).isEqualTo(unknown);
    }

    @Test
    void topupBeforeTrustedPaymentCreatesNoProgress() {
        var order = newOrder("B1-NO-W");

        assertThatThrownBy(() -> service.completeMockTopup(SUBJECT, order.orderRef(), "CMD-TOP-NW", "IDEM-TOP-NW",
                1L, 1L, MnpState.CONFIRMED)).hasMessage("PAYMENT_NOT_CONFIRMED");
        assertThat(service.getOrder(SUBJECT, order.orderRef())).isEqualTo(order);
    }

    @Test
    void staleTopupVersionCreatesNoProgress() {
        var paid = paidOrder("TOPUP-STALE");

        assertThatThrownBy(() -> service.completeMockTopup(SUBJECT, paid.orderRef(), "CMD-TOP-S", "IDEM-TOP-S",
                1L, 2L, MnpState.CONFIRMED)).hasMessage("PROJECTION_VERSION_CONFLICT");
        assertThat(service.getOrder(SUBJECT, paid.orderRef())).isEqualTo(paid);
    }

    @Test
    void confirmedTopupAdvancesExactlyOnceAndReplaysWithoutDuplication() {
        var paid = paidOrder("TOPUP-ONCE");

        var completed = service.completeMockTopup(SUBJECT, paid.orderRef(), "CMD-TOP-1", "IDEM-TOP-1",
                2L, 2L, MnpState.CONFIRMED);
        var replay = service.completeMockTopup(SUBJECT, paid.orderRef(), "CMD-TOP-1", "IDEM-TOP-1",
                2L, 2L, MnpState.CONFIRMED);

        assertThat(completed.orderState()).isEqualTo(OrderState.COMPLETED);
        assertThat(completed.projectionVersion()).isEqualTo(3L);
        assertThat(replay).isEqualTo(completed);
        assertThat(service.getOrder(SUBJECT, paid.orderRef())).isEqualTo(completed);
    }

    @Test
    void sameKeysAreIndependentAcrossProjectSubjects() {
        String other = "SUBJECT-OTHER";
        var quoteA = service.createQuote(SUBJECT, "8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.CONFIRMED);
        var quoteB = service.createQuote(other, "8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.CONFIRMED);
        var orderA = service.createOrder(SUBJECT, quoteA.quoteRef(), "CMD-SHARED", "IDEM-SHARED");
        var orderB = service.createOrder(other, quoteB.quoteRef(), "CMD-SHARED", "IDEM-SHARED");
        assertThat(orderA.orderRef()).isNotEqualTo(orderB.orderRef());
    }

    @Test
    void sameSemanticPaymentWithFreshKeysIsRejectedBeforeVersionChecks() {
        var paid = paidOrder("SEMANTIC-REKEY");
        assertThatThrownBy(() -> service.confirmMockPayment(SUBJECT, paid.orderRef(),
                "CMD-PAY-NEW", "IDEM-PAY-NEW", 999L, 999L))
                .hasMessage("IDEMPOTENCY_CONFLICT");
        assertThat(service.getOrder(SUBJECT, paid.orderRef())).isEqualTo(paid);
    }

    @Test
    void aggregateAndProjectionVersionsAreRejectedSeparately() {
        var order = newOrder("VERSION-SPLIT");
        assertThatThrownBy(() -> service.confirmMockPayment(SUBJECT, order.orderRef(),
                "CMD-PAY-AGG", "IDEM-PAY-AGG", 1L, 0L))
                .hasMessage("AGGREGATE_VERSION_CONFLICT");
        assertThatThrownBy(() -> service.confirmMockPayment(SUBJECT, order.orderRef(),
                "CMD-PAY-PROJ", "IDEM-PAY-PROJ", 0L, 1L))
                .hasMessage("PROJECTION_VERSION_CONFLICT");
        assertThat(service.getOrder(SUBJECT, order.orderRef())).isEqualTo(order);
    }

    private com.huarenzaimeng.core.ProjectProjection newOrder(String suffix) {
        var quote = service.createQuote(SUBJECT, "8801700000000", "SYN-OP", "SYN-PRODUCT", MnpState.CONFIRMED);
        return service.createOrder(SUBJECT, quote.quoteRef(), "CMD-ORDER-" + suffix, "IDEM-ORDER-" + suffix);
    }

    private com.huarenzaimeng.core.ProjectProjection paidOrder(String suffix) {
        var order = newOrder(suffix);
        return service.confirmMockPayment(SUBJECT, order.orderRef(), "CMD-PAY-" + suffix,
                "IDEM-PAY-" + suffix, 1L, 1L);
    }
}
