package com.huarenzaimeng.api;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static com.huarenzaimeng.api.P014TopupDomain.*;
import static com.huarenzaimeng.api.P014TopupSideEffectProbe.Counter.*;

@Service
@Profile({"mock", "test"})
@ConditionalOnProperty(name="hz.p014.mode",havingValue="local-synthetic")
final class P014TopupService {
    private final P014TopupSideEffectProbe probe;
    private final Map<String, Fixture> fixtures = new HashMap<>();
    private final Map<String, AuthorizationState> authorizations = new HashMap<>();
    private final Map<String, CanonicalRecord> byBusinessKey = new HashMap<>();
    private final Map<String, CanonicalRecord> byCommand = new HashMap<>();
    private final Map<String, CanonicalRecord> byIdempotency = new HashMap<>();
    private final Set<String> reachableSupportRefs = new HashSet<>();
    private final Map<String,String> supportRefOwners = new HashMap<>();
    private final Set<String> resultReviewSignals = new HashSet<>();
    private static final Map<String, ResponseRule> RESPONSE_RULES = Map.ofEntries(
            rule("TOPUP_NOT_AVAILABLE", "REJECTED", "NONE", PayloadMode.NONE, RequestRefMode.ANY, false),
            rule("IDEMPOTENCY_CONFLICT", "REJECTED", "NONE", PayloadMode.NONE, RequestRefMode.REQUIRED, false),
            rule("VERSION_CONFLICT", "REJECTED", "READ_SAFE", PayloadMode.NONE, RequestRefMode.REQUIRED, false),
            rule("TOPUP_QUALIFICATION_UNKNOWN", "REJECTED", "READ_SAFE", PayloadMode.SAFE_PROJECTION, RequestRefMode.REQUIRED, false),
            rule("TOPUP_INTENT_CREATE_UNKNOWN", "UNKNOWN", "SAME_ACTION_QUERY_ONLY", PayloadMode.NONE, RequestRefMode.REQUIRED, true),
            rule("TOPUP_INTENT_CREATED", "ACCEPTED", "NONE", PayloadMode.RESOURCE_PROJECTION, RequestRefMode.REQUIRED, false),
            rule("TOPUP_INTENT_REPLAYED", "ACCEPTED", "NONE", PayloadMode.RESOURCE_PROJECTION, RequestRefMode.REQUIRED, false),
            rule("TOPUP_INTENT_RESULT_FOUND", "ACCEPTED", "NONE", PayloadMode.RESOURCE_PROJECTION, RequestRefMode.REQUIRED, false),
            rule("TOPUP_INTENT_RESULT_REJECTED", "REJECTED", "NONE", PayloadMode.NONE, RequestRefMode.REQUIRED, false),
            rule("TOPUP_INTENT_RESULT_UNKNOWN", "UNKNOWN", "SAME_ACTION_QUERY_ONLY", PayloadMode.NONE, RequestRefMode.REQUIRED, true),
            rule("TOPUP_INTENT_QUERY_NOT_AVAILABLE", "REJECTED", "NONE", PayloadMode.NONE, RequestRefMode.ANY, false),
            rule("TOPUP_PROGRESS_NOT_AVAILABLE", "REJECTED", "NONE", PayloadMode.NONE, RequestRefMode.NULL, false),
            rule("TOPUP_PROGRESS_READ", "ACCEPTED", "NONE", PayloadMode.PROJECTION, RequestRefMode.NULL, false));

    P014TopupService(P014TopupSideEffectProbe probe) { this.probe = probe; }

    synchronized Response create(String environment, String subject, String sessionRef, String orderRef,
                                 CreateRequest request) {
        Fixture current = authorize(environment, subject, sessionRef, orderRef, request.sessionVersion(),
                request.authorizationSetRef()); // Authorization always precedes canonical lookup.
        if (current == null) return unavailable(request.commandId(), "TOPUP_NOT_AVAILABLE");

        String commandKey = subject + "|" + request.commandId();
        String idempotencyKey = subject + "|POST_TOPUP|" + request.idempotencyKey();
        CanonicalRecord command = byCommand.get(commandKey), idempotency = byIdempotency.get(idempotencyKey);
        if (command != null || idempotency != null) {
            if (command == null || idempotency == null || command != idempotency
                    || !command.matchesOriginalRequest(request, subject, orderRef)) {
                return unavailable(request.commandId(), "IDEMPOTENCY_CONFLICT");
            }
            if (!command.originalBindingsIntact()) return unavailable(request.commandId(), "IDEMPOTENCY_CONFLICT");
            return found(command, current, request.commandId(), "TOPUP_INTENT_REPLAYED");
        }
        String businessKey = businessKey(environment, subject, orderRef);
        if (byBusinessKey.containsKey(businessKey)) return unavailable(request.commandId(), "IDEMPOTENCY_CONFLICT");
        if (request.expectedProjectionVersion() != current.projectionVersion()
                || request.expectedAggregateVersion() != current.aggregateVersion()) {
            return versionConflict(request.commandId());
        }
        if (!firstQualificationKnown(current)) {
            return qualificationUnknown(request.commandId(), current);
        }
        if (!firstQualificationEligible(current)) return unavailable(request.commandId(), "TOPUP_NOT_AVAILABLE");
        if (current.createResultState() == ResultState.REJECTED) {
            return unavailable(request.commandId(), "TOPUP_NOT_AVAILABLE");
        }

        String fingerprint = fingerprint(environment, subject, request, orderRef, current);
        String qualificationBinding = qualificationBinding(current);
        String topupSemantic = hash(businessKey + "|CREATE_TOPUP_COORDINATION|LOCAL_SYNTHETIC_W_AND_MNP_ELIGIBLE");
        String dispatchSemantic = hash(topupSemantic + "|CREATE_DISPATCH_INTENT|LOCAL_SYNTHETIC_W_AND_MNP_ELIGIBLE");
        String topupRef = "topup_" + hash(businessKey).substring(0, 20);
        String dispatchRef = "dispatch_" + hash(dispatchSemantic).substring(0, 20);
        String supportRef = current.supportRef();
        Fixture original = copyWith(current, current.projectionVersion() + 1, current.aggregateVersion() + 1,
                supportRef, current.createResultState());
        CanonicalRecord record = new CanonicalRecord(businessKey, topupSemantic, dispatchSemantic, fingerprint,
                qualificationBinding, subject, orderRef, request, topupRef, dispatchRef, original,
                current.createResultState());
        byBusinessKey.put(businessKey, record); byCommand.put(commandKey, record); byIdempotency.put(idempotencyKey, record);

        if (current.createResultState() == ResultState.UNKNOWN) {
            return new Response(request.commandId(), "UNKNOWN", "TOPUP_INTENT_CREATE_UNKNOWN", null,
                    null, null, "SAME_ACTION_QUERY_ONLY", current.nextPollAt());
        }
        commitCanonical(record);
        return found(record, original, request.commandId(), "TOPUP_INTENT_CREATED");
    }

    synchronized Response result(String environment, String subject, String sessionRef, String orderRef,
                                 String commandId, String idempotencyKey, Long sessionVersion,
                                 String authorizationSetRef, boolean invalidShape) {
        probe.increment(QueryCall);
        if (invalidShape || commandId == null || idempotencyKey == null || sessionVersion == null
                || authorizationSetRef == null) return unavailable(commandId, "TOPUP_INTENT_QUERY_NOT_AVAILABLE");
        Fixture current = authorize(environment, subject, sessionRef, orderRef, sessionVersion, authorizationSetRef);
        if (current == null) return unavailable(commandId, "TOPUP_INTENT_QUERY_NOT_AVAILABLE");
        CanonicalRecord command = byCommand.get(subject + "|" + commandId);
        CanonicalRecord idempotency = byIdempotency.get(subject + "|POST_TOPUP|" + idempotencyKey);
        if (command == null || command != idempotency || !command.orderRef.equals(orderRef)
                || !command.originalBindingsIntact()) {
            return unavailable(commandId, "TOPUP_INTENT_QUERY_NOT_AVAILABLE");
        }
        return switch (command.resultState) {
            case FOUND -> found(command, current, command.originalRequest.commandId(), "TOPUP_INTENT_RESULT_FOUND");
            case REJECTED -> new Response(command.originalRequest.commandId(), "REJECTED",
                    "TOPUP_INTENT_RESULT_REJECTED", null, null, null, "NONE", null);
            case UNKNOWN -> new Response(command.originalRequest.commandId(), "UNKNOWN",
                    "TOPUP_INTENT_RESULT_UNKNOWN", null, null, null, "SAME_ACTION_QUERY_ONLY", command.nextPollAt);
        };
    }

    synchronized Response projection(String environment, String subject, String sessionRef, String orderRef) {
        probe.increment(QueryCall);
        Fixture current = authorizeCurrent(environment, subject, sessionRef, orderRef);
        if (current == null) return unavailable(null, "TOPUP_PROGRESS_NOT_AVAILABLE");
        CanonicalRecord record = byBusinessKey.get(businessKey(environment, subject, orderRef));
        try {
            Projection projection = project(current, record);
            return new Response(null, "ACCEPTED", "TOPUP_PROGRESS_READ",
                    record == null ? null : record.topupRef, projection.aggregateVersion(), projection, "NONE", null);
        } catch (ContractViolation error) {
            return unavailable(null, "TOPUP_PROGRESS_NOT_AVAILABLE");
        }
    }

    private Fixture authorize(String environment, String subject, String sessionRef, String orderRef,
                              long sessionVersion, String authorizationSetRef) {
        Fixture fixture = fixtures.get(orderRef);
        AuthorizationState authorization = authorizations.get(sessionRef);
        if (fixture == null || authorization == null || !ENVIRONMENT.equals(environment)
                || !ENVIRONMENT.equals(fixture.environment()) || !REALITY_LEVEL.equals(fixture.realityEvidenceLevel())
                || !"BUYER".equals(fixture.sessionRole()) || !Objects.equals(subject, fixture.projectSubjectRef())
                || !Objects.equals(subject, authorization.projectSubjectRef) || !Objects.equals(sessionRef, fixture.sessionRef())
                || authorization.revoked || authorization.sessionVersion != sessionVersion
                || !Objects.equals(authorization.authorizationSetRef, authorizationSetRef)
                || authorization.sessionVersion != fixture.sessionVersion()
                || !Objects.equals(authorization.authorizationSetRef, fixture.authorizationSetRef())
                || !Objects.equals(authorization.authorizationEvidenceVersion, fixture.authorizationEvidenceVersion())
                || !authorization.authorizedOrderRefs.equals(new HashSet<>(fixture.authorizedOrderRefs()))
                || !authorization.authorizedOrderRefs.contains(orderRef) || !fixture.authorizedOrderRefs().contains(orderRef)
                || !ORDER_STATE.equals(fixture.orderState()) || !validPriceSnapshot(fixture)) return null;
        return fixture;
    }

    private Fixture authorizeCurrent(String environment, String subject, String sessionRef, String orderRef) {
        AuthorizationState state = authorizations.get(sessionRef);
        return state == null ? null : authorize(environment, subject, sessionRef, orderRef, state.sessionVersion,
                state.authorizationSetRef);
    }

    private static boolean firstQualificationKnown(Fixture f) {
        return !Set.of("UNKNOWN", "UNAVAILABLE", "CONFLICT").contains(f.mnpState())
                && !Set.of("UNKNOWN", "CONFLICT", "NOT_OBSERVED").contains(f.paymentState())
                && f.priceSnapshotDigest() != null && f.paymentDecisionRef() != null && f.paymentDecisionVersion() != null
                && f.mnpDecisionRef() != null && f.mnpDecisionVersion() != null && f.catalogVersion() != null
                && f.supportedOperatorSetVersion() != null;
    }
    private static boolean firstQualificationEligible(Fixture f) {
        return "CONFIRMED".equals(f.paymentState()) && "ELIGIBLE".equals(f.mnpState())
                && f.catalogCurrent() && f.supportSetCurrent() && f.allowed();
    }

    private Response qualificationUnknown(String requestRef, Fixture current) {
        try {
            Projection safe = project(current, null);
            return new Response(requestRef, "REJECTED", "TOPUP_QUALIFICATION_UNKNOWN", null,
                    safe.aggregateVersion(), safe, "READ_SAFE", null);
        } catch (ContractViolation error) {
            return unavailable(requestRef, "TOPUP_NOT_AVAILABLE");
        }
    }
    private static Response versionConflict(String requestRef) {
        return new Response(requestRef, "REJECTED", "VERSION_CONFLICT", null, null, null, "READ_SAFE", null);
    }
    private static Response unavailable(String requestRef, String projectCode) {
        return new Response(requestRef, "REJECTED", projectCode, null, null, null, "NONE", null);
    }

    private Response found(CanonicalRecord record, Fixture current, String requestRef, String projectCode) {
        try {
            Projection projection = project(current, record);
            if (projection.projectionVersion() < record.createdProjectionVersion
                    || projection.aggregateVersion() < record.createdAggregateVersion) throw new ContractViolation();
            return new Response(requestRef, "ACCEPTED", projectCode, record.topupRef,
                    projection.aggregateVersion(), projection, "NONE", null);
        } catch (ContractViolation error) {
            return unavailable(requestRef, projectCode.equals("TOPUP_INTENT_RESULT_FOUND")
                    ? "TOPUP_INTENT_QUERY_NOT_AVAILABLE" : "TOPUP_NOT_AVAILABLE");
        }
    }

    private Projection project(Fixture current, CanonicalRecord record) {
        Fixture original = record == null ? current : record.original;
        if (!validPriceSnapshot(original) || current.projectionVersion() < 1 || current.aggregateVersion() < 1)
            throw new ContractViolation();
        Decision d = decide(current, record);
        String topupRef = record == null ? null : record.topupRef;
        String dispatchRef = record == null ? null : record.dispatchRef;
        if ((topupRef == null) != (dispatchRef == null)) throw new ContractViolation();
        List<Fact> facts = List.of(
                fact("PAYMENT", current.paymentState(), current.now()),
                fact("UPSTREAM_DEBIT", current.upstreamDebitState(), current.now()),
                fact("DELIVERY", current.deliveryState(), current.now()),
                fact("ACCOUNTING_CLOSURE", current.accountingClosureState(), current.now()));
        List<String> confirmed = facts.stream().filter(f -> "CONFIRMED".equals(f.state())
                || "ABSENT_CONFIRMED".equals(f.state())).map(Fact::factCode).toList();
        List<String> unknown = facts.stream().filter(f -> "UNKNOWN".equals(f.state())
                || "NOT_OBSERVED".equals(f.state())).map(Fact::factCode).toList();
        String supportRef = switch (d) {
            case P0, P1A, P1B -> null;
            case P5 -> record == null ? null : record.supportRef;
            default -> current.supportRef();
        };
        if (d.requireSupport && (supportRef == null || supportRef.isBlank() || !reachableSupportRefs.contains(supportRef)
                || !Objects.equals(supportRefOwners.get(supportRef), current.orderRef())))
            throw new ContractViolation();
        List<AllowedAction> actions = d.actions.stream().map(action -> new AllowedAction(action, true,
                current.projectionVersion(), actionBinding(action, current, original, d == Decision.P5 ? supportRef : null))).toList();
        Projection projection = new Projection(current.orderRef(), d.stateCode, SCHEMA, current.projectionVersion(),
                current.aggregateVersion(), original.priceSnapshot(), topupRef, dispatchRef, current.paymentState(),
                current.upstreamDebitState(), current.deliveryState(), current.accountingClosureState(),
                new ProgressSummary(d.messageCode, confirmed, unknown, d.responsibility, supportRef,
                        current.now(), d.nextReview ? current.now().plusSeconds(300) : null), facts, actions);
        validateProjection(projection, record, original, d);
        return projection;
    }

    private static Decision decide(Fixture f, CanonicalRecord record) {
        if (record == null) {
            if ("CONFIRMED".equals(f.paymentState()) && firstQualificationEligible(f)) return Decision.P0;
            if ("CONFIRMED".equals(f.paymentState())) return Decision.P1A;
            return Decision.P1B;
        }
        if (f.duplicateCanonicalFactConflict() || "CONFLICT".equals(f.upstreamDebitState())
                || "CONFLICT".equals(f.deliveryState()) || "CONFLICT".equals(f.accountingClosureState())) return Decision.P2;
        if ("CONFIRMED".equals(f.deliveryState()) && (!"CONFIRMED".equals(f.upstreamDebitState())
                || !"CONFIRMED".equals(f.accountingClosureState()))) return Decision.P3;
        if ("CONFIRMED".equals(f.upstreamDebitState()) && "CONFIRMED".equals(f.deliveryState())
                && "CONFIRMED".equals(f.accountingClosureState())) return Decision.P4;
        if (f.unknownAgeDecision() == UnknownAgeDecision.LONG_RUNNING
                || f.unknownAgeDecision() == UnknownAgeDecision.UNKNOWN) return Decision.P5;
        if ("CONFIRMED".equals(f.upstreamDebitState()) && "ABSENT_CONFIRMED".equals(f.deliveryState())) return Decision.P6;
        return Decision.P7;
    }

    private static Fact fact(String code, String state, Instant now) { return new Fact(code, state, now, now); }
    private static String actionBinding(String action, Fixture current, Fixture original, String supportRef) {
        return hash(String.join("|", action, Long.toString(current.projectionVersion()), current.orderRef(),
                current.authorizationSetRef(), current.authorizationEvidenceVersion(), original.paymentDecisionVersion(),
                original.mnpDecisionVersion(), supportRef == null ? "NO_SUPPORT_BINDING" : supportRef)).substring(0, 24);
    }

    private static void validateProjection(Projection p, CanonicalRecord record, Fixture original, Decision d) {
        if (!p.orderRef().equals(original.orderRef()) || !p.priceSnapshotSummary().equals(original.priceSnapshot())
                || !Set.of("PAID_AWAITING_TOPUP", "TOPUP_PROCESSING", "TOPUP_RESULT_UNKNOWN",
                "CONFIRMED_NOT_DELIVERED", "DELIVERED", "SUPPORT_REVIEW").contains(p.stateCode())
                || p.factTimeline().size() != 4 || p.factTimeline().stream().map(Fact::factCode).distinct().count() != 4
                || p.allowedActions().size() != d.actions.size()
                || p.allowedActions().stream().anyMatch(a -> !a.enabled()
                || a.expectedProjectionVersion() != p.projectionVersion() || a.actionBindingVersion().isBlank())) {
            throw new ContractViolation();
        }
        if (record != null && (!record.topupRef.equals(p.topupIntentRef())
                || !record.dispatchRef.equals(p.dispatchIntentRef()))) throw new ContractViolation();
        if (d == Decision.P5 && (!p.allowedActions().stream().map(AllowedAction::actionCode).toList()
                .equals(List.of("QUERY_ORIGINAL_TOPUP", "OPEN_SUPPORT", "SAFE_LEAVE"))
                || p.progressSummary().supportRef() == null)) throw new ContractViolation();
    }

    private static boolean validPriceSnapshot(Fixture f) {
        PriceSnapshotSummary p = f.priceSnapshot();
        return p != null && p.priceSnapshotRef() != null && !p.priceSnapshotRef().isBlank()
                && p.totalMinor() >= 0 && p.targetFaceValueMinor() >= 0 && iso(p.currency()) && iso(p.targetCurrency())
                && nonBlank(p.displayVersion(), p.maskedRecipientNumber(), p.operatorDisplayName(), p.productDisplayName())
                && p.maskedRecipientNumber().contains("*") && p.expiresAt() != null
                && snapshotDigest(p).equals(f.priceSnapshotDigest());
    }
    private static boolean iso(String value) { return value != null && value.matches("[A-Z]{3}"); }
    private static boolean nonBlank(String... values) { return Arrays.stream(values).allMatch(v -> v != null && !v.isBlank()); }

    private static String fingerprint(String env, String subject, CreateRequest r, String orderRef, Fixture f) {
        return hash(String.join("|", env, subject, "BUYER", Long.toString(r.sessionVersion()), r.authorizationSetRef(),
                f.authorizationEvidenceVersion(), orderRef, r.topupCreationPrecondition(),
                Long.toString(r.expectedProjectionVersion()), Long.toString(r.expectedAggregateVersion()),
                f.priceSnapshotDigest(), f.paymentDecisionRef(), f.mnpDecisionRef(), CREATE_ACTION));
    }
    private static String qualificationBinding(Fixture f) {
        return hash(String.join("|", f.paymentDecisionRef(), f.paymentDecisionVersion(), f.mnpDecisionRef(),
                f.mnpDecisionVersion(), f.catalogVersion(), f.supportedOperatorSetVersion(), f.priceSnapshotDigest()));
    }
    private static String businessKey(String env, String subject, String orderRef) { return env + "|" + subject + "|" + orderRef; }
    static String snapshotDigest(PriceSnapshotSummary p) {
        return hash(String.join("|", p.priceSnapshotRef(), Long.toString(p.totalMinor()), p.currency(), p.displayVersion(),
                p.maskedRecipientNumber(), p.operatorDisplayName(), p.productDisplayName(),
                Long.toString(p.targetFaceValueMinor()), p.targetCurrency(), p.expiresAt().toString()));
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    synchronized void installFixtureForTest(Fixture fixture) {
        fixtures.put(fixture.orderRef(), fixture);
        if (fixture.supportRef() != null && !fixture.supportRef().isBlank()) {
            reachableSupportRefs.add(fixture.supportRef());
            supportRefOwners.merge(fixture.supportRef(), fixture.orderRef(), (oldOwner,newOwner)->oldOwner.equals(newOwner)?oldOwner:"AMBIGUOUS");
        }
        authorizations.put(fixture.sessionRef(), new AuthorizationState(fixture.projectSubjectRef(), fixture.sessionVersion(),
                fixture.authorizationSetRef(), fixture.authorizationEvidenceVersion(),
                new HashSet<>(fixture.authorizedOrderRefs()), false));
    }
    synchronized void replaceMutableFixtureForTest(Fixture fixture) { fixtures.put(fixture.orderRef(), fixture); }
    synchronized void applyObservationForTest(String orderRef, String upstream, String delivery, String accounting,
                                              UnknownAgeDecision age, boolean duplicateConflict) {
        Fixture f = fixtures.get(orderRef);
        Fixture next = new Fixture(f.environment(), f.realityEvidenceLevel(), f.orderRef(), f.projectSubjectRef(),
                f.sessionRef(), f.sessionRole(), f.sessionVersion(), f.authorizationSetRef(), f.authorizationEvidenceVersion(),
                f.authorizedOrderRefs(), f.orderState(), f.projectionVersion(), f.aggregateVersion(), f.priceSnapshot(),
                f.priceSnapshotDigest(), f.paymentDecisionRef(), f.paymentDecisionVersion(), f.paymentState(),
                f.mnpDecisionRef(), f.mnpDecisionVersion(), f.mnpState(), f.catalogVersion(), f.supportedOperatorSetVersion(),
                f.catalogCurrent(), f.supportSetCurrent(), f.allowed(), merge(f.upstreamDebitState(), upstream, false),
                merge(f.deliveryState(), delivery, true), merge(f.accountingClosureState(), accounting, false), age,
                f.supportRef(), f.duplicateCanonicalFactConflict() || duplicateConflict, f.createResultState(),
                f.nextPollAt(), f.now());
        if (!next.equals(f)) {
            fixtures.put(orderRef, next);
            probe.increment(SyntheticObservation);
        }
    }
    synchronized void revokeForTest(String sessionRef) { AuthorizationState a = authorizations.get(sessionRef); if (a != null) a.revoked = true; }
    synchronized void alterAuthorizationEvidenceForTest(String sessionRef, String evidenceVersion) {
        AuthorizationState old = authorizations.get(sessionRef);
        if (old != null) authorizations.put(sessionRef, new AuthorizationState(old.projectSubjectRef, old.sessionVersion,
                old.authorizationSetRef, evidenceVersion, old.authorizedOrderRefs, old.revoked));
    }
    synchronized void alterAuthorizationForTest(String sessionRef, long sessionVersion, String setRef,
                                                 String evidenceVersion, Set<String> authorizedOrders) {
        AuthorizationState old = authorizations.get(sessionRef);
        if (old != null) authorizations.put(sessionRef, new AuthorizationState(old.projectSubjectRef, sessionVersion,
                setRef, evidenceVersion, new HashSet<>(authorizedOrders), old.revoked));
    }
    synchronized void convergeResultForTest(String orderRef, ResultState result) {
        CanonicalRecord record = byBusinessKey.values().stream().filter(r -> r.orderRef.equals(orderRef)).findFirst().orElseThrow();
        if (record.resultState == ResultState.UNKNOWN) {
            if (result == ResultState.UNKNOWN) return;
            if (result == ResultState.FOUND) commitCanonical(record);
            else record.submissionState = SubmissionState.NOT_COMMITTED;
            record.resultState = result;
        }
        else if (record.resultState != result) {
            record.resultConflict = true;
            resultReviewSignals.add(hash(record.businessKey+"|RESULT_CONFLICT").substring(0,24));
        }
    }
    private void commitCanonical(CanonicalRecord record) {
        if (record.submissionState == SubmissionState.COMMITTED) return;
        if (record.submissionState == SubmissionState.NOT_COMMITTED) throw new ContractViolation();
        fixtures.put(record.orderRef, record.original);
        probe.incrementAll(List.of(Command, CommandAlias, TopupBusinessKey, TopupSemanticAction, TopupIntent,
                DispatchSemanticAction, DispatchIntent, OrderVersion, ProjectionVersion));
        record.submissionState = SubmissionState.COMMITTED;
    }
    synchronized void resetForTest() { fixtures.clear(); authorizations.clear(); byBusinessKey.clear(); byCommand.clear(); byIdempotency.clear(); reachableSupportRefs.clear(); supportRefOwners.clear(); resultReviewSignals.clear(); probe.reset(); }
    Map<String, Long> countsForTest() { return probe.snapshot(); }
    int resultReviewSignalCountForTest() { return resultReviewSignals.size(); }
    synchronized int canonicalMapEntryCountForTest() { return byBusinessKey.size() + byCommand.size() + byIdempotency.size(); }
    synchronized String submissionStateForTest(String orderRef) {
        return byBusinessKey.values().stream().filter(record -> record.orderRef.equals(orderRef)).findFirst()
                .map(record -> record.submissionState.name()).orElse("ABSENT");
    }
    Set<String> responseProjectCodesForTest() { return RESPONSE_RULES.keySet(); }
    boolean hasNoForbiddenSideEffectDeltaForTest(Map<String, Long> before, Map<String, Long> after) {
        return probe.hasNoForbiddenDelta(before, after);
    }
    Response strictOrFail(Response response, String fallbackCode) {
        return isStrictResponseForTest(response) ? response : unavailable(response == null ? null : response.requestRef(), fallbackCode);
    }
    boolean isStrictResponseForTest(Response r) {
        if (r == null || r.projectCode() == null) return false;
        ResponseRule rule = RESPONSE_RULES.get(r.projectCode());
        if (rule == null || !rule.outcome.equals(r.outcome()) || !rule.retryClass.equals(r.retryClass())
                || (!rule.nextPollAllowed && r.nextPollAt() != null)
                || rule.requestRefMode == RequestRefMode.REQUIRED && (r.requestRef() == null || r.requestRef().isBlank())
                || rule.requestRefMode == RequestRefMode.NULL && r.requestRef() != null) return false;
        boolean payloadValid = switch (rule.payloadMode) {
            case NONE -> r.resourceRef() == null && r.aggregateVersion() == null && r.currentProjection() == null;
            case SAFE_PROJECTION -> r.resourceRef() == null && r.aggregateVersion() != null && r.currentProjection() != null
                    && r.currentProjection().topupIntentRef() == null && r.currentProjection().dispatchIntentRef() == null;
            case RESOURCE_PROJECTION -> r.resourceRef() != null && r.aggregateVersion() != null && r.currentProjection() != null
                    && r.resourceRef().equals(r.currentProjection().topupIntentRef());
            case PROJECTION -> r.aggregateVersion() != null && r.currentProjection() != null
                    && Objects.equals(r.resourceRef(), r.currentProjection().topupIntentRef());
        };
        if (!payloadValid) return false;
        if (r.currentProjection() == null) return true;
        Projection p = r.currentProjection();
        if (p.orderRef() == null || !SCHEMA.equals(p.schemaVersion()) || p.projectionVersion() < 1 || p.aggregateVersion() < 1
                || r.aggregateVersion() == null || r.aggregateVersion() != p.aggregateVersion()
                || p.progressSummary() == null || p.factTimeline() == null || p.allowedActions() == null
                || !Set.of("PAID_AWAITING_TOPUP","TOPUP_PROCESSING","TOPUP_RESULT_UNKNOWN","CONFIRMED_NOT_DELIVERED","DELIVERED","SUPPORT_REVIEW").contains(p.stateCode())
                || !Set.of("NOT_OBSERVED","UNKNOWN","CONFIRMED","CONFLICT").contains(p.paymentConfirmationState())
                || !Set.of("NOT_OBSERVED","UNKNOWN","CONFIRMED","CONFLICT").contains(p.upstreamDebitState())
                || !Set.of("NOT_OBSERVED","UNKNOWN","CONFIRMED","CONFLICT").contains(p.accountingClosureState())
                || !Set.of("NOT_OBSERVED","UNKNOWN","CONFIRMED","ABSENT_CONFIRMED","CONFLICT").contains(p.deliveryState())
                || !validPriceSnapshotForProjection(p.priceSnapshotSummary())
                || p.factTimeline().size() != 4 || p.factTimeline().stream().map(Fact::factCode).distinct().count() != 4
                || p.factTimeline().stream().anyMatch(f -> !Set.of("PAYMENT", "UPSTREAM_DEBIT", "DELIVERY", "ACCOUNTING_CLOSURE").contains(f.factCode())
                || f.state() == null || f.occurredAt() == null || f.observedAt() == null)
                || p.allowedActions().stream().anyMatch(a -> a == null || !a.enabled() || a.actionCode() == null
                || a.actionBindingVersion() == null || a.actionBindingVersion().isBlank()
                || a.expectedProjectionVersion() != p.projectionVersion()
                || !Set.of("CREATE_LOCAL_SYNTHETIC_TOPUP","QUERY_ORIGINAL_TOPUP","REFRESH_ORDER_PROJECTION","OPEN_SUPPORT","SAFE_LEAVE").contains(a.actionCode()))
                || p.allowedActions().stream().map(AllowedAction::actionCode).distinct().count()!=p.allowedActions().size()
                || p.allowedActions().stream().map(AllowedAction::actionBindingVersion).distinct().count()!=p.allowedActions().size()
                || p.progressSummary().userMessageCode()==null || p.progressSummary().responsibilityCode()==null
                || !Set.of("SYSTEM_RECHECK","SUPPORT_REVIEW","ACCOUNTING_REVIEW","NONE").contains(p.progressSummary().responsibilityCode())
                || p.progressSummary().confirmedItems()==null || p.progressSummary().unknownItems()==null
                || p.progressSummary().confirmedItems().stream().anyMatch(p.progressSummary().unknownItems()::contains)) return false;
        if (r.resourceRef() != null && !r.resourceRef().equals(p.topupIntentRef())) return false;
        return (p.topupIntentRef() == null) == (p.dispatchIntentRef() == null);
    }

    private static Map.Entry<String, ResponseRule> rule(String projectCode, String outcome, String retryClass,
                                                        PayloadMode payloadMode, RequestRefMode requestRefMode,
                                                        boolean nextPollAllowed) {
        return Map.entry(projectCode, new ResponseRule(outcome, retryClass, payloadMode, requestRefMode, nextPollAllowed));
    }

    private static boolean validPriceSnapshotForProjection(PriceSnapshotSummary p) {
        return p != null && nonBlank(p.priceSnapshotRef(), p.displayVersion(), p.maskedRecipientNumber(),
                p.operatorDisplayName(), p.productDisplayName()) && p.totalMinor() >= 0 && p.targetFaceValueMinor() >= 0
                && iso(p.currency()) && iso(p.targetCurrency()) && p.maskedRecipientNumber().contains("*") && p.expiresAt() != null;
    }

    private static Fixture copyWith(Fixture f, long projectionVersion, long aggregateVersion, String supportRef,
                                    ResultState resultState) {
        return new Fixture(f.environment(), f.realityEvidenceLevel(), f.orderRef(), f.projectSubjectRef(), f.sessionRef(),
                f.sessionRole(), f.sessionVersion(), f.authorizationSetRef(), f.authorizationEvidenceVersion(),
                List.copyOf(f.authorizedOrderRefs()), f.orderState(), projectionVersion, aggregateVersion,
                f.priceSnapshot(), f.priceSnapshotDigest(), f.paymentDecisionRef(), f.paymentDecisionVersion(),
                f.paymentState(), f.mnpDecisionRef(), f.mnpDecisionVersion(), f.mnpState(), f.catalogVersion(),
                f.supportedOperatorSetVersion(), f.catalogCurrent(), f.supportSetCurrent(), f.allowed(),
                f.upstreamDebitState(), f.deliveryState(), f.accountingClosureState(), f.unknownAgeDecision(), supportRef,
                f.duplicateCanonicalFactConflict(), resultState, f.nextPollAt(), f.now());
    }

    private static String merge(String oldState, String nextState, boolean delivery) {
        if (nextState == null || nextState.equals(oldState) || "UNKNOWN".equals(nextState)
                || "NOT_OBSERVED".equals(nextState)) return oldState;
        if ("UNKNOWN".equals(oldState) || "NOT_OBSERVED".equals(oldState)) return nextState;
        if (delivery && Set.of("CONFIRMED", "ABSENT_CONFIRMED").contains(oldState)
                && Set.of("CONFIRMED", "ABSENT_CONFIRMED").contains(nextState) && !oldState.equals(nextState)) return "CONFLICT";
        return oldState.equals(nextState) ? oldState : "CONFLICT";
    }

    private enum Decision {
        P0("PAID_AWAITING_TOPUP", "PAYMENT_CONFIRMED_READY_FOR_TOPUP", "NONE", false, false,
                List.of("CREATE_LOCAL_SYNTHETIC_TOPUP", "REFRESH_ORDER_PROJECTION", "OPEN_SUPPORT", "SAFE_LEAVE")),
        P1A("PAID_AWAITING_TOPUP", "PAYMENT_CONFIRMED_TOPUP_QUALIFICATION_CHECKING", "SYSTEM_RECHECK", false, false,
                List.of("REFRESH_ORDER_PROJECTION", "OPEN_SUPPORT", "SAFE_LEAVE")),
        P1B("PAID_AWAITING_TOPUP", "PAYMENT_CONFIRMATION_CHECKING_NO_AUTO_TOPUP", "SYSTEM_RECHECK", false, false,
                List.of("REFRESH_ORDER_PROJECTION", "OPEN_SUPPORT", "SAFE_LEAVE")),
        P2("SUPPORT_REVIEW", "TOPUP_FACT_CONFLICT_UNDER_REVIEW", "SUPPORT_REVIEW", false, true,
                List.of("QUERY_ORIGINAL_TOPUP", "OPEN_SUPPORT", "SAFE_LEAVE")),
        P3("SUPPORT_REVIEW", "DELIVERY_EVIDENCE_UNDER_REVIEW", "ACCOUNTING_REVIEW", false, true,
                List.of("QUERY_ORIGINAL_TOPUP", "OPEN_SUPPORT", "SAFE_LEAVE")),
        P4("DELIVERED", "TOPUP_DELIVERED", "NONE", false, false,
                List.of("REFRESH_ORDER_PROJECTION", "OPEN_SUPPORT", "SAFE_LEAVE")),
        P5("TOPUP_RESULT_UNKNOWN", "TOPUP_RESULT_PENDING_CONFIRMATION", "SUPPORT_REVIEW", true, true,
                List.of("QUERY_ORIGINAL_TOPUP", "OPEN_SUPPORT", "SAFE_LEAVE")),
        P6("CONFIRMED_NOT_DELIVERED", "UPSTREAM_CONFIRMED_DELIVERY_ABSENT", "SUPPORT_REVIEW", false, true,
                List.of("QUERY_ORIGINAL_TOPUP", "REFRESH_ORDER_PROJECTION", "OPEN_SUPPORT", "SAFE_LEAVE")),
        P7("TOPUP_PROCESSING", "TOPUP_PROCESSING_DELIVERY_UNCONFIRMED", "SYSTEM_RECHECK", false, true,
                List.of("QUERY_ORIGINAL_TOPUP", "REFRESH_ORDER_PROJECTION", "OPEN_SUPPORT", "SAFE_LEAVE"));
        final String stateCode, messageCode, responsibility; final boolean requireSupport, nextReview; final List<String> actions;
        Decision(String stateCode, String messageCode, String responsibility, boolean requireSupport,
                 boolean nextReview, List<String> actions) { this.stateCode=stateCode; this.messageCode=messageCode;
            this.responsibility=responsibility; this.requireSupport=requireSupport; this.nextReview=nextReview; this.actions=actions; }
    }

    private static final class AuthorizationState {
        final String projectSubjectRef; final long sessionVersion; final String authorizationSetRef;
        final String authorizationEvidenceVersion; final Set<String> authorizedOrderRefs; boolean revoked;
        AuthorizationState(String subject, long version, String setRef, String evidence, Set<String> orders, boolean revoked) {
            this.projectSubjectRef=subject; this.sessionVersion=version; this.authorizationSetRef=setRef;
            this.authorizationEvidenceVersion=evidence; this.authorizedOrderRefs=orders; this.revoked=revoked; }
    }
    private enum PayloadMode { NONE, SAFE_PROJECTION, RESOURCE_PROJECTION, PROJECTION }
    private enum RequestRefMode { ANY, REQUIRED, NULL }
    private record ResponseRule(String outcome, String retryClass, PayloadMode payloadMode,
                                RequestRefMode requestRefMode, boolean nextPollAllowed) {}
    private enum SubmissionState { UNKNOWN, COMMITTED, NOT_COMMITTED }
    private static final class CanonicalRecord {
        final String businessKey, topupSemanticKey, dispatchSemanticKey, fingerprint, qualificationBinding;
        final String subject, orderRef, topupRef, dispatchRef, supportRef; final CreateRequest originalRequest;
        final Fixture original; final long createdProjectionVersion, createdAggregateVersion; final Instant nextPollAt;
        ResultState resultState; SubmissionState submissionState = SubmissionState.UNKNOWN; boolean resultConflict;
        CanonicalRecord(String businessKey, String topupSemanticKey, String dispatchSemanticKey, String fingerprint,
                        String qualificationBinding, String subject, String orderRef, CreateRequest request,
                        String topupRef, String dispatchRef, Fixture original, ResultState state) {
            this.businessKey=businessKey; this.topupSemanticKey=topupSemanticKey; this.dispatchSemanticKey=dispatchSemanticKey;
            this.fingerprint=fingerprint; this.qualificationBinding=qualificationBinding; this.subject=subject;
            this.orderRef=orderRef; this.originalRequest=request; this.topupRef=topupRef; this.dispatchRef=dispatchRef;
            this.original=original; this.supportRef=original.supportRef(); this.resultState=state;
            this.createdProjectionVersion=original.projectionVersion(); this.createdAggregateVersion=original.aggregateVersion();
            this.nextPollAt=original.nextPollAt();
        }
        boolean matchesOriginalRequest(CreateRequest request, String subject, String orderRef) {
            return this.subject.equals(subject) && this.orderRef.equals(orderRef) && originalRequest.equals(request);
        }
        boolean originalBindingsIntact() {
            if (!nonBlank(businessKey, topupSemanticKey, dispatchSemanticKey, fingerprint, qualificationBinding,
                    topupRef, dispatchRef, original.paymentDecisionRef(), original.paymentDecisionVersion(),
                    original.mnpDecisionRef(), original.mnpDecisionVersion(), original.catalogVersion(),
                    original.supportedOperatorSetVersion(), original.priceSnapshotDigest())) return false;
            String expectedBusiness=P014TopupService.businessKey(original.environment(),subject,orderRef);
            String expectedTopup=hash(expectedBusiness+"|CREATE_TOPUP_COORDINATION|LOCAL_SYNTHETIC_W_AND_MNP_ELIGIBLE");
            String expectedDispatch=hash(expectedTopup+"|CREATE_DISPATCH_INTENT|LOCAL_SYNTHETIC_W_AND_MNP_ELIGIBLE");
            return businessKey.equals(expectedBusiness) && topupSemanticKey.equals(expectedTopup)
                    && dispatchSemanticKey.equals(expectedDispatch)
                    && fingerprint.equals(P014TopupService.fingerprint(original.environment(),subject,originalRequest,orderRef,original))
                    && qualificationBinding.equals(P014TopupService.qualificationBinding(original));
        }
    }
    private static final class ContractViolation extends RuntimeException {}
}
