package com.huarenzaimeng.api;

import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.OrderState;
import com.huarenzaimeng.core.PriceSnapshot;
import com.huarenzaimeng.core.Quote;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "mysql")
class MyBatisFlowStore implements FlowStore {
    private static final Set<String> PAYMENT_INTENT_UNIQUE_CONSTRAINTS = Set.of(
            "PRIMARY",
            "uk_hz_command_subject_idempotency",
            "uk_hz_command_subject_semantic",
            "uk_hz_command_subject_endpoint_idempotency",
            "uk_hz_payment_intent_business",
            "uk_hz_payment_intent_business_key",
            "uk_hz_payment_intent_semantic");
    private final FlowMapper mapper;
    private final TransactionTemplate transactions;
    private final Map<String, PaymentIntentReviewSignal> paymentIntentReviewSignals = new ConcurrentHashMap<>();

    MyBatisFlowStore(FlowMapper mapper, TransactionTemplate transactions) {
        this.mapper = mapper;
        this.transactions = transactions;
    }

    @Override
    public Quote createQuote(String subject, Quote quote, CommandIdentity command) {
        try {
            Quote result = transactions.execute(status -> {
                CommandRow replay = findCommand(subject, command, true);
                if (replay != null) return requireQuote(subject, replay.resourceRef());
                mapper.insertQuote(subject, quote.quoteRef(), quote.maskedPhone(), quote.operatorCode(),
                        quote.productCode(), quote.denominationRef(), quote.supportedOperatorSetVersion(),
                        quote.catalogVersion(), BigDecimal.valueOf(quote.totalAmountMinor(), 2),
                        quote.totalAmountMinor(), quote.currency(), Timestamp.from(quote.expiresAt()),
                        Timestamp.from(Instant.now()));
                insertCommand(subject, command, quote.quoteRef());
                return requireQuote(subject, quote.quoteRef());
            });
            if (result == null) throw new IllegalStateException("quote transaction returned no result");
            return result;
        } catch (DuplicateKeyException race) {
            CommandRow existing = findCommand(subject, command, false);
            if (existing == null) throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
            return requireQuote(subject, existing.resourceRef());
        }
    }

    @Override
    public Quote requireQuote(String subject, String quoteRef) {
        Map<String, Object> row = mapper.selectQuote(subject, quoteRef);
        if (row == null) throw new FlowRejectedException("QUOTE_NOT_FOUND");
        requireCatalogMetadata(row);
        return new Quote(string(row, "quote_ref"), string(row, "phone_masked"), string(row, "operator_code"),
                string(row, "product_code"), string(row, "denomination_ref"),
                number(row, "supported_operator_set_version"), number(row, "catalog_version"),
                number(row, "total_amount_minor"), string(row, "total_currency"),
                timestamp(row, "expires_at").toInstant());
    }

    @Override public long quoteCount(String subject) { return mapper.countQuotes(subject); }

    @Override
    public OrderCreateResult replayOrder(String subject, CommandIdentity command) {
        try {
            return transactions.execute(status -> replayOrderInTransaction(subject, command, true));
        } catch (DuplicateKeyException race) {
            OrderCommandMatch match = findOrderCommand(subject, command, false);
            if (match == null) throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
            return new OrderCreateResult(requireOrder(subject, match.row().resourceRef()), false);
        }
    }

    @Override
    public OrderCreateResult createOrder(String subject, Quote quote, CommandIdentity command) {
        try {
            OrderCreateResult result = transactions.execute(status -> {
                OrderCreateResult replay = replayOrderInTransaction(subject, command, true);
                if (replay != null) return replay;
                String orderRef = "O-" + UUID.randomUUID();
                mapper.insertOrder(subject, orderRef, quote.quoteRef(), Timestamp.from(Instant.now()));
                insertCommand(subject, command, orderRef);
                return new OrderCreateResult(requireOrder(subject, orderRef), true);
            });
            if (result == null) throw new IllegalStateException("order transaction returned no result");
            return result;
        } catch (DuplicateKeyException race) {
            OrderCreateResult replay = replayOrder(subject, command);
            if (replay == null) throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
            return replay;
        }
    }

    @Override
    public OrderProjection requireOrder(String subject, String orderRef) {
        Map<String, Object> row = mapper.selectOrder(subject, orderRef);
        if (row == null) throw new FlowRejectedException("ORDER_NOT_FOUND");
        return order(row);
    }

    @Override public List<OrderProjection> listOrders(String subject) {
        return mapper.selectOrders(subject).stream().map(MyBatisFlowStore::order).toList();
    }

    @Override
    public PaymentIntentCreateResult createPaymentIntent(String subject, PaymentIntentDraft draft,
                                                          CommandIdentity command,
                                                          long expectedProjectionVersion,
                                                          long expectedAggregateVersion,
                                                          Runnable firstCreationQualification) {
        try {
            PaymentIntentCreateResult result = transactions.execute(status -> {
                CommandRow replay = findCommand(subject, command, true);
                if (replay != null) {
                    PaymentIntentRecord existing = requirePaymentIntent(subject, replay.resourceRef());
                    return new PaymentIntentCreateResult(existing, requireOrder(subject, existing.orderRef()), false);
                }
                Map<String, Object> locked = mapper.selectOrderForUpdate(subject, draft.orderRef());
                if (locked == null) throw new FlowRejectedException("PAYMENT_INTENT_NOT_AVAILABLE");
                CommandRow replayAfterParentLock = findCommand(subject, command, true);
                if (replayAfterParentLock != null) {
                    PaymentIntentRecord existing = requirePaymentIntent(subject, replayAfterParentLock.resourceRef());
                    return new PaymentIntentCreateResult(existing,
                            requireOrder(subject, existing.orderRef()), false);
                }
                OrderProjection current = order(locked);
                if (current.projectionVersion() != expectedProjectionVersion) {
                    throw new FlowRejectedException("PROJECTION_VERSION_CONFLICT");
                }
                if (current.aggregateVersion() != expectedAggregateVersion) {
                    throw new FlowRejectedException("AGGREGATE_VERSION_CONFLICT");
                }
                if (current.orderState() != OrderState.AWAITING_PAYMENT
                        || !MockFlowService.CREATE_PAYMENT_INTENT_ACTION.equals(current.nextAction())
                        || !current.quoteRef().equals(draft.priceSnapshot().quoteRef())
                        || current.totalAmountMinor() != draft.priceSnapshot().totalAmountMinor()
                        || !current.currency().equals(draft.priceSnapshot().currency())) {
                    throw new FlowRejectedException("PAYMENT_INTENT_NOT_AVAILABLE");
                }
                firstCreationQualification.run();
                Timestamp now = Timestamp.from(draft.createdAt());
                mapper.insertSemanticAction(draft.semanticActionKey(), draft.businessKey(),
                        "CREATE_PAYMENT_INTENT", "LOCAL_SYNTHETIC_ELIGIBLE", now);
                mapper.insertPaymentIntent(draft.paymentIntentRef(), draft.environment(), subject, draft.orderRef(),
                        draft.businessKey(), draft.semanticActionKey(), draft.requestFingerprint(),
                        draft.priceSnapshotDigest(),
                        draft.paymentEligibilityDecisionRef(), draft.scope(), now);
                int changed = mapper.updateOrder(subject, current.orderRef(), current.orderState().name(),
                        current.paymentState(), current.upstreamDebitState(), current.deliveryState(),
                        current.refundState(), MockFlowService.QUERY_PAYMENT_INTENT_ACTION,
                        current.projectionVersion() + 1, current.aggregateVersion() + 1,
                        expectedAggregateVersion, now);
                if (changed != 1) throw new FlowRejectedException("AGGREGATE_VERSION_CONFLICT");
                insertCommand(subject, command, draft.paymentIntentRef());
                mapper.insertOutbox(current.orderRef() + ":aggregate:" + (current.aggregateVersion() + 1),
                        current.orderRef(), current.projectionVersion() + 1,
                        current.aggregateVersion() + 1, now);
                return new PaymentIntentCreateResult(requirePaymentIntent(subject, draft.paymentIntentRef()),
                        requireOrder(subject, current.orderRef()), true);
            });
            if (result == null) throw new IllegalStateException("payment intent transaction returned no result");
            return result;
        } catch (DuplicateKeyException race) {
            return recoverPaymentIntentUniqueConflict(subject, command, race);
        }
    }

    private PaymentIntentCreateResult recoverPaymentIntentUniqueConflict(String subject, CommandIdentity command,
                                                                           DuplicateKeyException race) {
        paymentIntentUniqueConstraintCategory(race.getMostSpecificCause() == null
                ? race.getMessage() : race.getMostSpecificCause().getMessage());
        try {
            CommandRow replay = findCommand(subject, command, false);
            if (replay == null) throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
            PaymentIntentRecord existing = requirePaymentIntent(subject, replay.resourceRef());
            return new PaymentIntentCreateResult(existing, requireOrder(subject, existing.orderRef()), false);
        } catch (FlowRejectedException conflict) {
            throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
        } catch (RuntimeException unreadableCanonicalRecord) {
            throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
        }
    }

    static String paymentIntentUniqueConstraintCategory(String databaseMessage) {
        if (databaseMessage == null) return "UNKNOWN_UNIQUE_CONSTRAINT";
        return PAYMENT_INTENT_UNIQUE_CONSTRAINTS.stream()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .filter(databaseMessage::contains)
                .findFirst()
                .orElse("UNKNOWN_UNIQUE_CONSTRAINT");
    }

    @Override
    public PaymentIntentRecord requirePaymentIntent(String subject, String paymentIntentRef) {
        Map<String, Object> row = mapper.selectPaymentIntent(subject, paymentIntentRef);
        if (row == null) throw new FlowRejectedException("PAYMENT_INTENT_NOT_AVAILABLE");
        PriceSnapshot snapshot = new PriceSnapshot(string(row, "quote_ref"), string(row, "phone_masked"),
                string(row, "operator_code"), string(row, "product_code"), string(row, "denomination_ref"),
                number(row, "supported_operator_set_version"), number(row, "catalog_version"),
                number(row, "total_amount_minor"), string(row, "total_currency"),
                timestamp(row, "expires_at").toInstant());
        return new PaymentIntentRecord(string(row, "payment_intent_ref"), string(row, "environment"),
                string(row, "project_subject_ref"), string(row, "order_ref"),
                string(row, "business_key"), string(row, "semantic_action_key"),
                string(row, "request_fingerprint"), snapshot, string(row, "price_snapshot_digest"),
                string(row, "payment_eligibility_decision_ref"), string(row, "intent_scope"),
                timestamp(row, "created_at").toInstant());
    }

    @Override
    public PaymentIntentResultRecord queryPaymentIntentResult(String subject, String environment, String orderRef,
                                                               String originalCommandId,
                                                               String originalIdempotencyKey) {
        Map<String, Object> row = mapper.selectPaymentIntentResultByOriginalKeys(subject, orderRef,
                originalCommandId, originalIdempotencyKey);
        if (row == null) return null;
        if (!environment.equals(string(row, "environment"))
                || !subject.equals(string(row, "project_subject_ref"))
                || !orderRef.equals(string(row, "order_ref"))
                || !originalCommandId.equals(string(row, "command_id"))
                || !originalIdempotencyKey.equals(string(row, "idempotency_key"))) return null;
        String expectedBusinessKey = MockFlowService.paymentIntentBusinessKey(environment, subject, orderRef);
        String expectedSemanticActionKey = MockFlowService.paymentIntentSemanticActionKey(environment, subject,
                orderRef);
        String commandFingerprint = string(row, "command_canonical_fingerprint");
        String commandSemanticActionKey = string(row, "command_semantic_action_key");
        String intentBusinessKey = string(row, "payment_intent_business_key");
        String intentSemanticActionKey = string(row, "payment_intent_semantic_action_key");
        String intentFingerprint = string(row, "payment_intent_request_fingerprint");
        if (!commandFingerprint.equals(intentFingerprint)
                || !commandSemanticActionKey.equals(intentSemanticActionKey)
                || !expectedBusinessKey.equals(intentBusinessKey)
                || !expectedSemanticActionKey.equals(commandSemanticActionKey)) {
            throw new PaymentIntentReviewRequiredException(
                    PaymentIntentReviewReason.ORIGINAL_COMMAND_BINDING_MISMATCH,
                    string(row, "payment_intent_ref"));
        }
        return new PaymentIntentResultRecord(environment, subject, orderRef, originalCommandId,
                originalIdempotencyKey, intentBusinessKey, intentSemanticActionKey,
                intentFingerprint, string(row, "payment_intent_ref"),
                PaymentIntentResultState.FOUND, null);
    }

    @Override
    public void recordPaymentIntentReviewSignal(PaymentIntentReviewSignalInput input) {
        PaymentIntentReviewSignal signal = PaymentIntentReviewSignal.from(input);
        paymentIntentReviewSignals.putIfAbsent(input.projectSubjectRef() + "\u0000" + signal.reviewSignalRef(),
                signal);
    }

    long paymentIntentReviewSignalCountForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return paymentIntentReviewSignals.keySet().stream().filter(key -> key.startsWith(prefix)).count();
    }

    List<PaymentIntentReviewSignal> paymentIntentReviewSignalsForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return paymentIntentReviewSignals.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue).toList();
    }

    private static OrderProjection order(Map<String, Object> row) {
        return new OrderProjection(string(row, "order_ref"), string(row, "quote_ref"),
                OrderState.valueOf(string(row, "order_state")), string(row, "payment_state"),
                string(row, "upstream_debit_state"), string(row, "delivery_state"), string(row, "refund_state"),
                number(row, "total_amount_minor"), string(row, "total_currency"),
                number(row, "projection_version"), number(row, "aggregate_version"), string(row, "allowed_action"));
    }

    @Override
    public OrderProjection transitionOrderAuthorized(String subject, String orderRef, StateAdvanceCommand advance,
            long expectedProjectionVersion, long expectedAggregateVersion,
            UnaryOperator<OrderProjection> transition) {
        try {
        OrderProjection result = stateAdvanceWrites().execute(status -> {
            Map<String,Object> identity=mapper.selectOrderAuthorityForUpdate(subject,orderRef);
            if(identity==null)throw new FlowRejectedException("STATE_ADVANCE_AGGREGATE_IDENTITY_MISSING");
            StateAdvanceGate.verify(orderRef,new StateAdvanceGate.AggregateIdentity(
                    StateAdvanceAuthority.Environment.valueOf(string(identity,"environment")),
                    StateAdvanceAuthority.EvidenceLevel.valueOf(string(identity,"evidence_level")),
                    string(identity,"authority_state")),advance);
            CommandRow replay = findCommand(subject, advance.command(), true);
            if (replay != null) return requireOrder(subject, replay.resourceRef());
            OrderProjection current = requireOrder(subject, orderRef);
            if (current.projectionVersion()!=expectedProjectionVersion || current.aggregateVersion()!=expectedAggregateVersion)
                throw new FlowRejectedException("AGGREGATE_VERSION_CONFLICT");
            OrderProjection updated=transition.apply(current);
            int changed=mapper.updateOrder(subject,orderRef,updated.orderState().name(),updated.paymentState(),
                    updated.upstreamDebitState(),updated.deliveryState(),updated.refundState(),updated.nextAction(),
                    updated.projectionVersion(),updated.aggregateVersion(),expectedAggregateVersion,Timestamp.from(advance.decisionInstant()));
            if(changed!=1)throw new FlowRejectedException("AGGREGATE_VERSION_CONFLICT");
            var a=advance.authority();
            if(mapper.insertStateAdvanceAuthorityFact(orderRef,advance.command().commandId(),a.environment().name(),a.evidenceLevel().name(),
                    a.authorizationRef(),advance.targetTransition(),advance.evidenceRef(),"NON_PRODUCTION",
                    Timestamp.from(advance.decisionInstant()))!=1)throw new FlowRejectedException("AUTHORITY_FACT_WRITE_FAILED");
            insertCommand(subject,advance.command(),orderRef);
            mapper.insertOutbox(orderRef+":aggregate:"+updated.aggregateVersion(),orderRef,updated.projectionVersion(),
                    updated.aggregateVersion(),Timestamp.from(advance.decisionInstant()));
            return updated;
        });
        if(result==null)throw new IllegalStateException("authorized transition returned no result");
        return result;
        } catch (DuplicateKeyException duplicate) {
            StateAdvanceConcurrentResult classification = canonicalReads().execute(status ->
                    classifyStateAdvanceDuplicate(subject, orderRef, advance.command()));
            if (classification == StateAdvanceConcurrentResult.REPLAYED) return requireOrder(subject, orderRef);
            throw new FlowRejectedException(classification == null
                    ? StateAdvanceConcurrentResult.CONCURRENT_RESULT_UNKNOWN.name() : classification.name());
        }
    }

    private TransactionTemplate stateAdvanceWrites() {
        if (transactions.getTransactionManager() == null) return transactions;
        TransactionTemplate template = new TransactionTemplate(transactions.getTransactionManager());
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private TransactionTemplate canonicalReads() {
        if (transactions.getTransactionManager() == null) return transactions;
        TransactionTemplate template = new TransactionTemplate(transactions.getTransactionManager());
        template.setReadOnly(true);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private StateAdvanceConcurrentResult classifyStateAdvanceDuplicate(String subject, String orderRef,
                                                                        CommandIdentity command) {
        try {
            CommandRow canonical = findCommand(subject, command, false);
            if (canonical == null) return StateAdvanceConcurrentResult.CONCURRENT_RESULT_UNKNOWN;
            if (canonical.resourceRef() == null || canonical.fingerprint() == null
                    || canonical.semanticActionKey() == null || canonical.commandId() == null)
                return StateAdvanceConcurrentResult.STORAGE_INTEGRITY_CONFLICT;
            if (!orderRef.equals(canonical.resourceRef())) return StateAdvanceConcurrentResult.STORAGE_INTEGRITY_CONFLICT;
            return canonical.matches(command) ? StateAdvanceConcurrentResult.REPLAYED
                    : StateAdvanceConcurrentResult.IDEMPOTENCY_CONFLICT;
        } catch (FlowRejectedException conflictingKeys) {
            return StateAdvanceConcurrentResult.IDEMPOTENCY_CONFLICT;
        } catch (RuntimeException unreadable) {
            return StateAdvanceConcurrentResult.CONCURRENT_RESULT_UNKNOWN;
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

    private OrderCommandMatch findOrderCommand(String subject, CommandIdentity command, boolean forUpdate) {
        List<Map<String, Object>> rows = forUpdate
                ? mapper.selectOrderCommandsForUpdate(subject, command.commandId(), command.endpointScope(),
                command.idempotencyKey(), command.semanticActionKey())
                : mapper.selectOrderCommands(subject, command.commandId(), command.endpointScope(),
                command.idempotencyKey(), command.semanticActionKey());
        if (rows.isEmpty()) return null;
        List<CommandRow> found = rows.stream().map(MyBatisFlowStore::commandRow).toList();
        String resourceRef = found.get(0).resourceRef();
        if (found.stream().anyMatch(existing -> !existing.matchesOrderRequest(command, resourceRef))) {
            throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
        }
        boolean exactKeys = found.stream().anyMatch(existing -> existing.acceptsBothKeys(command));
        boolean bothKeysAreNew = found.stream().noneMatch(existing -> existing.acceptsCommandId(command))
                && found.stream().noneMatch(existing -> existing.acceptsIdempotencyKey(command));
        return new OrderCommandMatch(found.get(0), !exactKeys && bothKeysAreNew);
    }

    private OrderCreateResult replayOrderInTransaction(String subject, CommandIdentity command,
                                                        boolean registerAlias) {
        OrderCommandMatch match = findOrderCommand(subject, command, true);
        if (match == null) return null;
        if (registerAlias && match.aliasRequired()) insertOrderAlias(subject, command, match.row().resourceRef());
        return new OrderCreateResult(requireOrder(subject, match.row().resourceRef()), false);
    }

    private void insertOrderAlias(String subject, CommandIdentity command, String resourceRef) {
        String aliasSemanticKey = "ORDER_ALIAS:" + CanonicalFingerprint.sha256(command.commandId(),
                command.idempotencyKey(), command.semanticActionKey());
        mapper.insertCommand(subject, command.commandId(), command.idempotencyKey(), command.endpointScope(),
                command.resourceScope(), aliasSemanticKey, command.canonicalFingerprint(), resourceRef,
                Timestamp.from(Instant.now()));
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
    private static void requireCatalogMetadata(Map<String, Object> row) {
        if (row.get("denomination_ref") == null || row.get("supported_operator_set_version") == null
                || row.get("catalog_version") == null) {
            throw new FlowRejectedException("QUOTE_REQUOTE_REQUIRED_LEGACY_RECORD");
        }
    }
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

        boolean matchesOrderRequest(CommandIdentity command, String expectedResourceRef) {
            return endpointScope.equals(command.endpointScope()) && resourceScope.equals(command.resourceScope())
                    && fingerprint.equals(command.canonicalFingerprint()) && resourceRef.equals(expectedResourceRef);
        }

        boolean acceptsBothKeys(CommandIdentity command) {
            return acceptsCommandId(command) && acceptsIdempotencyKey(command);
        }

        boolean acceptsCommandId(CommandIdentity command) { return commandId.equals(command.commandId()); }

        boolean acceptsIdempotencyKey(CommandIdentity command) {
            return endpointScope.equals(command.endpointScope()) && idempotencyKey.equals(command.idempotencyKey());
        }
    }

    private record OrderCommandMatch(CommandRow row, boolean aliasRequired) {}
}
