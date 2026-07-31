package com.huarenzaimeng.api;

import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.OrderState;
import com.huarenzaimeng.core.Quote;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockFlowService {
    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<String, OrderProjection> orders = new ConcurrentHashMap<>();
    private final Map<String, String> commandToOrder = new ConcurrentHashMap<>();

    public Quote createQuote(String phone, String operatorCode, String productCode, MnpState mnpState) {
        if (mnpState != MnpState.CONFIRMED) {
            throw new FlowRejectedException("PREPAY_MNP_NOT_CONFIRMED");
        }
        String quoteRef = "Q-" + UUID.randomUUID();
        Quote quote = new Quote(quoteRef, mask(phone), operatorCode, productCode,
                1_000L, "CNY", Instant.now().plus(10, ChronoUnit.MINUTES));
        quotes.put(quoteRef, quote);
        return quote;
    }

    public synchronized OrderProjection createOrder(String quoteRef, String commandId) {
        String existing = commandToOrder.get(commandId);
        if (existing != null) return orders.get(existing);
        Quote quote = requireQuote(quoteRef);
        if (quote.expiresAt().isBefore(Instant.now())) throw new FlowRejectedException("QUOTE_EXPIRED");
        String orderRef = "O-" + UUID.randomUUID();
        OrderProjection order = new OrderProjection(orderRef, quoteRef, OrderState.AWAITING_PAYMENT,
                "ABSENT_CONFIRMED", "ABSENT_CONFIRMED", "ABSENT_CONFIRMED", "ABSENT_CONFIRMED",
                quote.totalAmountMinor(), quote.currency(), 1L, "REQUEST_MOCK_PAYMENT");
        orders.put(orderRef, order);
        commandToOrder.put(commandId, orderRef);
        return order;
    }

    public synchronized OrderProjection confirmMockPayment(String orderRef, String commandId) {
        OrderProjection current = requireOrder(orderRef);
        if (current.paymentState().equals("CONFIRMED")) return current;
        if (current.orderState() != OrderState.AWAITING_PAYMENT) throw new FlowRejectedException("PAYMENT_NOT_ALLOWED");
        OrderProjection updated = copy(current, OrderState.PAYMENT_CONFIRMED, "CONFIRMED",
                current.upstreamDebitState(), current.deliveryState(), current.refundState(), "REQUEST_MOCK_TOPUP");
        orders.put(orderRef, updated);
        return updated;
    }

    public synchronized OrderProjection completeMockTopup(String orderRef, MnpState postPaymentMnpState) {
        OrderProjection current = requireOrder(orderRef);
        if (!current.paymentState().equals("CONFIRMED")) throw new FlowRejectedException("PAYMENT_NOT_CONFIRMED");
        if (postPaymentMnpState != MnpState.CONFIRMED) {
            OrderProjection review = copy(current, OrderState.TOPUP_REVIEW, "CONFIRMED",
                    "UNKNOWN", "UNKNOWN", current.refundState(), "WAIT_OR_CONTACT_SUPPORT");
            orders.put(orderRef, review);
            return review;
        }
        if (current.orderState() == OrderState.COMPLETED) return current;
        OrderProjection completed = copy(current, OrderState.COMPLETED, "CONFIRMED",
                "CONFIRMED", "CONFIRMED", "ABSENT_CONFIRMED", "NONE");
        orders.put(orderRef, completed);
        return completed;
    }

    public OrderProjection getOrder(String orderRef) { return requireOrder(orderRef); }

    private Quote requireQuote(String quoteRef) {
        Quote quote = quotes.get(quoteRef);
        if (quote == null) throw new FlowRejectedException("QUOTE_NOT_FOUND");
        return quote;
    }

    private OrderProjection requireOrder(String orderRef) {
        OrderProjection order = orders.get(orderRef);
        if (order == null) throw new FlowRejectedException("ORDER_NOT_FOUND");
        return order;
    }

    private static OrderProjection copy(OrderProjection current, OrderState state, String payment,
                                        String debit, String delivery, String refund, String nextAction) {
        return new OrderProjection(current.orderRef(), current.quoteRef(), state, payment, debit, delivery,
                refund, current.totalAmountMinor(), current.currency(), current.projectionVersion() + 1, nextAction);
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }

    static final class FlowRejectedException extends RuntimeException {
        FlowRejectedException(String code) { super(code); }
    }
}
