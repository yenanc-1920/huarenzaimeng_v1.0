package com.huarenzaimeng.api;

import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.OrderState;
import com.huarenzaimeng.core.Quote;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "mysql")
class MyBatisFlowStore implements FlowStore {
    private final FlowMapper mapper;
    private final TransactionTemplate transactions;

    MyBatisFlowStore(FlowMapper mapper, TransactionTemplate transactions) {
        this.mapper = mapper;
        this.transactions = transactions;
    }

    @Override
    public void saveQuote(String subject, Quote quote) {
        mapper.insertQuote(subject, quote.quoteRef(), quote.maskedPhone(), quote.operatorCode(), quote.productCode(),
                BigDecimal.valueOf(quote.totalAmountMinor(), 2), quote.totalAmountMinor(), quote.currency(),
                Timestamp.from(quote.expiresAt()), Timestamp.from(Instant.now()));
    }

    @Override
    public Quote requireQuote(String subject, String quoteRef) {
        Map<String, Object> row = mapper.selectQuote(subject, quoteRef);
        if (row == null) throw new FlowRejectedException("QUOTE_NOT_FOUND");
        return new Quote(string(row, "quote_ref"), string(row, "phone_masked"), string(row, "operator_code"),
                string(row, "product_code"), number(row, "total_amount_minor"), string(row, "total_currency"),
                timestamp(row, "expires_at").toInstant());
    }

    @Override
    public OrderProjection createOrder(String subject, Quote quote, CommandIdentity command) {
        try {
            OrderProjection result = transactions.execute(status -> {
                CommandRow replay = findCommand(subject, command, true);
                if (replay != null) return requireOrder(subject, replay.resourceRef());
                String orderRef = "O-" + UUID.randomUUID();
                mapper.insertOrder(subject, orderRef, quote.quoteRef(), Timestamp.from(Instant.now()));
                insertCommand(subject, command, orderRef);
                return requireOrder(subject, orderRef);
            });
            if (result == null) throw new IllegalStateException("order transaction returned no result");
            return result;
        } catch (DuplicateKeyException race) {
            return replayAfterRace(subject, command);
        }
    }

    @Override
    public OrderProjection requireOrder(String subject, String orderRef) {
        Map<String, Object> row = mapper.selectOrder(subject, orderRef);
        if (row == null) throw new FlowRejectedException("ORDER_NOT_FOUND");
        return new OrderProjection(string(row, "order_ref"), string(row, "quote_ref"),
                OrderState.valueOf(string(row, "order_state")), string(row, "payment_state"),
                string(row, "upstream_debit_state"), string(row, "delivery_state"), string(row, "refund_state"),
                number(row, "total_amount_minor"), string(row, "total_currency"),
                number(row, "projection_version"), number(row, "aggregate_version"), string(row, "allowed_action"));
    }

    @Override
    public OrderProjection transitionOrder(String subject, String orderRef, CommandIdentity command,
                                           long expectedProjectionVersion, long expectedAggregateVersion,
                                           UnaryOperator<OrderProjection> transition) {
        try {
            OrderProjection result = transactions.execute(status -> {
                CommandRow replay = findCommand(subject, command, true);
                if (replay != null) return requireOrder(subject, replay.resourceRef());
                OrderProjection current = requireOrder(subject, orderRef);
                if (current.projectionVersion() != expectedProjectionVersion) {
                    throw new FlowRejectedException("PROJECTION_VERSION_CONFLICT");
                }
                if (current.aggregateVersion() != expectedAggregateVersion) {
                    throw new FlowRejectedException("AGGREGATE_VERSION_CONFLICT");
                }
                OrderProjection updated = transition.apply(current);
                int changed = mapper.updateOrder(subject, orderRef, updated.orderState().name(),
                        updated.paymentState(), updated.upstreamDebitState(), updated.deliveryState(),
                        updated.refundState(), updated.nextAction(), updated.projectionVersion(),
                        updated.aggregateVersion(), expectedAggregateVersion, Timestamp.from(Instant.now()));
                if (changed != 1) throw new FlowRejectedException("AGGREGATE_VERSION_CONFLICT");
                insertCommand(subject, command, orderRef);
                mapper.insertOutbox(orderRef + ":aggregate:" + updated.aggregateVersion(), orderRef,
                        updated.projectionVersion(), updated.aggregateVersion(), Timestamp.from(Instant.now()));
                return updated;
            });
            if (result == null) throw new IllegalStateException("transition transaction returned no result");
            return result;
        } catch (DuplicateKeyException race) {
            return replayAfterRace(subject, command);
        }
    }

    private CommandRow findCommand(String subject, CommandIdentity command, boolean forUpdate) {
        List<Map<String, Object>> rows = forUpdate
                ? mapper.selectCommandsForUpdate(subject, command.commandId(), command.endpointScope(),
                command.resourceScope(), command.idempotencyKey(), command.semanticActionKey())
                : mapper.selectCommands(subject, command.commandId(), command.endpointScope(),
                command.resourceScope(), command.idempotencyKey(), command.semanticActionKey());
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
        CommandRow existing = commandRow(rows.get(0));
        if (!existing.matches(command)) throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
        return existing;
    }

    private void insertCommand(String subject, CommandIdentity command, String resourceRef) {
        mapper.insertCommand(subject, command.commandId(), command.idempotencyKey(), command.endpointScope(),
                command.resourceScope(), command.semanticActionKey(), command.canonicalFingerprint(), resourceRef,
                Timestamp.from(Instant.now()));
    }

    private OrderProjection replayAfterRace(String subject, CommandIdentity command) {
        CommandRow existing = findCommand(subject, command, false);
        if (existing == null) throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
        return requireOrder(subject, existing.resourceRef());
    }

    private static CommandRow commandRow(Map<String, Object> row) {
        return new CommandRow(string(row, "command_id"), string(row, "idempotency_key"),
                string(row, "endpoint_scope"), string(row, "resource_scope"),
                string(row, "semantic_action_key"), string(row, "canonical_fingerprint"),
                string(row, "resource_ref"));
    }

    private static String string(Map<String, Object> row, String key) { return String.valueOf(row.get(key)); }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    static Timestamp timestamp(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Timestamp timestamp) return timestamp;
        if (value instanceof LocalDateTime localDateTime) return Timestamp.valueOf(localDateTime);
        if (value instanceof Instant instant) return Timestamp.from(instant);
        if (value == null) throw new IllegalStateException("missing timestamp column: " + key);
        throw new IllegalStateException("unsupported timestamp type for " + key + ": "
                + value.getClass().getName());
    }

    private record CommandRow(String commandId, String idempotencyKey, String endpointScope,
                              String resourceScope, String semanticActionKey, String fingerprint,
                              String resourceRef) {
        boolean matches(CommandIdentity command) {
            return commandId.equals(command.commandId()) && idempotencyKey.equals(command.idempotencyKey())
                    && endpointScope.equals(command.endpointScope()) && resourceScope.equals(command.resourceScope())
                    && semanticActionKey.equals(command.semanticActionKey())
                    && fingerprint.equals(command.canonicalFingerprint());
        }
    }
}
