package com.huarenzaimeng.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class LocalSyntheticOrderRecoveryService {
    static final String ENVIRONMENT = "LOCAL_SYNTHETIC";
    static final String CREATION_PRECONDITION = "RECOVERY_CASE_MUST_NOT_EXIST";

    private final Clock clock;
    private final boolean localSyntheticEnabled;
    private final Map<String, SessionState> sessions = new HashMap<>();
    private final Map<String, AuthorizationSetRecord> authorizationSets = new HashMap<>();
    private final Map<String, SyntheticOrderRecord> orders = new HashMap<>();
    private final Map<String, SyntheticRecoveryEvidence> evidence = new HashMap<>();
    private final HashSet<String> faultEvidence = new HashSet<>();
    private final Map<String, RecoveryCaseRecord> cases = new HashMap<>();
    private final Map<String, String> fingerprintToCase = new HashMap<>();
    private final Map<String, String> activeUnknownCaseBySubject = new HashMap<>();
    private final Map<String, RecoveryCommandRecord> commands = new HashMap<>();
    private final Map<String, String> idempotencyToCommand = new HashMap<>();
    private final Map<String, SyntheticRecoveryEvidence> authoritativeResults = new HashMap<>();
    private final Map<String, Long> sessionTransitionCounts = new HashMap<>();

    LocalSyntheticOrderRecoveryService(Clock clock, Environment environment,
            @Value("${hz.persistence.mode:in-memory}") String persistenceMode) {
        this.clock = clock;
        this.localSyntheticEnabled = "in-memory".equals(persistenceMode)
                && environment.acceptsProfiles(Profiles.of("mock", "test"));
    }

    synchronized OrderListResponse listOrders(String environment, String projectSubjectRef, String sessionRef,
                                               Long expectedSessionVersion, String expectedAuthorizationSetRef) {
        requireLocalSynthetic(environment, projectSubjectRef, sessionRef);
        SessionState session = sessions.get(sessionRef);
        if (session == null) throw unavailable();
        if (session.role() != ProjectSessionRole.BUYER || session.authorizationSetRef() == null) {
            throw unavailable();
        }
        if ((expectedSessionVersion == null) != (expectedAuthorizationSetRef == null)) throw unavailable();
        if (expectedSessionVersion != null && (session.version() != expectedSessionVersion
                || !session.authorizationSetRef().equals(expectedAuthorizationSetRef))) throw unavailable();

        AuthorizationSetRecord set = authorizationSets.get(session.authorizationSetRef());
        validateSet(session, set, projectSubjectRef);
        List<UserOrderListItem> projected = new ArrayList<>();
        for (String orderRef : set.authorizedOrderRefs()) {
            SyntheticOrderRecord order = orders.get(orderRef);
            if (order == null || order.orderRef() == null || order.orderRef().isBlank()
                    || !orderRef.equals(order.orderRef()) || !order.synthetic()
                    || !ENVIRONMENT.equals(order.environment())
                    || !projectSubjectRef.equals(order.projectSubjectRef())
                    || !set.authorizationEvidenceVersion().equals(order.authorizationEvidenceVersion())
                    || order.stateCode() == null || order.stateCode().isBlank()
                    || order.projectionVersion() < 1 || order.generatedAt() == null) {
                throw unavailable();
            }
            UserOrderStateCode state;
            try {
                state = UserOrderStateCode.valueOf(order.stateCode());
            } catch (IllegalArgumentException error) {
                throw unavailable();
            }
            projected.add(new UserOrderListItem(order.orderRef(), state, order.projectionVersion(),
                    order.generatedAt()));
        }
        if (projected.size() != set.authorizedOrderRefs().size()) throw unavailable();
        return new OrderListResponse(projectSubjectRef, session.role(), session.version(), set.authorizationSetRef(),
                set.authorizationEvidenceVersion(), List.copyOf(set.authorizedOrderRefs()), set.issuedAt(),
                set.expiresAt(), List.copyOf(projected));
    }

    synchronized BuyerAuthorization requireBuyerAuthorization(String environment, String projectSubjectRef,
                                                               String sessionRef, long expectedSessionVersion,
                                                               String expectedAuthorizationSetRef) {
        requireLocalSynthetic(environment, projectSubjectRef, sessionRef);
        SessionState session = sessions.get(sessionRef);
        if (session == null || session.role() != ProjectSessionRole.BUYER
                || session.version() != expectedSessionVersion
                || !expectedAuthorizationSetRef.equals(session.authorizationSetRef())) {
            throw new FlowRejectedException("ORDER_CREATION_NOT_AVAILABLE");
        }
        AuthorizationSetRecord set = authorizationSets.get(expectedAuthorizationSetRef);
        try {
            validateSet(session, set, projectSubjectRef);
        } catch (FlowRejectedException error) {
            throw new FlowRejectedException("ORDER_CREATION_NOT_AVAILABLE");
        }
        return new BuyerAuthorization(ENVIRONMENT, projectSubjectRef, session.version(),
                set.authorizationSetRef(), set.authorizationEvidenceVersion(),
                List.copyOf(set.authorizedOrderRefs()));
    }

    synchronized BuyerAuthorization requirePaymentIntentBuyerAuthorization(String environment,
                                                                            String projectSubjectRef,
                                                                            String sessionRef,
                                                                            long expectedSessionVersion,
                                                                            String expectedAuthorizationSetRef) {
        try {
            return requireBuyerAuthorization(environment, projectSubjectRef, sessionRef, expectedSessionVersion,
                    expectedAuthorizationSetRef);
        } catch (FlowRejectedException error) {
            throw new FlowRejectedException("PAYMENT_INTENT_NOT_AVAILABLE");
        }
    }

    synchronized BuyerAuthorization requireCurrentOrderDetailAuthorization(String environment,
                                                                            String projectSubjectRef,
                                                                            String sessionRef,
                                                                            String orderRef) {
        requireLocalSynthetic(environment, projectSubjectRef, sessionRef);
        SessionState session = sessions.get(sessionRef);
        if (session == null || session.role() != ProjectSessionRole.BUYER
                || session.authorizationSetRef() == null || orderRef == null || orderRef.isBlank()) {
            throw new FlowRejectedException("ORDER_DETAIL_NOT_AVAILABLE");
        }
        AuthorizationSetRecord set = authorizationSets.get(session.authorizationSetRef());
        try {
            validateSet(session, set, projectSubjectRef);
        } catch (FlowRejectedException error) {
            throw new FlowRejectedException("ORDER_DETAIL_NOT_AVAILABLE");
        }
        if (!set.authorizedOrderRefs().contains(orderRef)) {
            throw new FlowRejectedException("ORDER_DETAIL_NOT_AVAILABLE");
        }
        return new BuyerAuthorization(ENVIRONMENT, projectSubjectRef, session.version(),
                set.authorizationSetRef(), set.authorizationEvidenceVersion(),
                List.copyOf(set.authorizedOrderRefs()));
    }

    synchronized RecoveryCaseResponse createRecoveryCase(String environment, String projectSubjectRef,
                                                          String sessionRef, RecoveryCommand command) {
        requireLocalSynthetic(environment, projectSubjectRef, sessionRef);
        if (!CREATION_PRECONDITION.equals(command.creationPrecondition())) {
            throw new FlowRejectedException("RECOVERY_CREATION_PRECONDITION_INVALID");
        }
        String calculated = recoveryInputFingerprint(command.orderRef(), command.recoveryMaterialRef());
        if (!calculated.equals(command.recoveryInputFingerprint())) {
            throw new FlowRejectedException("RECOVERY_INPUT_FINGERPRINT_MISMATCH");
        }
        RecoveryCommandRecord replay = replay(projectSubjectRef, command);
        if (replay != null) return responseFor(replay.recoveryCaseRef());
        SessionState before = sessions.get(sessionRef);
        if (before != null && before.role() != ProjectSessionRole.GUEST) {
            throw new FlowRejectedException("ORDER_RECOVERY_NOT_ELIGIBLE");
        }
        String activeCaseRef = activeUnknownCaseBySubject.get(projectSubjectRef);
        if (activeCaseRef != null) {
            RecoveryCaseRecord active = cases.get(activeCaseRef);
            if (active != null && active.response().outcome() == RecoveryOutcome.UNKNOWN) {
                if (!active.command().recoveryInputFingerprint().equals(command.recoveryInputFingerprint())) {
                    throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
                }
                record(projectSubjectRef, command, activeCaseRef);
                return active.response();
            }
            activeUnknownCaseBySubject.remove(projectSubjectRef);
        }
        String fingerprintScope = fingerprintScope(projectSubjectRef, command.recoveryInputFingerprint());
        String canonicalCaseRef = fingerprintToCase.get(fingerprintScope);
        if (canonicalCaseRef != null) {
            record(projectSubjectRef, command, canonicalCaseRef);
            return responseFor(canonicalCaseRef);
        }
        if (faultEvidence.contains(command.recoveryMaterialRef())) {
            throw new LocalSyntheticRecoveryUnavailableException();
        }
        if (before == null) {
            before = SessionState.guest(projectSubjectRef);
            sessions.put(sessionRef, before);
        }

        SyntheticRecoveryEvidence found = evidence.get(command.recoveryMaterialRef());
        RecoveryOutcome outcome = found == null ? RecoveryOutcome.REJECTED : found.outcome();
        if (outcome == RecoveryOutcome.RECOVERED && !isRecoverable(found, command.orderRef(), projectSubjectRef)) {
            outcome = RecoveryOutcome.REJECTED;
        }

        String caseRef = "RC-SYN-" + UUID.randomUUID();
        RecoveryCaseResponse response;
        if (outcome == RecoveryOutcome.RECOVERED) {
            response = recover(caseRef, projectSubjectRef, sessionRef, before, found);
        } else if (outcome == RecoveryOutcome.UNKNOWN) {
            response = new RecoveryCaseResponse(caseRef, outcome, "READ_SAFE",
                    "/api/v1/recovery-cases/" + caseRef, null);
        } else {
            response = new RecoveryCaseResponse(caseRef, outcome, "NONE", null, null);
        }
        RecoveryCaseRecord recoveryCase = new RecoveryCaseRecord(caseRef, projectSubjectRef, sessionRef,
                command, response, null);
        cases.put(caseRef, recoveryCase);
        fingerprintToCase.put(fingerprintScope, caseRef);
        if (outcome == RecoveryOutcome.UNKNOWN) {
            activeUnknownCaseBySubject.put(projectSubjectRef, caseRef);
        }
        record(projectSubjectRef, command, caseRef);
        return response;
    }

    synchronized RecoveryCaseResponse convergeRecoveryCase(String environment, String projectSubjectRef,
                                                             String sessionRef, String recoveryCaseRef,
                                                             String authoritativeResultRef) {
        requireLocalSynthetic(environment, projectSubjectRef, sessionRef);
        RecoveryCaseRecord existing = cases.get(recoveryCaseRef);
        if (existing == null || !projectSubjectRef.equals(existing.projectSubjectRef())
                || !sessionRef.equals(existing.sessionRef())) {
            throw new FlowRejectedException("RECOVERY_NOT_CONFIRMED");
        }
        SyntheticRecoveryEvidence result = authoritativeResults.get(authoritativeResultRef);
        if (result == null || result.outcome() == RecoveryOutcome.UNKNOWN) {
            throw new FlowRejectedException("RECOVERY_AUTHORITATIVE_RESULT_INVALID");
        }
        if (existing.response().outcome() != RecoveryOutcome.UNKNOWN) {
            if (authoritativeResultRef.equals(existing.authoritativeResultRef())
                    && result.outcome() == existing.response().outcome()) {
                return existing.response();
            }
            throw new FlowRejectedException("RECOVERY_RESULT_CONFLICT");
        }

        RecoveryCaseResponse converged;
        if (result.outcome() == RecoveryOutcome.RECOVERED) {
            if (!isRecoverable(result, existing.command().orderRef(), projectSubjectRef)) {
                throw new FlowRejectedException("RECOVERY_AUTHORITATIVE_RESULT_INVALID");
            }
            SessionState before = sessions.get(sessionRef);
            if (before != null && before.role() != ProjectSessionRole.GUEST) {
                throw new FlowRejectedException("ORDER_RECOVERY_NOT_ELIGIBLE");
            }
            if (before == null) {
                before = SessionState.guest(projectSubjectRef);
                sessions.put(sessionRef, before);
            }
            converged = recover(recoveryCaseRef, projectSubjectRef, sessionRef, before, result);
        } else {
            converged = new RecoveryCaseResponse(recoveryCaseRef, RecoveryOutcome.REJECTED, "NONE", null, null);
        }
        cases.put(recoveryCaseRef, new RecoveryCaseRecord(recoveryCaseRef, projectSubjectRef, sessionRef,
                existing.command(), converged, authoritativeResultRef));
        activeUnknownCaseBySubject.remove(projectSubjectRef, recoveryCaseRef);
        return converged;
    }

    synchronized RecoveryCaseResponse queryRecoveryCase(String environment, String projectSubjectRef,
                                                         String sessionRef, String recoveryCaseRef) {
        requireLocalSynthetic(environment, projectSubjectRef, sessionRef);
        RecoveryCaseRecord found = cases.get(recoveryCaseRef);
        if (found == null || !projectSubjectRef.equals(found.projectSubjectRef())
                || !sessionRef.equals(found.sessionRef())) {
            throw new FlowRejectedException("RECOVERY_NOT_CONFIRMED");
        }
        return found.response();
    }

    static String recoveryInputFingerprint(String orderRef, String recoveryMaterialRef) {
        return CanonicalFingerprint.sha256(orderRef, recoveryMaterialRef);
    }

    private RecoveryCaseResponse recover(String caseRef, String projectSubjectRef, String sessionRef,
                                         SessionState before, SyntheticRecoveryEvidence found) {
        long nextVersion = before.version() + 1;
        String setRef = "AS-SYN-" + UUID.randomUUID();
        Instant issuedAt = clock.instant();
        AuthorizationSetRecord set = new AuthorizationSetRecord(setRef, projectSubjectRef,
                ProjectSessionRole.BUYER, nextVersion, found.authorizationEvidenceVersion(),
                List.copyOf(found.authorizedOrderRefs()), issuedAt, issuedAt.plus(1, ChronoUnit.HOURS), true);
        SessionState after = new SessionState(projectSubjectRef, ProjectSessionRole.BUYER, nextVersion, setRef);
        authorizationSets.put(setRef, set);
        sessions.put(sessionRef, after);
        sessionTransitionCounts.merge(sessionRef, 1L, Long::sum);
        return new RecoveryCaseResponse(caseRef, RecoveryOutcome.RECOVERED, "NONE", null, envelope(set));
    }

    private boolean isRecoverable(SyntheticRecoveryEvidence found, String locatorOrderRef,
                                  String projectSubjectRef) {
        List<String> refs = found.authorizedOrderRefs();
        if (found.authorizationEvidenceVersion() == null || refs == null || refs.isEmpty()
                || !refs.contains(locatorOrderRef) || new HashSet<>(refs).size() != refs.size()) return false;
        for (String ref : refs) {
            SyntheticOrderRecord order = orders.get(ref);
            if (order == null || !order.synthetic() || !ENVIRONMENT.equals(order.environment())
                    || !projectSubjectRef.equals(order.projectSubjectRef())
                    || !found.authorizationEvidenceVersion().equals(order.authorizationEvidenceVersion())) {
                return false;
            }
            try {
                UserOrderStateCode.valueOf(order.stateCode());
            } catch (IllegalArgumentException error) {
                return false;
            }
        }
        return true;
    }

    private void validateSet(SessionState session, AuthorizationSetRecord set, String projectSubjectRef) {
        if (set == null || !set.active() || !projectSubjectRef.equals(set.projectSubjectRef())
                || set.sessionRole() != ProjectSessionRole.BUYER || set.sessionVersion() != session.version()
                || !set.authorizationSetRef().equals(session.authorizationSetRef())
                || set.authorizationEvidenceVersion() == null || set.authorizationEvidenceVersion().isBlank()
                || set.issuedAt() == null || set.expiresAt() == null || !set.expiresAt().isAfter(clock.instant())
                || set.authorizedOrderRefs() == null
                || new HashSet<>(set.authorizedOrderRefs()).size() != set.authorizedOrderRefs().size()) {
            throw unavailable();
        }
    }

    private RecoveryCommandRecord replay(String projectSubjectRef, RecoveryCommand command) {
        String commandScope = projectSubjectRef + "\u0000" + command.commandId();
        String idempotencyScope = projectSubjectRef + "\u0000POST:/api/v1/recovery-cases\u0000"
                + command.idempotencyKey();
        RecoveryCommandRecord byCommand = commands.get(commandScope);
        String mapped = idempotencyToCommand.get(idempotencyScope);
        if (byCommand == null && mapped == null) return null;
        RecoveryCommandRecord existing = byCommand != null ? byCommand : commands.get(mapped);
        if (existing == null || !existing.command().equals(command)) {
            throw new FlowRejectedException("IDEMPOTENCY_CONFLICT");
        }
        return existing;
    }

    private RecoveryCaseResponse responseFor(String recoveryCaseRef) {
        RecoveryCaseRecord found = cases.get(recoveryCaseRef);
        if (found == null) throw new FlowRejectedException("RECOVERY_NOT_CONFIRMED");
        return found.response();
    }

    private static String fingerprintScope(String projectSubjectRef, String fingerprint) {
        return projectSubjectRef + "\u0000FP:" + fingerprint;
    }

    private void record(String projectSubjectRef, RecoveryCommand command, String recoveryCaseRef) {
        String commandScope = projectSubjectRef + "\u0000" + command.commandId();
        String idempotencyScope = projectSubjectRef + "\u0000POST:/api/v1/recovery-cases\u0000"
                + command.idempotencyKey();
        commands.put(commandScope, new RecoveryCommandRecord(command, recoveryCaseRef));
        idempotencyToCommand.put(idempotencyScope, commandScope);
    }

    private void requireLocalSynthetic(String environment, String projectSubjectRef, String sessionRef) {
        if (!localSyntheticEnabled || !ENVIRONMENT.equals(environment) || projectSubjectRef == null
                || !projectSubjectRef.startsWith("SYN-SUBJECT-") || sessionRef == null
                || !sessionRef.startsWith("SYN-SESSION-")) {
            throw new FlowRejectedException("LOCAL_SYNTHETIC_IDENTITY_REQUIRED");
        }
    }

    private static AuthorizationEnvelope envelope(AuthorizationSetRecord set) {
        return new AuthorizationEnvelope(set.projectSubjectRef(), set.sessionRole(), set.sessionVersion(),
                set.authorizationSetRef(), set.authorizationEvidenceVersion(),
                List.copyOf(set.authorizedOrderRefs()), set.issuedAt(), set.expiresAt());
    }

    private static FlowRejectedException unavailable() {
        return new FlowRejectedException("ORDER_LIST_NOT_AVAILABLE");
    }

    synchronized void resetForTest() {
        sessions.clear();
        authorizationSets.clear();
        orders.clear();
        evidence.clear();
        faultEvidence.clear();
        cases.clear();
        fingerprintToCase.clear();
        activeUnknownCaseBySubject.clear();
        commands.clear();
        idempotencyToCommand.clear();
        authoritativeResults.clear();
        sessionTransitionCounts.clear();
    }

    synchronized void installBuyerAuthorizationForTest(String projectSubjectRef, String sessionRef,
                                                         long sessionVersion, String authorizationSetRef,
                                                         String authorizationEvidenceVersion) {
        installLocalSyntheticBuyerAuthorization(projectSubjectRef, sessionRef, sessionVersion,
                authorizationSetRef, authorizationEvidenceVersion, List.of());
    }

    synchronized void installLocalSyntheticBuyerAuthorization(String projectSubjectRef, String sessionRef,
                                                               long sessionVersion, String authorizationSetRef,
                                                               String authorizationEvidenceVersion,
                                                               List<String> authorizedOrderRefs) {
        Instant issuedAt = clock.instant();
        sessions.put(sessionRef, new SessionState(projectSubjectRef, ProjectSessionRole.BUYER, sessionVersion,
                authorizationSetRef));
        authorizationSets.put(authorizationSetRef, new AuthorizationSetRecord(authorizationSetRef,
                projectSubjectRef, ProjectSessionRole.BUYER, sessionVersion, authorizationEvidenceVersion,
                List.copyOf(authorizedOrderRefs), issuedAt, issuedAt.plus(1, ChronoUnit.HOURS), true));
    }

    synchronized void revokeBuyerAuthorizationForTest(String authorizationSetRef) {
        AuthorizationSetRecord current = authorizationSets.get(authorizationSetRef);
        if (current == null) return;
        authorizationSets.put(authorizationSetRef, new AuthorizationSetRecord(current.authorizationSetRef(),
                current.projectSubjectRef(), current.sessionRole(), current.sessionVersion(),
                current.authorizationEvidenceVersion(), current.authorizedOrderRefs(), current.issuedAt(),
                current.expiresAt(), false));
    }

    synchronized void installOrderForTest(SyntheticOrderRecord order) { orders.put(order.orderRef(), order); }
    synchronized void installEvidenceForTest(String materialRef, SyntheticRecoveryEvidence value) {
        evidence.put(materialRef, value);
    }
    synchronized void installFaultForTest(String materialRef) { faultEvidence.add(materialRef); }
    synchronized void installAuthoritativeResultForTest(String resultRef, SyntheticRecoveryEvidence value) {
        authoritativeResults.put(resultRef, value);
    }
    synchronized long recoveryCaseCountForTest() { return cases.size(); }
    synchronized long sessionCountForTest() { return sessions.size(); }
    synchronized long authorizationSetCountForTest() { return authorizationSets.size(); }
    synchronized long orderCountForTest() { return orders.size(); }
    synchronized long totalSessionTransitionCountForTest() {
        return sessionTransitionCounts.values().stream().mapToLong(Long::longValue).sum();
    }
    synchronized long sessionTransitionCountForTest(String sessionRef) {
        return sessionTransitionCounts.getOrDefault(sessionRef, 0L);
    }
    synchronized void replaceOrderStateForTest(String orderRef, String stateCode) {
        SyntheticOrderRecord current = orders.get(orderRef);
        orders.put(orderRef, new SyntheticOrderRecord(current.orderRef(), current.projectSubjectRef(),
                current.authorizationEvidenceVersion(), current.environment(), stateCode,
                current.projectionVersion(), current.generatedAt(), current.synthetic()));
    }
    synchronized void replaceOrderForTest(String orderRef, SyntheticOrderRecord replacement) {
        orders.put(orderRef, replacement);
    }
    synchronized void appendOrderRefForTest(String authorizationSetRef, String orderRef) {
        AuthorizationSetRecord current = authorizationSets.get(authorizationSetRef);
        List<String> refs = new ArrayList<>(current.authorizedOrderRefs());
        refs.add(orderRef);
        authorizationSets.put(authorizationSetRef, new AuthorizationSetRecord(current.authorizationSetRef(),
                current.projectSubjectRef(), current.sessionRole(), current.sessionVersion(),
                current.authorizationEvidenceVersion(), List.copyOf(refs), current.issuedAt(), current.expiresAt(),
                current.active()));
    }

    record RecoveryCommand(String commandId, String idempotencyKey, String recoveryInputFingerprint,
                           String creationPrecondition, String orderRef, String recoveryMaterialRef) {}
    private record SessionState(String projectSubjectRef, ProjectSessionRole role, long version,
                                String authorizationSetRef) {
        static SessionState guest(String subject) { return new SessionState(subject, ProjectSessionRole.GUEST, 0, null); }
    }
    private record AuthorizationSetRecord(String authorizationSetRef, String projectSubjectRef,
                                          ProjectSessionRole sessionRole, long sessionVersion,
                                          String authorizationEvidenceVersion, List<String> authorizedOrderRefs,
                                          Instant issuedAt, Instant expiresAt, boolean active) {}
    private record RecoveryCaseRecord(String recoveryCaseRef, String projectSubjectRef, String sessionRef,
                                      RecoveryCommand command, RecoveryCaseResponse response,
                                      String authoritativeResultRef) {}
    private record RecoveryCommandRecord(RecoveryCommand command, String recoveryCaseRef) {}
}
