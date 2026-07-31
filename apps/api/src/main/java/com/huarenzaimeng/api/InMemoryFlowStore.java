package com.huarenzaimeng.api;

import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.OrderState;
import com.huarenzaimeng.core.Quote;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "in-memory")
class InMemoryFlowStore implements FlowStore {
    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<String, OrderProjection> orders = new ConcurrentHashMap<>();
    private final Map<String, CommandRecord> commands = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyToCommand = new ConcurrentHashMap<>();

    @Override
    public void saveQuote(String projectSubjectRef, Quote quote) {
        quotes.putIfAbsent(subjectKey(projectSubjectRef, quote.quoteRef()), quote);
    }

    @Override
    public Quote requireQuote(String projectSubjectRef, String quoteRef) {
        Quote quote = quotes.get(subjectKey(projectSubjectRef, quoteRef));
        if (quote == null) throw new FlowRejectedException("QUOTE_NOT_FOUND");
        return quote;
    }

    @Override
    public synchronized OrderProjection createOrder(String projectSubjectRef, Quote quote, CommandIdentity command) {
        CommandRecord replay = replay(projectSubjectRef, command);
        if (replay != null) return requireOrder(projectSubjectRef, replay.resourceRef());

        String orderRef = "O-" + UUID.randomUUID();
        OrderProjection order = new OrderProjection(orderRef, quote.quoteRef(), OrderState.AWAITING_PAYMENT,
                "ABSENT_CONFIRMED", "ABSENT_CONFIRMED", "ABSENT_CONFIRMED", "ABSENT_CONFIRMED",
                quote.totalAmountMinor(), quote.currency(), 1L, 1L, "REQUEST_MOCK_PAYMENT");
        orders.put(subjectKey(projectSubjectRef, orderRef), order);
        record(projectSubjectRef, command, orderRef);
        return order;
    }

    @Override
    public OrderProjection requireOrder(String projectSubjectRef, String orderRef) {
        OrderProjection order = orders.get(subjectKey(projectSubjectRef, orderRef));
        if (order == null) throw new FlowRejectedException("ORDER_NOT_FOUND");
        return order;
    }

    @Override
    public synchronized OrderProjection transitionOrder(String projectSubjectRef, String orderRef,
                                                        CommandIdentity command, long expectedProjectionVersion,
                                                        long expectedAggregateVersion,
                                                        UnaryOperator<OrderProjection> transition) {
        CommandRecord replay = replay(projectSubjectRef, command);
        if (replay != null) return requireOrder(projectSubjectRef, replay.resourceRef());
        OrderProjection current = requireOrder(projectSubjectRef, orderRef);
        if (current.projectionVersion() != expectedProjectionVersion) {
            throw new FlowRejectedException("PROJECTION_VERSION_CONFLICT");
        }
        if (current.aggregateVersion() != expectedAggregateVersion) {
            throw new FlowRejectedException("AGGREGATE_VERSION_CONFLICT");
        }
        OrderProjection updated = transition.apply(current);
        orders.put(subjectKey(projectSubjectRef, orderRef), updated);
        record(projectSubjectRef, command, orderRef);
        return updated;
    }

    private CommandRecord replay(String subject, CommandIdentity command) {
        String commandScope = subjectKey(subject, command.commandId());
        String idempotencyScope = subjectKey(subject, command.endpointScope() + ":" + command.resourceScope()
                + ":" + command.idempotencyKey());
        String semanticScope = subjectKey(subject, "SEM:" + command.semanticActionKey());
        CommandRecord byCommand = commands.get(commandScope);
        String mappedCommand = idempotencyToCommand.get(idempotencyScope);
        String semanticCommand = idempotencyToCommand.get(semanticScope);
        if (semanticCommand != null && !semanticCommand.equals(commandScope)) {
            throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
        }
        if (byCommand == null && mappedCommand == null) return null;
        CommandRecord existing = byCommand != null ? byCommand : commands.get(mappedCommand);
        if (existing == null || !existing.command().equals(command)) {
            throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
        }
        return existing;
    }

    private void record(String subject, CommandIdentity command, String resourceRef) {
        String commandScope = subjectKey(subject, command.commandId());
        CommandRecord record = new CommandRecord(command, resourceRef);
        commands.put(commandScope, record);
        idempotencyToCommand.put(subjectKey(subject, command.endpointScope() + ":" + command.resourceScope()
                + ":" + command.idempotencyKey()), commandScope);
        idempotencyToCommand.put(subjectKey(subject, "SEM:" + command.semanticActionKey()), commandScope);
    }

    private static String subjectKey(String subject, String value) { return subject + "\u0000" + value; }

    private record CommandRecord(CommandIdentity command, String resourceRef) {}
}
