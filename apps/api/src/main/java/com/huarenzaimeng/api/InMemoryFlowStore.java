package com.huarenzaimeng.api;

import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.OrderState;
import com.huarenzaimeng.core.Quote;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;
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
    private final Map<String, OrderBinding> orderBusinessKeys = new ConcurrentHashMap<>();
    private final Map<String, OrderBinding> orderCommandKeys = new ConcurrentHashMap<>();
    private final Map<String, OrderBinding> orderIdempotencyKeys = new ConcurrentHashMap<>();
    private final Map<String, PaymentIntentRecord> paymentIntentRecords = new ConcurrentHashMap<>();
    private final Map<String, PaymentIntentBinding> paymentIntentBusinessKeys = new ConcurrentHashMap<>();
    private final Map<String, PaymentIntentBinding> paymentIntentCommandKeys = new ConcurrentHashMap<>();
    private final Map<String, PaymentIntentBinding> paymentIntentIdempotencyKeys = new ConcurrentHashMap<>();
    private final Map<String, PaymentIntentBinding> paymentIntentSemanticKeys = new ConcurrentHashMap<>();
    private final Map<String, PaymentIntentResultRecord> paymentIntentResultOverrides = new ConcurrentHashMap<>();
    private final Map<String, PaymentIntentReviewSignal> paymentIntentReviewSignals = new ConcurrentHashMap<>();
    private final Set<String> paymentIntentResultRuntimeFailures = ConcurrentHashMap.newKeySet();
    private final Set<String> semanticActions = ConcurrentHashMap.newKeySet();
    private final Set<String> paymentIntents = ConcurrentHashMap.newKeySet();
    private final Set<String> paymentAttempts = ConcurrentHashMap.newKeySet();
    private final Set<String> wechatPrepayCalls = ConcurrentHashMap.newKeySet();
    private final Set<String> requestPaymentCalls = ConcurrentHashMap.newKeySet();
    private final Set<String> paymentNotifications = ConcurrentHashMap.newKeySet();
    private final Set<String> dispatchIntents = ConcurrentHashMap.newKeySet();
    private final Set<String> wechatPaymentFacts = ConcurrentHashMap.newKeySet();
    private final Set<String> upstreamDebitFacts = ConcurrentHashMap.newKeySet();
    private final Set<String> deliveryFacts = ConcurrentHashMap.newKeySet();
    private final Set<String> refundFacts = ConcurrentHashMap.newKeySet();
    private final Set<String> localLedgerFacts = ConcurrentHashMap.newKeySet();
    private final Set<String> ledgerEntries = ConcurrentHashMap.newKeySet();
    private final Map<String, StateAdvanceCommand> stateAdvanceAuthorityFacts = new ConcurrentHashMap<>();
    private final Map<String, StateAdvanceGate.AggregateIdentity> aggregateIdentities = new ConcurrentHashMap<>();
    private final Set<String> externalCalls = ConcurrentHashMap.newKeySet();

    @Override
    public synchronized Quote createQuote(String projectSubjectRef, Quote quote, CommandIdentity command) {
        CommandRecord replay = replay(projectSubjectRef, command);
        if (replay != null) return requireQuote(projectSubjectRef, replay.resourceRef());
        quotes.put(subjectKey(projectSubjectRef, quote.quoteRef()), quote);
        record(projectSubjectRef, command, quote.quoteRef());
        return quote;
    }

    @Override
    public Quote requireQuote(String projectSubjectRef, String quoteRef) {
        Quote quote = quotes.get(subjectKey(projectSubjectRef, quoteRef));
        if (quote == null) throw new FlowRejectedException("QUOTE_NOT_FOUND");
        return quote;
    }

    @Override public long quoteCount(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return quotes.keySet().stream().filter(key -> key.startsWith(prefix)).count();
    }

    @Override
    public synchronized OrderCreateResult replayOrder(String projectSubjectRef, CommandIdentity command) {
        String businessScope = subjectKey(projectSubjectRef, command.semanticActionKey());
        String commandScope = subjectKey(projectSubjectRef, command.commandId());
        String idempotencyScope = orderIdempotencyScope(projectSubjectRef, command);
        OrderBinding byBusiness = orderBusinessKeys.get(businessScope);
        OrderBinding byCommand = orderCommandKeys.get(commandScope);
        OrderBinding byIdempotency = orderIdempotencyKeys.get(idempotencyScope);
        if (!compatible(byBusiness, command) || !compatible(byCommand, command)
                || !compatible(byIdempotency, command)
                || !sameResource(byBusiness, byCommand) || !sameResource(byBusiness, byIdempotency)
                || !sameResource(byCommand, byIdempotency)) {
            throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
        }
        OrderBinding replay = byBusiness != null ? byBusiness : byCommand != null ? byCommand : byIdempotency;
        if (replay != null) {
            orderCommandKeys.put(commandScope, replay);
            orderIdempotencyKeys.put(idempotencyScope, replay);
            return new OrderCreateResult(requireOrder(projectSubjectRef, replay.resourceRef()), false);
        }
        return null;
    }

    @Override
    public synchronized OrderCreateResult createOrder(String projectSubjectRef, Quote quote,
                                                       CommandIdentity command) {
        OrderCreateResult replay = replayOrder(projectSubjectRef, command);
        if (replay != null) return replay;

        String orderRef = "O-" + UUID.randomUUID();
        OrderProjection order = new OrderProjection(orderRef, quote.quoteRef(), OrderState.AWAITING_PAYMENT,
                "ABSENT_CONFIRMED", "ABSENT_CONFIRMED", "ABSENT_CONFIRMED", "ABSENT_CONFIRMED",
                quote.totalAmountMinor(), quote.currency(), 1L, 1L,
                MockFlowService.CREATE_PAYMENT_INTENT_ACTION);
        orders.put(subjectKey(projectSubjectRef, orderRef), order);
        aggregateIdentities.put(subjectKey(projectSubjectRef,orderRef),new StateAdvanceGate.AggregateIdentity(
                StateAdvanceAuthority.Environment.LOCAL_SYNTHETIC,StateAdvanceAuthority.EvidenceLevel.L1,"NON_PRODUCTION"));
        OrderBinding binding = new OrderBinding(command.canonicalFingerprint(), orderRef);
        orderBusinessKeys.put(subjectKey(projectSubjectRef, command.semanticActionKey()), binding);
        orderCommandKeys.put(subjectKey(projectSubjectRef, command.commandId()), binding);
        orderIdempotencyKeys.put(orderIdempotencyScope(projectSubjectRef, command), binding);
        return new OrderCreateResult(order, true);
    }

    @Override
    public OrderProjection requireOrder(String projectSubjectRef, String orderRef) {
        OrderProjection order = orders.get(subjectKey(projectSubjectRef, orderRef));
        if (order == null) throw new FlowRejectedException("ORDER_NOT_FOUND");
        return order;
    }

    @Override
    public synchronized PaymentIntentCreateResult createPaymentIntent(String subject, PaymentIntentDraft draft,
                                                                       CommandIdentity command,
                                                                       long expectedProjectionVersion,
                                                                       long expectedAggregateVersion,
                                                                       Runnable firstCreationQualification) {
        String businessScope = subjectKey(subject, draft.businessKey());
        String commandScope = subjectKey(subject, command.commandId());
        String idempotencyScope = paymentIntentIdempotencyScope(subject, command);
        String semanticScope = subjectKey(subject, draft.semanticActionKey());
        PaymentIntentBinding byBusiness = paymentIntentBusinessKeys.get(businessScope);
        PaymentIntentBinding byCommand = paymentIntentCommandKeys.get(commandScope);
        PaymentIntentBinding byIdempotency = paymentIntentIdempotencyKeys.get(idempotencyScope);
        PaymentIntentBinding bySemantic = paymentIntentSemanticKeys.get(semanticScope);
        if (byCommand == null && (orderCommandKeys.containsKey(commandScope) || commands.containsKey(commandScope))) {
            throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
        }
        PaymentIntentBinding replay = first(byBusiness, byCommand, byIdempotency, bySemantic);
        if (replay != null) {
            if (!matchesPaymentIntent(replay, draft, command)
                    || !samePaymentIntent(replay, byBusiness)
                    || !samePaymentIntent(replay, byCommand)
                    || !samePaymentIntent(replay, byIdempotency)
                    || !samePaymentIntent(replay, bySemantic)) {
                throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
            }
            PaymentIntentRecord existing = requirePaymentIntent(subject, replay.paymentIntentRef());
            return new PaymentIntentCreateResult(existing, requireOrder(subject, existing.orderRef()), false);
        }

        firstCreationQualification.run();
        OrderProjection current = requireOrder(subject, draft.orderRef());
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

        OrderProjection updated = new OrderProjection(current.orderRef(), current.quoteRef(), current.orderState(),
                current.paymentState(), current.upstreamDebitState(), current.deliveryState(), current.refundState(),
                current.totalAmountMinor(), current.currency(), current.projectionVersion() + 1,
                current.aggregateVersion() + 1, MockFlowService.QUERY_PAYMENT_INTENT_ACTION);
        PaymentIntentRecord record = new PaymentIntentRecord(draft.paymentIntentRef(), draft.environment(),
                draft.projectSubjectRef(), draft.orderRef(), draft.businessKey(), draft.semanticActionKey(),
                draft.requestFingerprint(), draft.priceSnapshot(), draft.priceSnapshotDigest(),
                draft.paymentEligibilityDecisionRef(), draft.scope(), draft.createdAt());
        PaymentIntentBinding binding = new PaymentIntentBinding(command, draft.businessKey(),
                draft.semanticActionKey(), draft.requestFingerprint(), draft.paymentIntentRef());
        orders.put(subjectKey(subject, current.orderRef()), updated);
        paymentIntentRecords.put(subjectKey(subject, draft.paymentIntentRef()), record);
        paymentIntentBusinessKeys.put(businessScope, binding);
        paymentIntentCommandKeys.put(commandScope, binding);
        paymentIntentIdempotencyKeys.put(idempotencyScope, binding);
        paymentIntentSemanticKeys.put(semanticScope, binding);
        semanticActions.add(subjectKey(subject, draft.semanticActionKey()));
        paymentIntents.add(subjectKey(subject, draft.paymentIntentRef()));
        return new PaymentIntentCreateResult(record, updated, true);
    }

    @Override
    public PaymentIntentRecord requirePaymentIntent(String projectSubjectRef, String paymentIntentRef) {
        PaymentIntentRecord paymentIntent = paymentIntentRecords.get(subjectKey(projectSubjectRef, paymentIntentRef));
        if (paymentIntent == null) throw new FlowRejectedException("PAYMENT_INTENT_NOT_AVAILABLE");
        return paymentIntent;
    }

    @Override
    public synchronized PaymentIntentResultRecord queryPaymentIntentResult(String subject, String environment,
                                                                            String orderRef,
                                                                            String originalCommandId,
                                                                            String originalIdempotencyKey) {
        String resultKey = paymentIntentResultKey(environment, subject, orderRef, originalCommandId,
                originalIdempotencyKey);
        if (paymentIntentResultRuntimeFailures.contains(resultKey)) {
            throw new IllegalStateException("controlled payment intent result read failure");
        }
        PaymentIntentResultRecord override = paymentIntentResultOverrides.get(resultKey);
        return override != null ? override : createdPaymentIntentResult(subject, environment, orderRef,
                originalCommandId, originalIdempotencyKey);
    }

    @Override
    public synchronized void recordPaymentIntentReviewSignal(PaymentIntentReviewSignalInput input) {
        PaymentIntentReviewSignal signal = PaymentIntentReviewSignal.from(input);
        paymentIntentReviewSignals.putIfAbsent(subjectKey(input.projectSubjectRef(), signal.reviewSignalRef()),
                signal);
    }

    private PaymentIntentResultRecord createdPaymentIntentResult(String subject, String environment,
                                                                  String orderRef, String originalCommandId,
                                                                  String originalIdempotencyKey) {
        PaymentIntentBinding byCommand = paymentIntentCommandKeys.get(subjectKey(subject, originalCommandId));
        String idempotencyScope = subjectKey(subject,
                "POST:/api/v1/orders/{orderRef}/payment-intents:" + originalIdempotencyKey);
        PaymentIntentBinding byIdempotency = paymentIntentIdempotencyKeys.get(idempotencyScope);
        if (byCommand == null || byIdempotency == null
                || !byCommand.paymentIntentRef().equals(byIdempotency.paymentIntentRef())) return null;
        CommandIdentity command = byCommand.command();
        if (!originalCommandId.equals(command.commandId())
                || !originalIdempotencyKey.equals(command.idempotencyKey())
                || !"POST:/api/v1/orders/{orderRef}/payment-intents".equals(command.endpointScope())
                || !orderRef.equals(command.resourceScope())) return null;
        PaymentIntentRecord intent = paymentIntentRecords.get(subjectKey(subject, byCommand.paymentIntentRef()));
        if (intent == null || !environment.equals(intent.environment()) || !subject.equals(intent.projectSubjectRef())
                || !orderRef.equals(intent.orderRef()) || !byCommand.businessKey().equals(intent.businessKey())
                || !byCommand.semanticActionKey().equals(intent.semanticActionKey())
                || !byCommand.requestFingerprint().equals(intent.requestFingerprint())) return null;
        return new PaymentIntentResultRecord(environment, subject, orderRef, originalCommandId,
                originalIdempotencyKey, byCommand.businessKey(), byCommand.semanticActionKey(),
                byCommand.requestFingerprint(), byCommand.paymentIntentRef(), PaymentIntentResultState.FOUND, null);
    }

    private OrderProjection applyAuthorizedTransition(String projectSubjectRef, String orderRef,
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
        recordSideEffects(projectSubjectRef, current, updated, command);
        record(projectSubjectRef, command, orderRef);
        return updated;
    }

    @Override
    public synchronized OrderProjection transitionOrderAuthorized(String projectSubjectRef, String orderRef,
            StateAdvanceCommand advance, long expectedProjectionVersion, long expectedAggregateVersion,
            UnaryOperator<OrderProjection> transition) {
        StateAdvanceGate.AggregateIdentity identity=aggregateIdentities.get(subjectKey(projectSubjectRef,orderRef));
        StateAdvanceGate.verify(orderRef,identity,advance);
        String factKey=subjectKey(projectSubjectRef,orderRef+":"+advance.command().commandId());
        OrderProjection result=applyAuthorizedTransition(projectSubjectRef, orderRef, advance.command(), expectedProjectionVersion,
                expectedAggregateVersion, transition);
        stateAdvanceAuthorityFacts.putIfAbsent(factKey,advance);
        return result;
    }

    synchronized long stateAdvanceAuthorityFactCountForTest(){return stateAdvanceAuthorityFacts.size();}

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

    private static String orderIdempotencyScope(String subject, CommandIdentity command) {
        return subjectKey(subject, command.endpointScope() + ":" + command.idempotencyKey());
    }

    private static String paymentIntentIdempotencyScope(String subject, CommandIdentity command) {
        return subjectKey(subject, command.endpointScope() + ":" + command.idempotencyKey());
    }

    private static PaymentIntentBinding first(PaymentIntentBinding... bindings) {
        for (PaymentIntentBinding binding : bindings) if (binding != null) return binding;
        return null;
    }

    private static boolean samePaymentIntent(PaymentIntentBinding expected, PaymentIntentBinding candidate) {
        return candidate == null || expected.paymentIntentRef().equals(candidate.paymentIntentRef());
    }

    private static boolean matchesPaymentIntent(PaymentIntentBinding binding, PaymentIntentDraft draft,
                                                CommandIdentity command) {
        return binding.command().equals(command)
                && binding.businessKey().equals(draft.businessKey())
                && binding.semanticActionKey().equals(draft.semanticActionKey())
                && binding.requestFingerprint().equals(draft.requestFingerprint());
    }

    private static boolean compatible(OrderBinding binding, CommandIdentity command) {
        return binding == null || binding.fingerprint().equals(command.canonicalFingerprint());
    }

    private static boolean sameResource(OrderBinding left, OrderBinding right) {
        return left == null || right == null || left.resourceRef().equals(right.resourceRef());
    }

    synchronized long orderCountForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return orders.keySet().stream().filter(key -> key.startsWith(prefix)).count();
    }

    synchronized long orderBusinessKeyCountForTest() { return orderBusinessKeys.size(); }

    synchronized long orderBusinessKeyCountForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return orderBusinessKeys.keySet().stream().filter(key -> key.startsWith(prefix)).count();
    }

    synchronized long semanticActionCountForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return semanticActions.stream().filter(key -> key.startsWith(prefix)).count();
    }

    synchronized long paymentIntentCountForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return paymentIntentRecords.keySet().stream().filter(key -> key.startsWith(prefix)).count();
    }

    synchronized long paymentIntentBusinessKeyCountForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return paymentIntentBusinessKeys.keySet().stream().filter(key -> key.startsWith(prefix)).count();
    }

    synchronized PaymentIntentEvidenceSnapshot paymentIntentEvidenceSnapshot(String projectSubjectRef) {
        return new PaymentIntentEvidenceSnapshot(orderCountForTest(projectSubjectRef),
                paymentIntentCommandCountForTest(projectSubjectRef),
                paymentIntentCommandAliasCountForTest(projectSubjectRef),
                orderVersionTotalForTest(projectSubjectRef), projectionVersionTotalForTest(projectSubjectRef),
                semanticActionCountForTest(projectSubjectRef), paymentIntentCountForTest(projectSubjectRef),
                paymentIntentBusinessKeyCountForTest(projectSubjectRef),
                paymentIntentReviewSignalCountForTest(projectSubjectRef), sideEffectSnapshot(projectSubjectRef));
    }

    synchronized long paymentIntentReviewSignalCountForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return paymentIntentReviewSignals.keySet().stream().filter(key -> key.startsWith(prefix)).count();
    }

    synchronized java.util.List<PaymentIntentReviewSignal> paymentIntentReviewSignalsForTest(
            String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return paymentIntentReviewSignals.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue).toList();
    }

    private long paymentIntentCommandCountForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return paymentIntentCommandKeys.keySet().stream().filter(key -> key.startsWith(prefix)).count();
    }

    private long paymentIntentCommandAliasCountForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return paymentIntentIdempotencyKeys.keySet().stream().filter(key -> key.startsWith(prefix)).count();
    }

    private long orderVersionTotalForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return orders.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                .mapToLong(entry -> entry.getValue().aggregateVersion()).sum();
    }

    private long projectionVersionTotalForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return orders.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                .mapToLong(entry -> entry.getValue().projectionVersion()).sum();
    }

    synchronized void maskPaymentIntentResultForTest(String environment, String projectSubjectRef, String orderRef,
                                                      String originalCommandId, String originalIdempotencyKey,
                                                      PaymentIntentResultState state, java.time.Instant nextPollAt) {
        PaymentIntentResultRecord created = createdPaymentIntentResult(projectSubjectRef, environment, orderRef,
                originalCommandId, originalIdempotencyKey);
        if (created == null) throw new IllegalArgumentException("original payment intent binding is required");
        paymentIntentResultOverrides.put(paymentIntentResultKey(environment, projectSubjectRef, orderRef,
                originalCommandId, originalIdempotencyKey), copyResultState(created, state, nextPollAt));
    }

    synchronized void installRejectedPaymentIntentResultForTest(String environment, String projectSubjectRef,
                                                                 String orderRef, String originalCommandId,
                                                                 String originalIdempotencyKey,
                                                                 PaymentIntentResultState state,
                                                                 java.time.Instant nextPollAt) {
        if (state == PaymentIntentResultState.FOUND) {
            throw new IllegalArgumentException("FOUND requires an original committed PaymentIntent");
        }
        PaymentIntentResultRecord record = new PaymentIntentResultRecord(environment, projectSubjectRef, orderRef,
                originalCommandId, originalIdempotencyKey,
                MockFlowService.paymentIntentBusinessKey(environment, projectSubjectRef, orderRef),
                MockFlowService.paymentIntentSemanticActionKey(environment, projectSubjectRef, orderRef),
                CanonicalFingerprint.sha256("PaymentIntentRejectedFingerprint", originalCommandId,
                        originalIdempotencyKey), null, state, nextPollAt);
        paymentIntentResultOverrides.put(paymentIntentResultKey(environment, projectSubjectRef, orderRef,
                originalCommandId, originalIdempotencyKey), record);
    }

    synchronized void convergePaymentIntentResultForTest(String environment, String projectSubjectRef,
                                                          String orderRef, String originalCommandId,
                                                          String originalIdempotencyKey,
                                                          PaymentIntentResultState authoritativeState) {
        String key = paymentIntentResultKey(environment, projectSubjectRef, orderRef, originalCommandId,
                originalIdempotencyKey);
        PaymentIntentResultRecord current = paymentIntentResultOverrides.get(key);
        if (current == null) throw new IllegalArgumentException("UNKNOWN result observation is required");
        if (current.state() == authoritativeState) return;
        if (current.state() != PaymentIntentResultState.UNKNOWN
                || authoritativeState == PaymentIntentResultState.UNKNOWN
                || (authoritativeState == PaymentIntentResultState.FOUND && current.paymentIntentRef() == null)) {
            recordPaymentIntentReviewSignal(new PaymentIntentReviewSignalInput(environment, projectSubjectRef,
                    orderRef, originalCommandId, originalIdempotencyKey, current.paymentIntentRef(),
                    PaymentIntentReviewReason.AUTHORITATIVE_RESULT_CONFLICT));
            throw new IllegalStateException("payment intent result cannot move non-monotonically");
        }
        paymentIntentResultOverrides.put(key, copyResultState(current, authoritativeState, null));
    }

    private static PaymentIntentResultRecord copyResultState(PaymentIntentResultRecord source,
                                                               PaymentIntentResultState state,
                                                               java.time.Instant nextPollAt) {
        return new PaymentIntentResultRecord(source.environment(), source.projectSubjectRef(), source.orderRef(),
                source.originalCommandId(), source.originalIdempotencyKey(), source.businessKey(),
                source.semanticActionKey(), source.requestFingerprint(), source.paymentIntentRef(), state,
                state == PaymentIntentResultState.UNKNOWN ? nextPollAt : null);
    }

    private static String paymentIntentResultKey(String environment, String subject, String orderRef,
                                                  String commandId, String idempotencyKey) {
        return String.join("\u0000", environment, subject, orderRef, commandId, idempotencyKey);
    }

    synchronized void replaceOrderForTest(String projectSubjectRef, OrderProjection order) {
        orders.put(subjectKey(projectSubjectRef, order.orderRef()), order);
    }

    synchronized OrderCreationSideEffectSnapshot sideEffectSnapshot(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        return new OrderCreationSideEffectSnapshot(count(paymentIntents, prefix), count(paymentAttempts, prefix),
                count(wechatPrepayCalls, prefix), count(requestPaymentCalls, prefix),
                count(paymentNotifications, prefix), count(dispatchIntents, prefix), count(wechatPaymentFacts, prefix),
                count(upstreamDebitFacts, prefix), count(deliveryFacts, prefix), count(refundFacts, prefix),
                count(localLedgerFacts, prefix), count(ledgerEntries, prefix), count(externalCalls, prefix));
    }

    synchronized void installQuoteForTest(String projectSubjectRef, Quote quote) {
        quotes.put(subjectKey(projectSubjectRef, quote.quoteRef()), quote);
    }

    synchronized void writeObservedSideEffectForSensitivityTest(String projectSubjectRef, String boundary) {
        String observationRef = subjectKey(projectSubjectRef, "SENSITIVITY:" + boundary + ":" + UUID.randomUUID());
        switch (boundary) {
            case "PaymentAttempt" -> paymentAttempts.add(observationRef);
            case "requestPayment" -> requestPaymentCalls.add(observationRef);
            case "WechatPrepay" -> wechatPrepayCalls.add(observationRef);
            case "Notification" -> paymentNotifications.add(observationRef);
            case "DispatchIntent" -> dispatchIntents.add(observationRef);
            case "W" -> wechatPaymentFacts.add(observationRef);
            case "U" -> upstreamDebitFacts.add(observationRef);
            case "D" -> deliveryFacts.add(observationRef);
            case "R" -> refundFacts.add(observationRef);
            case "L" -> localLedgerFacts.add(observationRef);
            case "LedgerEntry" -> ledgerEntries.add(observationRef);
            case "ExternalCall" -> externalCalls.add(observationRef);
            default -> throw new IllegalArgumentException("unknown observed side-effect boundary: " + boundary);
        }
    }

    synchronized void writeObservedPaymentIntentMetadataForSensitivityTest(String projectSubjectRef,
                                                                            String orderRef, String boundary) {
        String unique = "SENSITIVITY:" + boundary + ":" + UUID.randomUUID();
        switch (boundary) {
            case "Command" -> {
                CommandIdentity command = new CommandIdentity("CMD-" + unique, "IDEM-" + unique,
                        "POST:/api/v1/orders/{orderRef}/payment-intents", orderRef,
                        "SEM-" + unique, "FP-" + unique);
                PaymentIntentBinding binding = new PaymentIntentBinding(command, "BK-" + unique,
                        command.semanticActionKey(), command.canonicalFingerprint(), "PI-" + unique);
                paymentIntentCommandKeys.put(subjectKey(projectSubjectRef, command.commandId()), binding);
            }
            case "CommandAlias" -> {
                CommandIdentity command = new CommandIdentity("CMD-" + unique, "IDEM-" + unique,
                        "POST:/api/v1/orders/{orderRef}/payment-intents", orderRef,
                        "SEM-" + unique, "FP-" + unique);
                PaymentIntentBinding binding = new PaymentIntentBinding(command, "BK-" + unique,
                        command.semanticActionKey(), command.canonicalFingerprint(), "PI-" + unique);
                paymentIntentIdempotencyKeys.put(paymentIntentIdempotencyScope(projectSubjectRef, command), binding);
            }
            case "OrderVersion" -> {
                OrderProjection current = requireOrder(projectSubjectRef, orderRef);
                orders.put(subjectKey(projectSubjectRef, orderRef), new OrderProjection(current.orderRef(),
                        current.quoteRef(), current.orderState(), current.paymentState(), current.upstreamDebitState(),
                        current.deliveryState(), current.refundState(), current.totalAmountMinor(), current.currency(),
                        current.projectionVersion(), current.aggregateVersion() + 1, current.nextAction()));
            }
            case "ProjectionVersion" -> {
                OrderProjection current = requireOrder(projectSubjectRef, orderRef);
                orders.put(subjectKey(projectSubjectRef, orderRef), new OrderProjection(current.orderRef(),
                        current.quoteRef(), current.orderState(), current.paymentState(), current.upstreamDebitState(),
                        current.deliveryState(), current.refundState(), current.totalAmountMinor(), current.currency(),
                        current.projectionVersion() + 1, current.aggregateVersion(), current.nextAction()));
            }
            default -> throw new IllegalArgumentException("unknown payment intent metadata boundary: " + boundary);
        }
    }

    synchronized void installPaymentIntentResultBindingConflictForTest(String environment, String projectSubjectRef,
                                                                        String orderRef, String originalCommandId,
                                                                        String originalIdempotencyKey) {
        PaymentIntentResultRecord created = createdPaymentIntentResult(projectSubjectRef, environment, orderRef,
                originalCommandId, originalIdempotencyKey);
        if (created == null) throw new IllegalArgumentException("original payment intent binding is required");
        paymentIntentResultOverrides.put(paymentIntentResultKey(environment, projectSubjectRef, orderRef,
                        originalCommandId, originalIdempotencyKey),
                new PaymentIntentResultRecord(created.environment(), created.projectSubjectRef(), created.orderRef(),
                        created.originalCommandId(), created.originalIdempotencyKey(), created.businessKey(),
                        created.semanticActionKey() + ":CONFLICT", created.requestFingerprint(),
                        created.paymentIntentRef(), created.state(), created.nextPollAt()));
    }

    synchronized void failPaymentIntentResultQueryForTest(String environment, String projectSubjectRef,
                                                           String orderRef, String originalCommandId,
                                                           String originalIdempotencyKey) {
        paymentIntentResultRuntimeFailures.add(paymentIntentResultKey(environment, projectSubjectRef, orderRef,
                originalCommandId, originalIdempotencyKey));
    }

    synchronized void resetSubjectForTest(String projectSubjectRef) {
        String prefix = projectSubjectRef + "\u0000";
        quotes.keySet().removeIf(key -> key.startsWith(prefix));
        orders.keySet().removeIf(key -> key.startsWith(prefix));
        commands.keySet().removeIf(key -> key.startsWith(prefix));
        idempotencyToCommand.keySet().removeIf(key -> key.startsWith(prefix));
        orderBusinessKeys.keySet().removeIf(key -> key.startsWith(prefix));
        orderCommandKeys.keySet().removeIf(key -> key.startsWith(prefix));
        orderIdempotencyKeys.keySet().removeIf(key -> key.startsWith(prefix));
        paymentIntentRecords.keySet().removeIf(key -> key.startsWith(prefix));
        paymentIntentBusinessKeys.keySet().removeIf(key -> key.startsWith(prefix));
        paymentIntentCommandKeys.keySet().removeIf(key -> key.startsWith(prefix));
        paymentIntentIdempotencyKeys.keySet().removeIf(key -> key.startsWith(prefix));
        paymentIntentSemanticKeys.keySet().removeIf(key -> key.startsWith(prefix));
        paymentIntentResultOverrides.keySet().removeIf(key -> key.contains("\u0000" + projectSubjectRef + "\u0000"));
        paymentIntentReviewSignals.keySet().removeIf(key -> key.startsWith(prefix));
        paymentIntentResultRuntimeFailures.removeIf(key -> key.contains("\u0000" + projectSubjectRef + "\u0000"));
        semanticActions.removeIf(key -> key.startsWith(prefix));
        paymentIntents.removeIf(key -> key.startsWith(prefix));
        paymentAttempts.removeIf(key -> key.startsWith(prefix));
        wechatPrepayCalls.removeIf(key -> key.startsWith(prefix));
        requestPaymentCalls.removeIf(key -> key.startsWith(prefix));
        paymentNotifications.removeIf(key -> key.startsWith(prefix));
        dispatchIntents.removeIf(key -> key.startsWith(prefix));
        wechatPaymentFacts.removeIf(key -> key.startsWith(prefix));
        upstreamDebitFacts.removeIf(key -> key.startsWith(prefix));
        deliveryFacts.removeIf(key -> key.startsWith(prefix));
        refundFacts.removeIf(key -> key.startsWith(prefix));
        localLedgerFacts.removeIf(key -> key.startsWith(prefix));
        ledgerEntries.removeIf(key -> key.startsWith(prefix));
        externalCalls.removeIf(key -> key.startsWith(prefix));
    }

    private void recordSideEffects(String subject, OrderProjection before, OrderProjection after,
                                   CommandIdentity command) {
        String actionKey = subjectKey(subject, command.semanticActionKey());
        if (command.endpointScope().endsWith("/mock-payment")) paymentAttempts.add(actionKey);
        if (command.endpointScope().endsWith("/mock-topup")) dispatchIntents.add(actionKey);
        if (!before.paymentState().equals(after.paymentState()) && !after.paymentState().equals("ABSENT_CONFIRMED")) {
            wechatPaymentFacts.add(actionKey);
        }
        if (!before.upstreamDebitState().equals(after.upstreamDebitState())
                && !after.upstreamDebitState().equals("ABSENT_CONFIRMED")) upstreamDebitFacts.add(actionKey);
        if (!before.deliveryState().equals(after.deliveryState())
                && !after.deliveryState().equals("ABSENT_CONFIRMED")) deliveryFacts.add(actionKey);
        if (!before.refundState().equals(after.refundState())
                && !after.refundState().equals("ABSENT_CONFIRMED")) refundFacts.add(actionKey);
    }

    private static long count(Set<String> boundary, String prefix) {
        return boundary.stream().filter(key -> key.startsWith(prefix)).count();
    }

    private record CommandRecord(CommandIdentity command, String resourceRef) {}
    private record OrderBinding(String fingerprint, String resourceRef) {}
    private record PaymentIntentBinding(CommandIdentity command, String businessKey, String semanticActionKey,
                                        String requestFingerprint, String paymentIntentRef) {}
}
