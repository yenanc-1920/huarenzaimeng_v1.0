package com.huarenzaimeng.api;

import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.OrderState;
import com.huarenzaimeng.core.AllowedAction;
import com.huarenzaimeng.core.PriceSnapshot;
import com.huarenzaimeng.core.ProjectProjection;
import com.huarenzaimeng.core.ProjectionFacts;
import com.huarenzaimeng.core.Quote;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class MockFlowService {
    private final FlowStore store;

    public MockFlowService(FlowStore store) {
        this.store = store;
    }

    public Quote createQuote(String projectSubjectRef, String phone, String operatorCode, String productCode,
                             MnpState mnpState) {
        if (mnpState != MnpState.CONFIRMED) {
            throw new FlowRejectedException("PREPAY_MNP_NOT_CONFIRMED");
        }
        String quoteRef = "Q-" + UUID.randomUUID();
        Quote quote = new Quote(quoteRef, mask(phone), operatorCode, productCode,
                1_000L, "CNY", Instant.now().plus(10, ChronoUnit.MINUTES));
        store.saveQuote(projectSubjectRef, quote);
        return quote;
    }

    public ProjectProjection createOrder(String projectSubjectRef, String quoteRef, String commandId,
                                         String idempotencyKey) {
        Quote quote = store.requireQuote(projectSubjectRef, quoteRef);
        if (quote.expiresAt().isBefore(Instant.now())) throw new FlowRejectedException("QUOTE_EXPIRED");
        CommandIdentity command = command(commandId, idempotencyKey, "POST:/api/v1/orders", quoteRef,
                "ORDER_CREATE:" + quoteRef, projectSubjectRef, quoteRef);
        return project(projectSubjectRef, store.createOrder(projectSubjectRef, quote, command));
    }

    public ProjectProjection confirmMockPayment(String projectSubjectRef, String orderRef, String commandId,
                                                String idempotencyKey, long expectedProjectionVersion,
                                                long expectedAggregateVersion) {
        CommandIdentity command = command(commandId, idempotencyKey, "POST:/api/v1/orders/{orderRef}/mock-payment",
                orderRef, "MOCK_PAYMENT:" + orderRef, projectSubjectRef, orderRef);
        OrderProjection updated = store.transitionOrder(projectSubjectRef, orderRef, command, expectedProjectionVersion,
                expectedAggregateVersion, current -> {
                    if (current.paymentState().equals("CONFIRMED")) return current;
                    if (current.orderState() != OrderState.AWAITING_PAYMENT) {
                        throw new FlowRejectedException("PAYMENT_NOT_ALLOWED");
                    }
                    return copy(current, OrderState.PAYMENT_CONFIRMED, "CONFIRMED",
                            current.upstreamDebitState(), current.deliveryState(), current.refundState(),
                            "REQUEST_MOCK_TOPUP");
                });
        return project(projectSubjectRef, updated);
    }

    public ProjectProjection completeMockTopup(String projectSubjectRef, String orderRef, String commandId,
                                               String idempotencyKey, long expectedProjectionVersion,
                                               long expectedAggregateVersion, MnpState postPaymentMnpState) {
        CommandIdentity command = command(commandId, idempotencyKey, "POST:/api/v1/orders/{orderRef}/mock-topup",
                orderRef, "MOCK_TOPUP:" + orderRef, projectSubjectRef, orderRef, postPaymentMnpState.name());
        OrderProjection updated = store.transitionOrder(projectSubjectRef, orderRef, command, expectedProjectionVersion,
                expectedAggregateVersion, current -> {
                    if (!current.paymentState().equals("CONFIRMED")) {
                        throw new FlowRejectedException("PAYMENT_NOT_CONFIRMED");
                    }
                    if (current.orderState() == OrderState.TOPUP_REVIEW
                            || current.upstreamDebitState().equals("UNKNOWN")
                            || current.deliveryState().equals("UNKNOWN")) {
                        throw new FlowRejectedException("TOPUP_UNKNOWN_QUERY_ONLY");
                    }
                    if (postPaymentMnpState != MnpState.CONFIRMED) {
                        return copy(current, OrderState.TOPUP_REVIEW, "CONFIRMED",
                                "UNKNOWN", "UNKNOWN", current.refundState(), "WAIT_OR_CONTACT_SUPPORT");
                    }
                    if (current.orderState() == OrderState.COMPLETED) return current;
                    return copy(current, OrderState.COMPLETED, "CONFIRMED",
                            "CONFIRMED", "CONFIRMED", "ABSENT_CONFIRMED", "NONE");
                });
        return project(projectSubjectRef, updated);
    }

    public ProjectProjection getOrder(String projectSubjectRef, String orderRef) {
        return project(projectSubjectRef, store.requireOrder(projectSubjectRef, orderRef));
    }

    private ProjectProjection project(String projectSubjectRef, OrderProjection order) {
        Quote quote = store.requireQuote(projectSubjectRef, order.quoteRef());
        ProjectionFacts facts = new ProjectionFacts(ProjectionFacts.MOCK_ONLY, order.paymentState(),
                order.upstreamDebitState(), order.deliveryState(), order.refundState());
        return new ProjectProjection(order.orderRef(), order.quoteRef(), order.orderState(), PriceSnapshot.from(quote),
                facts, order.projectionVersion(), order.aggregateVersion(), allowedActions(order));
    }

    private static List<AllowedAction> allowedActions(OrderProjection order) {
        return switch (order.nextAction()) {
            case "REQUEST_MOCK_PAYMENT" -> List.of(new AllowedAction("REQUEST_MOCK_PAYMENT",
                    order.projectionVersion(), order.aggregateVersion()));
            case "REQUEST_MOCK_TOPUP" -> List.of(new AllowedAction("REQUEST_MOCK_TOPUP",
                    order.projectionVersion(), order.aggregateVersion()));
            case "WAIT_OR_CONTACT_SUPPORT" -> List.of(new AllowedAction("WAIT_OR_CONTACT_SUPPORT",
                    order.projectionVersion(), null));
            default -> List.of();
        };
    }

    private static OrderProjection copy(OrderProjection current, OrderState state, String payment,
                                        String debit, String delivery, String refund, String nextAction) {
        return new OrderProjection(current.orderRef(), current.quoteRef(), state, payment, debit, delivery,
                refund, current.totalAmountMinor(), current.currency(), current.projectionVersion() + 1,
                current.aggregateVersion() + 1, nextAction);
    }

    private static CommandIdentity command(String commandId, String idempotencyKey, String endpointScope,
                                           String resourceScope, String semanticActionKey, String... fingerprintFields) {
        return new CommandIdentity(commandId, idempotencyKey, endpointScope, resourceScope, semanticActionKey,
                CanonicalFingerprint.sha256(fingerprintFields));
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }
}
