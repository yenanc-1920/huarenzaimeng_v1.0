package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile({"mock", "test"})
@ConditionalOnProperty(name = "hz.a110.mode", havingValue = "local-synthetic")
class A110ReconciliationService {
    static final String SCHEMA_VERSION = "A110_RECONCILIATION_READ_V1";
    static final String READ_SCOPE = "A110_RECONCILIATION_READ";
    private static final Set<String> ROLES = Set.of("FIN", "CS", "CONTENT");
    private static final Set<String> AUTH_STATES = Set.of("AUTHORIZED", "DENIED", "UNKNOWN", "REVOKED");
    private static final Set<String> VIEW_STATES = Set.of("READY", "EMPTY", "READ_ERROR", "UNAVAILABLE",
            "ACCESS_DENIED", "AUTHORITY_UNKNOWN", "REVOKED", "VERSION_CONFLICT", "LONG_RUNNING_UNKNOWN",
            "ASYMMETRIC_FACTS", "REFUND_DELIVERY_CONFLICT");
    private static final Set<String> DATA_STATES = Set.of("READY", "LONG_RUNNING_UNKNOWN", "ASYMMETRIC_FACTS",
            "REFUND_DELIVERY_CONFLICT");
    private static final Set<String> FACT_CODES = Set.of("W", "U", "D", "R", "L");
    private static final Set<String> FACT_STATES = Set.of("ABSENT_CONFIRMED", "PENDING_OR_INFLIGHT", "UNKNOWN",
            "CONFIRMED", "CONFLICT");
    private static final Set<String> DIFFERENCES = Set.of("MISSING", "DUPLICATE", "AMOUNT_MISMATCH",
            "CURRENCY_MISMATCH", "FACT_CONFLICT", "ACCOUNTING_INCOMPLETE", "REFUND_DELIVERY_CONFLICT", "UNKNOWN");
    private static final Set<String> AGE_STATES = Set.of("CURRENT", "LONG_RUNNING", "UNKNOWN");
    private static final List<String> FULL_ACTIONS = List.of("READ_REFRESH", "NAVIGATE_A100", "NAVIGATE_A140");
    private static final List<String> REFRESH_ONLY = List.of("READ_REFRESH");

    private final A110ReconciliationSideEffectProbe sideEffects;
    private final AtomicLong requestSequence = new AtomicLong();
    private A110ReconciliationFixture fixture;
    private long fixtureRevision;

    A110ReconciliationService(A110ReconciliationSideEffectProbe sideEffects) { this.sideEffects = sideEffects; }

    synchronized A110ReconciliationResponse read(String environment, String accountSubjectRef) {
        sideEffects.observeQuery();
        return project(environment, accountSubjectRef, fixture);
    }

    synchronized A110ReconciliationResponse invalidRequest() {
        sideEffects.observeQuery();
        return denied();
    }

    synchronized void installFixtureForTest(A110ReconciliationFixture installed) {
        fixture = installed;
        fixtureRevision++;
        requestSequence.set(0L);
        sideEffects.resetForTest();
    }

    synchronized A110ReconciliationSnapshot snapshotForTest() {
        return new A110ReconciliationSnapshot(fixtureRevision, sideEffects.snapshot());
    }

    private A110ReconciliationResponse project(String trustedEnvironment, String trustedSubject,
                                                A110ReconciliationFixture value) {
        if (value == null || !Boolean.TRUE.equals(value.syntheticMarker())
                || !"LOCAL_SYNTHETIC".equals(trustedEnvironment)
                || !"LOCAL_SYNTHETIC".equals(value.environment())
                || !present(trustedSubject) || !trustedSubject.equals(value.accountSubjectRef())
                || !ROLES.contains(value.role()) || !READ_SCOPE.equals(value.scope())
                || !AUTH_STATES.contains(value.authorizationState())) {
            return denied();
        }
        if ("CONTENT".equals(value.role()) || "DENIED".equals(value.authorizationState())) return denied();
        if ("UNKNOWN".equals(value.authorizationState())) return closed("AUTHORITY_UNKNOWN");
        if ("REVOKED".equals(value.authorizationState())) return closed("REVOKED");
        if (!present(value.roleBindingVersion()) || !present(value.authorizationDecisionVersion())
                || value.projectionVersion() == null || value.projectionVersion() <= 0
                || !VIEW_STATES.contains(value.readOutcome()) || value.items() == null) {
            return closed("AUTHORITY_UNKNOWN");
        }
        String state = value.readOutcome();
        if (Set.of("ACCESS_DENIED", "AUTHORITY_UNKNOWN", "REVOKED").contains(state)) return closed(state);
        if (!itemsValid(value.role(), state, value.projectionVersion(), value.items())) return closed("READ_ERROR");
        List<?> items = stateAllowsItems(state) ? List.copyOf(value.items()) : List.of();
        List<String> actions = actionsFor(state);
        String retry = Set.of("READ_ERROR", "UNAVAILABLE", "VERSION_CONFLICT").contains(state)
                ? "USER_INITIATED_READ_ONLY" : "NONE";
        return response(state, value.role(), value.roleBindingVersion(), value.authorizationDecisionVersion(),
                value.projectionVersion(), items, actions, retry);
    }

    private boolean itemsValid(String role, String state, Long projectionVersion, List<?> items) {
        if (!stateAllowsItems(state)) return items.isEmpty();
        if ("READY".equals(state) && items.isEmpty()) return false;
        Set<String> refs = new HashSet<>();
        for (Object item : items) {
            String ref;
            if ("FIN".equals(role) && item instanceof A110FinItem fin
                    && projectionVersion.equals(fin.projectionVersion()) && finValid(fin)) ref = fin.reconciliationRef();
            else if ("CS".equals(role) && item instanceof A110CsItem cs
                    && projectionVersion.equals(cs.projectionVersion()) && csValid(cs)) ref = cs.reconciliationRef();
            else return false;
            if (!refs.add(ref)) return false;
        }
        return !"FIN".equals(role) || finStateMatches(state, items);
    }

    private static boolean finStateMatches(String state, List<?> items) {
        if (items.stream().map(A110FinItem.class::cast)
                .anyMatch(item -> isRefundDeliveryConflict(item)
                        != item.differenceCategories().contains("REFUND_DELIVERY_CONFLICT"))) return false;
        boolean refundDeliveryConflict = items.stream().map(A110FinItem.class::cast)
                .anyMatch(A110ReconciliationService::isRefundDeliveryConflict);
        boolean asymmetric = items.stream().map(A110FinItem.class::cast)
                .anyMatch(A110ReconciliationService::isAsymmetric);
        boolean longRunningUnknown = items.stream().map(A110FinItem.class::cast)
                .anyMatch(item -> "LONG_RUNNING".equals(item.ageState()) && item.factSummaries().values().stream()
                        .anyMatch(fact -> Set.of("UNKNOWN", "PENDING_OR_INFLIGHT").contains(fact.factState())));
        String derived = refundDeliveryConflict ? "REFUND_DELIVERY_CONFLICT"
                : asymmetric ? "ASYMMETRIC_FACTS"
                : longRunningUnknown ? "LONG_RUNNING_UNKNOWN" : "READY";
        return state.equals(derived);
    }

    private static boolean isRefundDeliveryConflict(A110FinItem item) {
        A110FactSummary refund = item.factSummaries().get("R");
        A110FactSummary delivery = item.factSummaries().get("D");
        return refund != null && delivery != null
                && "CONFIRMED".equals(refund.factState()) && "CONFIRMED".equals(delivery.factState())
                && refund.occurredAt() != null && delivery.occurredAt() != null
                && delivery.occurredAt().isAfter(refund.occurredAt());
    }

    private static boolean isAsymmetric(A110FinItem item) {
        if (!item.differenceCategories().isEmpty()) return true;
        boolean payment = confirmed(item.factSummaries().get("W"));
        boolean upstream = confirmed(item.factSummaries().get("U"));
        boolean delivery = confirmed(item.factSummaries().get("D"));
        return payment != upstream || upstream != delivery;
    }

    private static boolean confirmed(A110FactSummary fact) {
        return fact != null && "CONFIRMED".equals(fact.factState());
    }

    private static boolean finValid(A110FinItem item) {
        if (!syntheticRef(item.reconciliationRef()) || !syntheticRef(item.orderRef())
                || !syntheticRefOrNull(item.supportRef()) || item.factSummaries() == null
                || !item.factSummaries().keySet().equals(FACT_CODES) || item.differenceCategories() == null
                || item.differenceCategories().stream().anyMatch(value -> !DIFFERENCES.contains(value))
                || !AGE_STATES.contains(item.ageState()) || !present(item.responsibilityCode())
                || item.timeline() == null || item.updatedAt() == null
                || item.projectionVersion() == null || item.projectionVersion() <= 0 || !present(item.displayVersion())) {
            return false;
        }
        for (Map.Entry<String, A110FactSummary> entry : item.factSummaries().entrySet()) {
            A110FactSummary fact = entry.getValue();
            if (fact == null || !FACT_STATES.contains(fact.factState())
                    || (fact.currency() != null && !fact.currency().matches("[A-Z]{3}"))
                    || (!Set.of("CONFIRMED", "CONFLICT").contains(fact.factState()) && fact.amountMinor() != null)
                    || (fact.amountMinor() != null && fact.currency() == null)) return false;
        }
        return item.timeline().stream().allMatch(entry -> entry != null && FACT_CODES.contains(entry.factCode())
                && FACT_STATES.contains(entry.factState()));
    }

    private static boolean csValid(A110CsItem item) {
        return syntheticRef(item.reconciliationRef()) && syntheticRef(item.orderRef())
                && syntheticRef(item.supportRef()) && present(item.maskedSubjectSummary())
                && present(item.userFacingSummary()) && safeCodes(item.confirmedItems())
                && safeCodes(item.unconfirmedItems()) && present(item.responsibilityCode())
                && item.updatedAt() != null
                && item.projectionVersion() != null && item.projectionVersion() > 0;
    }

    private static boolean safeCodes(List<String> values) {
        return values != null && values.stream().allMatch(value -> present(value) && value.matches("[A-Z0-9_]+"));
    }

    private static boolean stateAllowsItems(String state) { return DATA_STATES.contains(state); }

    private static List<String> actionsFor(String state) {
        if (Set.of("READY", "EMPTY", "LONG_RUNNING_UNKNOWN", "ASYMMETRIC_FACTS",
                "REFUND_DELIVERY_CONFLICT").contains(state)) return FULL_ACTIONS;
        if (Set.of("READ_ERROR", "UNAVAILABLE", "VERSION_CONFLICT").contains(state)) return REFRESH_ONLY;
        return List.of();
    }

    private A110ReconciliationResponse denied() { return closed("ACCESS_DENIED"); }

    private A110ReconciliationResponse closed(String state) {
        return response(state, null, null, null, null, List.of(), actionsFor(state),
                Set.of("READ_ERROR", "UNAVAILABLE", "VERSION_CONFLICT").contains(state)
                        ? "USER_INITIATED_READ_ONLY" : "NONE");
    }

    private A110ReconciliationResponse response(String state, String role, String roleVersion,
                                                String decisionVersion, Long projectionVersion, List<?> items,
                                                List<String> actions, String retryClass) {
        return new A110ReconciliationResponse("SYN-A110-" + requestSequence.incrementAndGet(), state,
                "A110_" + state, SCHEMA_VERSION, role, roleVersion, decisionVersion, projectionVersion,
                items, actions, retryClass);
    }

    private static boolean syntheticRef(String value) { return present(value) && value.startsWith("SYN-"); }
    private static boolean syntheticRefOrNull(String value) { return value == null || syntheticRef(value); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
