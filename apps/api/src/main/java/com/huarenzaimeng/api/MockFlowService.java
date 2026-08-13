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
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockFlowService {
    static final String ORDER_CREATION_PRECONDITION = "ORDER_MUST_NOT_EXIST";
    static final String PAYMENT_INTENT_CREATION_PRECONDITION = "PAYMENT_INTENT_MUST_NOT_EXIST";
    static final String CREATE_PAYMENT_INTENT_ACTION = "CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT";
    static final String QUERY_PAYMENT_INTENT_ACTION = "QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT";
    static final String PAYMENT_INTENT_SCOPE = "LOCAL_SYNTHETIC_ONLY";
    static final String PAYMENT_ELIGIBILITY_RULE_VERSION = "LOCAL_SYNTHETIC_A1_PAYMENT_ELIGIBILITY_V1";
    private final FlowStore store;
    private final CatalogService catalog;
    private final Clock clock;
    private final Map<String, PaymentEligibilityDecision> paymentEligibilityDecisions = new ConcurrentHashMap<>();
    private final Map<String, Long> paymentIntentResultQueryCalls = new ConcurrentHashMap<>();
    private final Map<String, Long> paymentIntentPostCalls = new ConcurrentHashMap<>();

    @Autowired
    public MockFlowService(FlowStore store, CatalogService catalog, Clock clock) {
        this.store = store;
        this.catalog = catalog;
        this.clock = clock;
    }

    MockFlowService(FlowStore store) {
        this.store = store;
        this.clock = Clock.systemUTC();
        this.catalog = new CatalogService(new InMemoryCatalogStore(clock));
    }

    public Quote createQuote(String projectSubjectRef, String phone, String operatorCode, String productRef,
                             String denominationRef, long supportedOperatorSetVersion, long catalogVersion,
                             String commandId, String idempotencyKey, MnpState mnpState) {
        if (mnpState != MnpState.CONFIRMED) {
            throw new FlowRejectedException("PREPAY_MNP_NOT_CONFIRMED");
        }
        CatalogSelection selected = catalog.requirePreset(supportedOperatorSetVersion, catalogVersion, operatorCode,
                productRef, denominationRef);
        String quoteRef = "Q-" + UUID.randomUUID();
        Quote quote = new Quote(quoteRef, mask(phone), operatorCode, productRef, denominationRef,
                supportedOperatorSetVersion, catalogVersion, selected.amountMinor(), selected.currency(),
                clock.instant().plus(10, ChronoUnit.MINUTES));
        CommandIdentity command = command(commandId, idempotencyKey, "POST:/api/v1/quotes", projectSubjectRef,
                "QUOTE_CREATE:" + commandId, projectSubjectRef, phone, operatorCode, productRef, denominationRef,
                String.valueOf(supportedOperatorSetVersion), String.valueOf(catalogVersion));
        return store.createQuote(projectSubjectRef, quote, command);
    }

    // Package-compatible local test helper; the HTTP project API always requires explicit versions and keys.
    Quote createQuote(String projectSubjectRef, String phone, String operatorCode, String productRef,
                      MnpState mnpState) {
        String suffix = UUID.randomUUID().toString();
        return createQuote(projectSubjectRef, phone, operatorCode, productRef, "SYN-DENOM-1000", 1L, 1L,
                "CMD-LEGACY-TEST-" + suffix, "IDEM-LEGACY-TEST-" + suffix, mnpState);
    }

    public ProjectProjection createOrder(String projectSubjectRef, String quoteRef, String commandId,
                                         String idempotencyKey) {
        Quote quote = store.requireQuote(projectSubjectRef, quoteRef);
        if (!quote.expiresAt().isAfter(clock.instant())) throw new FlowRejectedException("QUOTE_EXPIRED");
        CommandIdentity command = command(commandId, idempotencyKey, "POST:/api/v1/orders", quoteRef,
                "ORDER_CREATE:" + quoteRef, projectSubjectRef, quoteRef);
        return project(projectSubjectRef, store.createOrder(projectSubjectRef, quote, command).order());
    }

    OrderCreationResponse createLocalSyntheticOrder(BuyerAuthorization authorization, String quoteRef,
                                                     String orderCreationPrecondition, String commandId,
                                                     String idempotencyKey) {
        if (!ORDER_CREATION_PRECONDITION.equals(orderCreationPrecondition)) {
            throw new FlowRejectedException("ORDER_CREATION_PRECONDITION_INVALID");
        }
        String businessKey = String.join(":", "ORDER_CREATE", authorization.environment(),
                authorization.projectSubjectRef(), quoteRef);
        CommandIdentity command = command(commandId, idempotencyKey, "POST:/api/v1/orders", quoteRef,
                businessKey, authorization.environment(), authorization.projectSubjectRef(),
                String.valueOf(authorization.sessionVersion()), authorization.authorizationSetRef(),
                authorization.authorizationEvidenceVersion(), quoteRef, orderCreationPrecondition);
        OrderCreateResult replay = store.replayOrder(authorization.projectSubjectRef(), command);
        if (replay != null) {
            Quote originalQuote = store.requireQuote(authorization.projectSubjectRef(), replay.order().quoteRef());
            return orderCreationResponse(commandId, replay, originalQuote);
        }

        Quote quote;
        try {
            quote = store.requireQuote(authorization.projectSubjectRef(), quoteRef);
            requireCurrentQuote(quote);
        } catch (FlowRejectedException error) {
            if ("IDEMPOTENCY_CONFLICT".equals(error.getMessage())) throw error;
            throw new FlowRejectedException("ORDER_CREATION_NOT_AVAILABLE");
        }
        OrderCreateResult result = store.createOrder(authorization.projectSubjectRef(), quote, command);
        return orderCreationResponse(commandId, result, quote);
    }

    private OrderCreationResponse orderCreationResponse(String commandId, OrderCreateResult result, Quote quote) {
        OrderProjection order = result.order();
        OrderCreationProjection current = new OrderCreationProjection(order.orderRef(), order.quoteRef(),
                publicState(order), PriceSnapshot.from(quote), order.projectionVersion(),
                order.aggregateVersion(), allowedActions(order));
        return new OrderCreationResponse(commandId, "ACCEPTED",
                result.created() ? "ORDER_CREATED" : "ORDER_REPLAYED", order.orderRef(),
                order.aggregateVersion(), current, "NONE");
    }

    PaymentIntentResponse createLocalSyntheticPaymentIntent(BuyerAuthorization authorization, String orderRef,
                                                              String creationPrecondition, String commandId,
                                                              String idempotencyKey,
                                                              long expectedProjectionVersion,
                                                              long expectedAggregateVersion) {
        paymentIntentPostCalls.merge(authorization.projectSubjectRef(), 1L, Long::sum);
        if (!PAYMENT_INTENT_CREATION_PRECONDITION.equals(creationPrecondition)) {
            throw new FlowRejectedException("PAYMENT_INTENT_CREATION_PRECONDITION_INVALID");
        }
        if (!authorization.authorizedOrderRefs().contains(orderRef)) {
            throw new FlowRejectedException("PAYMENT_INTENT_NOT_AVAILABLE");
        }

        OrderProjection order;
        Quote quote;
        try {
            order = store.requireOrder(authorization.projectSubjectRef(), orderRef);
            quote = store.requireQuote(authorization.projectSubjectRef(), order.quoteRef());
        } catch (FlowRejectedException error) {
            throw new FlowRejectedException("PAYMENT_INTENT_NOT_AVAILABLE");
        }
        PriceSnapshot snapshot = PriceSnapshot.from(quote);
        requireCompletePriceSnapshot(snapshot);
        String snapshotDigest = priceSnapshotDigest(snapshot);
        PaymentEligibilityDecision eligibility = paymentEligibilityDecisions.get(orderRef);
        if (eligibility == null) throw new FlowRejectedException("PAYMENT_INTENT_NOT_AVAILABLE");
        String eligibilityRef = eligibility.decisionRef();
        String businessKey = paymentIntentBusinessKey(authorization.environment(),
                authorization.projectSubjectRef(), orderRef);
        String semanticActionKey = paymentIntentSemanticActionKey(authorization.environment(),
                authorization.projectSubjectRef(), orderRef);
        String fingerprint = CanonicalFingerprint.sha256(
                "Environment", authorization.environment(),
                "ProjectSubjectRef", authorization.projectSubjectRef(),
                "SessionRole", "BUYER",
                "sessionVersion", String.valueOf(authorization.sessionVersion()),
                "authorizationSetRef", authorization.authorizationSetRef(),
                "authorizationEvidenceVersion", authorization.authorizationEvidenceVersion(),
                "OrderRef", orderRef,
                "PaymentIntentCreationPrecondition", creationPrecondition,
                "ExpectedProjectionVersion", String.valueOf(expectedProjectionVersion),
                "ExpectedAggregateVersion", String.valueOf(expectedAggregateVersion),
                "PriceSnapshotDigest", snapshotDigest,
                "PaymentEligibilityDecisionRef", eligibilityRef,
                "AllowedActionCode", CREATE_PAYMENT_INTENT_ACTION);
        CommandIdentity command = new CommandIdentity(commandId, idempotencyKey,
                "POST:/api/v1/orders/{orderRef}/payment-intents", orderRef, semanticActionKey, fingerprint);
        PaymentIntentDraft draft = new PaymentIntentDraft("PI-" + UUID.randomUUID(), authorization.environment(),
                authorization.projectSubjectRef(), orderRef, businessKey, semanticActionKey, fingerprint, snapshot,
                snapshotDigest, eligibilityRef, PAYMENT_INTENT_SCOPE, clock.instant());
        PaymentIntentCreateResult result = store.createPaymentIntent(authorization.projectSubjectRef(), draft,
                command, expectedProjectionVersion, expectedAggregateVersion,
                () -> requireFirstPaymentIntentQualification(authorization, order, quote, snapshotDigest,
                        eligibility));
        return paymentIntentResponse(commandId, result);
    }

    private PaymentIntentResponse paymentIntentResponse(String requestRef, PaymentIntentCreateResult result) {
        OrderProjection order = result.order();
        PaymentIntentRecord paymentIntent = result.paymentIntent();
        PaymentIntentCurrentProjection current = new PaymentIntentCurrentProjection(order.orderRef(),
                publicState(order), paymentIntent.priceSnapshot(), paymentIntent.scope(), false, false,
                order.projectionVersion(),
                order.aggregateVersion(), allowedActions(order));
        return new PaymentIntentResponse(requestRef, "ACCEPTED",
                result.created() ? "PAYMENT_INTENT_CREATED" : "PAYMENT_INTENT_REPLAYED",
                paymentIntent.paymentIntentRef(), order.aggregateVersion(), current, "NONE");
    }

    PaymentIntentResultResponse queryLocalSyntheticPaymentIntentResult(
            LocalSyntheticOrderRecoveryService orderRecovery,
            String environment, String projectSubjectRef, String sessionRef, String orderRef,
            String originalCommandId, String originalIdempotencyKey, String sessionVersion,
            String authorizationSetRef, boolean invalidQueryShape) {
        paymentIntentResultQueryCalls.merge(projectSubjectRef == null ? "UNAUTHENTICATED" : projectSubjectRef,
                1L, Long::sum);
        try {
            if (invalidQueryShape || isBlank(orderRef) || isBlank(originalCommandId)
                    || isBlank(originalIdempotencyKey) || isBlank(sessionVersion)
                    || isBlank(authorizationSetRef)) return paymentIntentResultNotAvailable(originalCommandId);
            long parsedSessionVersion = Long.parseLong(sessionVersion);
            if (parsedSessionVersion < 1 || parsedSessionVersion > ProjectApiVersion.MAX) {
                return paymentIntentResultNotAvailable(originalCommandId);
            }
            BuyerAuthorization authorization = orderRecovery.requirePaymentIntentBuyerAuthorization(environment,
                    projectSubjectRef, sessionRef, parsedSessionVersion, authorizationSetRef);
            if (!authorization.authorizedOrderRefs().contains(orderRef)) {
                return paymentIntentResultNotAvailable(originalCommandId);
            }
            PaymentIntentResultRecord result = store.queryPaymentIntentResult(projectSubjectRef,
                    authorization.environment(), orderRef, originalCommandId, originalIdempotencyKey);
            if (!validPaymentIntentResultBinding(result, authorization, orderRef, originalCommandId,
                    originalIdempotencyKey)) {
                if (result != null) recordPaymentIntentReviewSignal(authorization.environment(), projectSubjectRef,
                        orderRef, originalCommandId, originalIdempotencyKey, result.paymentIntentRef(),
                        PaymentIntentReviewReason.PAYMENT_INTENT_RESULT_BINDING_CONFLICT);
                return paymentIntentResultNotAvailable(originalCommandId);
            }
            if (result.state() == PaymentIntentResultState.UNKNOWN) {
                return new PaymentIntentResultResponse(originalCommandId, "UNKNOWN",
                        "PAYMENT_INTENT_RESULT_UNKNOWN", null, null, null,
                        "SAME_ACTION_QUERY_ONLY", result.nextPollAt());
            }
            if (result.state() == PaymentIntentResultState.REJECTED) {
                if (result.paymentIntentRef() != null || result.nextPollAt() != null) {
                    return paymentIntentResultNotAvailable(originalCommandId);
                }
                return new PaymentIntentResultResponse(originalCommandId, "REJECTED",
                        "PAYMENT_INTENT_RESULT_REJECTED", null, null, null, "NONE", null);
            }
            PaymentIntentRecord intent = store.requirePaymentIntent(projectSubjectRef, result.paymentIntentRef());
            OrderProjection order = store.requireOrder(projectSubjectRef, orderRef);
            if (!result.businessKey().equals(intent.businessKey())
                    || !result.semanticActionKey().equals(intent.semanticActionKey())
                    || !result.requestFingerprint().equals(intent.requestFingerprint())
                    || !result.paymentIntentRef().equals(intent.paymentIntentRef())
                    || !PAYMENT_INTENT_SCOPE.equals(intent.scope())
                    || !authorization.environment().equals(intent.environment())
                    || !projectSubjectRef.equals(intent.projectSubjectRef())
                    || !orderRef.equals(intent.orderRef())) {
                recordPaymentIntentReviewSignal(authorization.environment(), projectSubjectRef, orderRef,
                        originalCommandId, originalIdempotencyKey, result.paymentIntentRef(),
                        PaymentIntentReviewReason.PAYMENT_INTENT_RESULT_BINDING_CONFLICT);
                return paymentIntentResultNotAvailable(originalCommandId);
            }
            PaymentIntentCurrentProjection current = new PaymentIntentCurrentProjection(order.orderRef(),
                    publicState(order), intent.priceSnapshot(), intent.scope(), false, false,
                    order.projectionVersion(), order.aggregateVersion(), allowedActions(order));
            return new PaymentIntentResultResponse(originalCommandId, "ACCEPTED",
                    "PAYMENT_INTENT_RESULT_FOUND", intent.paymentIntentRef(), order.aggregateVersion(), current,
                    "NONE", null);
        } catch (PaymentIntentReviewRequiredException review) {
            recordPaymentIntentReviewSignal(environment, projectSubjectRef, orderRef, originalCommandId,
                    originalIdempotencyKey, review.paymentIntentRef(), review.reason());
            return paymentIntentResultNotAvailable(originalCommandId);
        } catch (FlowRejectedException | NumberFormatException expectedValidationRejection) {
            return paymentIntentResultNotAvailable(originalCommandId);
        } catch (RuntimeException unavailable) {
            recordPaymentIntentReviewSignal(environment, projectSubjectRef, orderRef, originalCommandId,
                    originalIdempotencyKey, null, PaymentIntentReviewReason.CONTROLLED_RUNTIME_EXCEPTION);
            return paymentIntentResultNotAvailable(originalCommandId);
        }
    }

    private void recordPaymentIntentReviewSignal(String environment, String projectSubjectRef, String orderRef,
                                                  String originalCommandId, String originalIdempotencyKey,
                                                  String paymentIntentRef, PaymentIntentReviewReason reason) {
        try {
            store.recordPaymentIntentReviewSignal(new PaymentIntentReviewSignalInput(environment, projectSubjectRef,
                    orderRef, originalCommandId, originalIdempotencyKey, paymentIntentRef, reason));
        } catch (RuntimeException reviewEvidenceUnavailable) {
            // The public response remains existence-uniform. Durable review persistence is a separate fail-closed gate.
        }
    }

    private static boolean validPaymentIntentResultBinding(PaymentIntentResultRecord result,
                                                            BuyerAuthorization authorization, String orderRef,
                                                            String originalCommandId,
                                                            String originalIdempotencyKey) {
        return result != null && result.state() != null
                && authorization.environment().equals(result.environment())
                && authorization.projectSubjectRef().equals(result.projectSubjectRef())
                && orderRef.equals(result.orderRef())
                && originalCommandId.equals(result.originalCommandId())
                && originalIdempotencyKey.equals(result.originalIdempotencyKey())
                && paymentIntentBusinessKey(authorization.environment(), authorization.projectSubjectRef(), orderRef)
                .equals(result.businessKey())
                && paymentIntentSemanticActionKey(authorization.environment(), authorization.projectSubjectRef(),
                orderRef).equals(result.semanticActionKey())
                && !isBlank(result.requestFingerprint());
    }

    static String paymentIntentBusinessKey(String environment, String projectSubjectRef, String orderRef) {
        return CanonicalFingerprint.sha256("PaymentIntentBusinessKey", environment, projectSubjectRef, orderRef);
    }

    static String paymentIntentSemanticActionKey(String environment, String projectSubjectRef, String orderRef) {
        String caseKey = CanonicalFingerprint.sha256(environment, projectSubjectRef, orderRef);
        return CanonicalFingerprint.sha256("PaymentIntentSemanticActionKey", caseKey,
                "CREATE_PAYMENT_INTENT", "LOCAL_SYNTHETIC_ELIGIBLE");
    }

    private static PaymentIntentResultResponse paymentIntentResultNotAvailable(String requestRef) {
        return new PaymentIntentResultResponse(requestRef, "REJECTED", "PAYMENT_INTENT_QUERY_NOT_AVAILABLE",
                null, null, null, "NONE", null);
    }

    private void requireFirstPaymentIntentQualification(BuyerAuthorization authorization, OrderProjection order,
                                                        Quote quote, String snapshotDigest,
                                                        PaymentEligibilityDecision eligibility) {
        if (eligibility.status() != PaymentEligibilityDecisionStatus.ELIGIBLE
                || !eligibility.validUntil().isAfter(clock.instant())
                || !eligibility.decisionRef().equals(canonicalPaymentEligibilityDecisionRef(eligibility))
                || isBlank(eligibility.a1EvidenceRef()) || isBlank(eligibility.a1EvidenceVersion())
                || !PAYMENT_ELIGIBILITY_RULE_VERSION.equals(eligibility.ruleVersion())
                || !authorization.environment().equals(eligibility.environment())
                || !authorization.projectSubjectRef().equals(eligibility.projectSubjectRef())
                || !authorization.authorizationEvidenceVersion()
                .equals(eligibility.authorizationEvidenceVersion())
                || !order.orderRef().equals(eligibility.orderRef())
                || !snapshotDigest.equals(eligibility.priceSnapshotDigest())
                || quote.supportedOperatorSetVersion() != eligibility.supportedOperatorSetVersion()
                || quote.catalogVersion() != eligibility.catalogVersion()
                || order.projectionVersion() != eligibility.parentProjectionVersion()
                || order.aggregateVersion() != eligibility.parentAggregateVersion()
                || !CREATE_PAYMENT_INTENT_ACTION.equals(eligibility.allowedActionCode())) {
            throw new FlowRejectedException("PAYMENT_INTENT_NOT_AVAILABLE");
        }
        try {
            requireCurrentQuote(quote);
        } catch (FlowRejectedException error) {
            throw new FlowRejectedException("PAYMENT_INTENT_NOT_AVAILABLE");
        }
    }

    private static String priceSnapshotDigest(PriceSnapshot snapshot) {
        return CanonicalFingerprint.sha256(
                "quoteRef", snapshot.quoteRef(),
                "maskedPhone", snapshot.maskedPhone(),
                "operatorCode", snapshot.operatorCode(),
                "productCode", snapshot.productCode(),
                "denominationRef", snapshot.denominationRef(),
                "supportedOperatorSetVersion", String.valueOf(snapshot.supportedOperatorSetVersion()),
                "catalogVersion", String.valueOf(snapshot.catalogVersion()),
                "totalAmountMinor", String.valueOf(snapshot.totalAmountMinor()),
                "currency", snapshot.currency(),
                "expiresAt", snapshot.expiresAt().toString());
    }

    private static void requireCompletePriceSnapshot(PriceSnapshot snapshot) {
        if (isBlank(snapshot.quoteRef()) || isBlank(snapshot.maskedPhone())
                || isBlank(snapshot.operatorCode()) || isBlank(snapshot.productCode())
                || isBlank(snapshot.denominationRef()) || snapshot.supportedOperatorSetVersion() < 1
                || snapshot.catalogVersion() < 1 || snapshot.totalAmountMinor() < 1
                || isBlank(snapshot.currency()) || snapshot.expiresAt() == null) {
            throw new FlowRejectedException("PAYMENT_INTENT_NOT_AVAILABLE");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String canonicalPaymentEligibilityDecisionRef(PaymentEligibilityDecision decision) {
        return "PED-" + CanonicalFingerprint.sha256(
                "A1EvidenceRef", decision.a1EvidenceRef(),
                "A1EvidenceVersion", decision.a1EvidenceVersion(),
                "Environment", decision.environment(),
                "ProjectSubjectRef", decision.projectSubjectRef(),
                "OrderRef", decision.orderRef(),
                "PriceSnapshotDigest", decision.priceSnapshotDigest(),
                "SupportedOperatorSetVersion", String.valueOf(decision.supportedOperatorSetVersion()),
                "CatalogVersion", String.valueOf(decision.catalogVersion()),
                "ParentProjectionVersion", String.valueOf(decision.parentProjectionVersion()),
                "ParentAggregateVersion", String.valueOf(decision.parentAggregateVersion()),
                "AuthorizationEvidenceVersion", decision.authorizationEvidenceVersion(),
                "AllowedActionCode", decision.allowedActionCode(),
                "RuleVersion", decision.ruleVersion(),
                "Status", decision.status().name(),
                "ValidUntil", decision.validUntil().toString());
    }

    public ProjectProjection confirmMockPayment(String projectSubjectRef, String orderRef, String commandId,
                                                String idempotencyKey, long expectedProjectionVersion,
                                                long expectedAggregateVersion) {
        CommandIdentity command = command(commandId, idempotencyKey, "POST:/api/v1/orders/{orderRef}/mock-payment",
                orderRef, "MOCK_PAYMENT:" + orderRef, projectSubjectRef, orderRef);
        StateAdvanceCommand authority=StateAdvanceAuthorityResolver.resolveControlledLocal(command,orderRef,
                "MOCK_PAYMENT","EVIDENCE-LOCAL-MOCK-PAYMENT",Instant.now(clock));
        OrderProjection updated = store.transitionOrderAuthorized(projectSubjectRef, orderRef, authority, expectedProjectionVersion,
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
        StateAdvanceCommand authority=StateAdvanceAuthorityResolver.resolveControlledLocal(command,orderRef,
                "MOCK_TOPUP","EVIDENCE-LOCAL-MOCK-TOPUP",Instant.now(clock));
        OrderProjection updated = store.transitionOrderAuthorized(projectSubjectRef, orderRef, authority, expectedProjectionVersion,
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

    long quoteCount(String projectSubjectRef) { return store.quoteCount(projectSubjectRef); }

    PaymentEligibilityDecision installPaymentEligibilityDecisionForTest(String environment, String subject,
                                                                         String orderRef,
                                                                         String authorizationEvidenceVersion,
                                                                         PaymentEligibilityDecisionStatus status,
                                                                         Instant validUntil) {
        OrderProjection order = store.requireOrder(subject, orderRef);
        PriceSnapshot snapshot = PriceSnapshot.from(store.requireQuote(subject, order.quoteRef()));
        String digest = priceSnapshotDigest(snapshot);
        PaymentEligibilityDecision unsigned = new PaymentEligibilityDecision("PENDING",
                "A1-EVIDENCE-" + orderRef, "A1-EVIDENCE-V1", environment, subject, orderRef, digest,
                snapshot.supportedOperatorSetVersion(), snapshot.catalogVersion(), order.projectionVersion(),
                order.aggregateVersion(), authorizationEvidenceVersion, CREATE_PAYMENT_INTENT_ACTION,
                PAYMENT_ELIGIBILITY_RULE_VERSION, status, validUntil);
        PaymentEligibilityDecision decision = copyDecision(unsigned,
                canonicalPaymentEligibilityDecisionRef(unsigned));
        paymentEligibilityDecisions.put(orderRef, decision);
        return decision;
    }

    void installPaymentEligibilityDecisionForTest(PaymentEligibilityDecision decision) {
        paymentEligibilityDecisions.put(decision.orderRef(), decision);
    }

    void installPaymentEligibilityDecisionForTest(String lookupOrderRef, PaymentEligibilityDecision decision) {
        paymentEligibilityDecisions.put(lookupOrderRef, decision);
    }

    void removePaymentEligibilityDecisionForTest(String orderRef) {
        paymentEligibilityDecisions.remove(orderRef);
    }

    PaymentEligibilityDecision paymentEligibilityDecisionForTest(String orderRef) {
        return paymentEligibilityDecisions.get(orderRef);
    }

    PaymentEligibilityDecision copyPaymentEligibilityDecisionForTest(PaymentEligibilityDecision source,
                                                                      String decisionRef,
                                                                      String environment, String subject,
                                                                      String orderRef, String snapshotDigest,
                                                                      long supportedSetVersion, long catalogVersion,
                                                                      long projectionVersion, long aggregateVersion,
                                                                      String authorizationEvidenceVersion,
                                                                      String allowedActionCode, String ruleVersion,
                                                                      PaymentEligibilityDecisionStatus status,
                                                                      Instant validUntil) {
        PaymentEligibilityDecision unsigned = new PaymentEligibilityDecision(decisionRef, source.a1EvidenceRef(),
                source.a1EvidenceVersion(), environment, subject, orderRef, snapshotDigest, supportedSetVersion,
                catalogVersion, projectionVersion, aggregateVersion, authorizationEvidenceVersion,
                allowedActionCode, ruleVersion, status, validUntil);
        return "CANONICAL".equals(decisionRef)
                ? copyDecision(unsigned, canonicalPaymentEligibilityDecisionRef(unsigned)) : unsigned;
    }

    void resetPaymentEligibilityForTest() { paymentEligibilityDecisions.clear(); }

    void resetPaymentIntentResultQueryCallsForTest() {
        paymentIntentResultQueryCalls.clear();
        paymentIntentPostCalls.clear();
    }

    long paymentIntentResultQueryCallCountForTest(String projectSubjectRef) {
        return paymentIntentResultQueryCalls.getOrDefault(projectSubjectRef, 0L);
    }

    long paymentIntentPostCallCountForTest(String projectSubjectRef) {
        return paymentIntentPostCalls.getOrDefault(projectSubjectRef, 0L);
    }

    private static PaymentEligibilityDecision copyDecision(PaymentEligibilityDecision source, String decisionRef) {
        return new PaymentEligibilityDecision(decisionRef, source.a1EvidenceRef(), source.a1EvidenceVersion(),
                source.environment(), source.projectSubjectRef(), source.orderRef(), source.priceSnapshotDigest(),
                source.supportedOperatorSetVersion(), source.catalogVersion(), source.parentProjectionVersion(),
                source.parentAggregateVersion(), source.authorizationEvidenceVersion(), source.allowedActionCode(),
                source.ruleVersion(), source.status(), source.validUntil());
    }

    private void requireCurrentQuote(Quote quote) {
        if (quote.quoteRef() == null || quote.quoteRef().isBlank()
                || quote.operatorCode() == null || quote.operatorCode().isBlank()
                || quote.productCode() == null || quote.productCode().isBlank()
                || quote.denominationRef() == null || quote.denominationRef().isBlank()
                || quote.supportedOperatorSetVersion() < 1 || quote.catalogVersion() < 1
                || quote.totalAmountMinor() < 1 || quote.currency() == null || quote.currency().isBlank()
                || quote.expiresAt() == null || !quote.expiresAt().isAfter(clock.instant())) {
            throw new FlowRejectedException("QUOTE_NOT_ELIGIBLE");
        }
        CatalogSelection current = catalog.requirePreset(quote.supportedOperatorSetVersion(), quote.catalogVersion(),
                quote.operatorCode(), quote.productCode(), quote.denominationRef());
        if (current.amountMinor() != quote.totalAmountMinor() || !current.currency().equals(quote.currency())) {
            throw new FlowRejectedException("QUOTE_SNAPSHOT_MISMATCH");
        }
    }

    private static UserOrderStateCode publicState(OrderProjection order) {
        return switch (order.orderState()) {
            case AWAITING_PAYMENT -> UserOrderStateCode.AWAITING_PAYMENT;
            case PAYMENT_CONFIRMED -> UserOrderStateCode.PAID_AWAITING_TOPUP;
            case TOPUP_REVIEW -> UserOrderStateCode.TOPUP_RESULT_UNKNOWN;
            case COMPLETED -> UserOrderStateCode.DELIVERED;
        };
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
            case CREATE_PAYMENT_INTENT_ACTION -> List.of(new AllowedAction(CREATE_PAYMENT_INTENT_ACTION,
                    order.projectionVersion(), order.aggregateVersion()));
            case QUERY_PAYMENT_INTENT_ACTION -> List.of(new AllowedAction(QUERY_PAYMENT_INTENT_ACTION,
                    order.projectionVersion(), null));
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
