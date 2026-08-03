package com.huarenzaimeng.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.huarenzaimeng.api.P021OrderDetailDomain.*;
import static com.huarenzaimeng.api.P021OrderDetailSideEffectProbe.Counter.QueryCall;

@Service
final class P021OrderDetailService {
    private static final Set<String> USER_ITEM_CODES = Set.of("PAYMENT_CONFIRMATION", "TOPUP_RESULT",
            "DELIVERY_RESULT", "REFUND_RESULT", "ACCOUNTING_REVIEW");
    private static final Set<String> RESPONSIBILITY_CODES = Set.of("NONE", "USER_PAYMENT", "SYSTEM_RECHECK",
            "SUPPORT_REVIEW");
    private static final Set<UserOrderStateCode> SUPPORT_STATES = Set.of(UserOrderStateCode.TOPUP_RESULT_UNKNOWN,
            UserOrderStateCode.CONFIRMED_NOT_DELIVERED, UserOrderStateCode.DELIVERY_REFUND_CONFLICT_REVIEW,
            UserOrderStateCode.SUPPORT_REVIEW);
    private static final Pattern OPAQUE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern ISO_CURRENCY = Pattern.compile("[A-Z]{3}");

    private final LocalSyntheticOrderRecoveryService recovery;
    private final P021OrderDetailSideEffectProbe probe;
    private final String mode;
    private final boolean enabled;
    private final P021Store store;

    @Autowired
    P021OrderDetailService(LocalSyntheticOrderRecoveryService recovery, P021OrderDetailSideEffectProbe probe,
                           P021Store store, Environment environment, @Value("${hz.p021.mode:disabled}") String mode,
                           @Value("${hz.persistence.mode:in-memory}") String persistenceMode) {
        this.recovery = recovery;
        this.probe = probe;
        this.store = store;
        this.mode = mode;
        this.enabled = ("local-synthetic".equals(mode) && "in-memory".equals(persistenceMode)
                && environment.acceptsProfiles(Profiles.of("mock", "test")))
                || ("test-readonly".equals(mode) && ("mysql".equals(persistenceMode) || "in-memory".equals(persistenceMode))
                && !environment.acceptsProfiles(Profiles.of("prod")));
    }

    // Kept for focused non-Spring contract tests; production wiring always supplies the conditional store bean.
    P021OrderDetailService(LocalSyntheticOrderRecoveryService recovery, P021OrderDetailSideEffectProbe probe,
                           Environment environment, String mode, String persistenceMode) {
        this(recovery, probe, new InMemoryP021Store(), environment, mode, persistenceMode);
    }

    synchronized Response read(String environment, String projectSubjectRef, String sessionRef, String orderRef) {
        return read(environment, projectSubjectRef, sessionRef, orderRef, null);
    }

    synchronized Response read(String environment, String projectSubjectRef, String sessionRef, String orderRef,
                               SessionSnapshot trustedSession) {
        probe.observeQuery();
        if (!enabled || !ENVIRONMENT.equals(environment) || !validOpaque(orderRef)) return unavailable();
        Fixture fixture;
        if ("local-synthetic".equals(mode)) {
            BuyerAuthorization authorization;
            try {
                authorization = recovery.requireCurrentOrderDetailAuthorization(environment, projectSubjectRef,
                        sessionRef, orderRef);
            } catch (RuntimeException error) {
                return unavailable();
            }
            fixture = store.findAuthorized(orderRef, projectSubjectRef, sessionRef).orElse(null);
            if (fixture == null || !matchesAuthorization(fixture, authorization, orderRef)) return unavailable();
        } else {
            if (trustedSession == null || !projectSubjectRef.equals(trustedSession.projectSubjectRef())
                    || !sessionRef.equals(trustedSession.sessionRef())) return unavailable();
            fixture = store.findAuthorized(orderRef, trustedSession).orElse(null);
        }
        if (fixture == null) return unavailable();
        try {
            requireFixture(fixture, orderRef);
            Response response = new Response(null, "ACCEPTED", PROJECT_CODE_READ, orderRef,
                    fixture.projection().aggregateVersion(), fixture.projection(), "NONE", null);
            if (!isStrictResponse(response)) throw new ContractViolation();
            return response;
        } catch (ContractViolation error) {
            return readError();
        }
    }

    synchronized Response rejectInvalidInput() {
        probe.observeQuery();
        return unavailable();
    }

    boolean isStrictResponse(Response response) {
        if (response == null || response.requestRef() != null || response.outcome() == null
                || response.projectCode() == null || response.retryClass() == null || response.nextPollAt() != null) {
            return false;
        }
        if (PROJECT_CODE_READ.equals(response.projectCode())) {
            return "ACCEPTED".equals(response.outcome()) && "NONE".equals(response.retryClass())
                    && validOpaque(response.resourceRef()) && response.aggregateVersion() != null
                    && response.aggregateVersion() > 0 && response.currentProjection() != null
                    && response.resourceRef().equals(response.currentProjection().orderRef())
                    && response.aggregateVersion() == response.currentProjection().aggregateVersion()
                    && validProjection(response.currentProjection());
        }
        if (PROJECT_CODE_UNAVAILABLE.equals(response.projectCode())) {
            return "REJECTED".equals(response.outcome()) && "NONE".equals(response.retryClass())
                    && response.resourceRef() == null && response.aggregateVersion() == null
                    && response.currentProjection() == null;
        }
        if (PROJECT_CODE_ERROR.equals(response.projectCode())) {
            return "REJECTED".equals(response.outcome()) && "READ_SAFE".equals(response.retryClass())
                    && response.resourceRef() == null && response.aggregateVersion() == null
                    && response.currentProjection() == null;
        }
        return false;
    }

    boolean isStrictStoredFixture(Fixture fixture, String orderRef) {
        try { requireFixture(fixture, orderRef); return validProjection(fixture.projection()); }
        catch (RuntimeException error) { return false; }
    }

    private static boolean matchesAuthorization(Fixture fixture, BuyerAuthorization authorization, String orderRef) {
        return fixture != null && authorization != null && fixture.authorizedOrderRefs() != null
                && fixture.authorizedOrderRefs().contains(orderRef)
                && authorization.projectSubjectRef().equals(fixture.projectSubjectRef())
                && authorization.sessionVersion() == fixture.sessionVersion()
                && authorization.authorizationSetRef().equals(fixture.authorizationSetRef())
                && authorization.authorizationEvidenceVersion().equals(fixture.authorizationEvidenceVersion())
                && authorization.authorizedOrderRefs().equals(fixture.authorizedOrderRefs());
    }

    private void requireFixture(Fixture fixture, String orderRef) {
        if (!fixture.syntheticMarker() || !ENVIRONMENT.equals(fixture.environment())
                || !REALITY_LEVEL.equals(fixture.realityEvidenceLevel())
                || fixture.sessionRole() != ProjectSessionRole.BUYER || fixture.sessionVersion() < 1
                || blank(fixture.projectSubjectRef(), fixture.authorizationSetRef(),
                        fixture.authorizationEvidenceVersion(), fixture.priceSnapshotDigest(),
                        fixture.fixtureSchemaVersion(), fixture.fixtureDigest())
                || fixture.authorizedOrderRefs() == null
                || new HashSet<>(fixture.authorizedOrderRefs()).size() != fixture.authorizedOrderRefs().size()
                || !fixture.authorizedOrderRefs().contains(orderRef)
                || fixture.projection() == null || !orderRef.equals(fixture.projection().orderRef())
                || !fixture.priceSnapshotDigest().equals(snapshotDigest(fixture.projection().priceSnapshotSummary()))
                || !fixture.fixtureDigest().equals(fixtureDigest(fixture))) {
            throw new ContractViolation();
        }
    }

    private boolean validProjection(Projection projection) {
        if (!validOpaque(projection.orderRef()) || projection.aggregateVersion() < 1
                || projection.projectionVersion() < 1 || projection.stateCode() == null
                || projection.priceSnapshotSummary() == null || projection.confirmedItems() == null
                || projection.unknownItems() == null || blank(projection.responsibilityCode())
                || projection.updatedAt() == null || projection.timeline() == null
                || projection.allowedActions() == null || !RESPONSIBILITY_CODES.contains(projection.responsibilityCode())
                || !validUserItems(projection.confirmedItems(), projection.unknownItems())
                || !validPriceSnapshot(projection.priceSnapshotSummary()) || !validTimeline(projection)
                || !validActions(projection)) return false;
        boolean supportState = SUPPORT_STATES.contains(projection.stateCode());
        return supportState ? validOpaque(projection.supportRef()) : projection.supportRef() == null;
    }

    private static boolean validUserItems(List<String> confirmed, List<String> unknown) {
        if (new HashSet<>(confirmed).size() != confirmed.size() || new HashSet<>(unknown).size() != unknown.size()
                || !USER_ITEM_CODES.containsAll(confirmed) || !USER_ITEM_CODES.containsAll(unknown)) return false;
        Set<String> overlap = new HashSet<>(confirmed); overlap.retainAll(unknown); return overlap.isEmpty();
    }

    private static boolean validPriceSnapshot(PriceSnapshotSummary price) {
        return validOpaque(price.priceSnapshotRef()) && price.totalMinor() >= 0
                && ISO_CURRENCY.matcher(nullSafe(price.currency())).matches()
                && ISO_CURRENCY.matcher(nullSafe(price.targetCurrency())).matches()
                && !blank(price.displayVersion(), price.maskedTarget(), price.brandDisplayName(),
                        price.productDisplayName(), price.targetValueDisplay())
                && safelyMasked(price.maskedTarget())
                && price.validUntil() != null;
    }

    private static boolean safelyMasked(String value) {
        if (value == null || !value.matches("[+*0-9 .()\\-]+") || value.chars().filter(ch -> ch == '*').count() < 4) {
            return false;
        }
        long visibleDigits = value.chars().filter(Character::isDigit).count();
        return visibleDigits > 0 && visibleDigits <= 4;
    }

    private static boolean validTimeline(Projection projection) {
        if (projection.timeline().isEmpty()) return false;
        Set<String> refs = new HashSet<>(); long lastVersion = 0; UserOrderStateCode lastState = null;
        for (int index = 0; index < projection.timeline().size(); index++) {
            TimelineItem item = projection.timeline().get(index);
            if (item == null || !validOpaque(item.timelineItemRef()) || !refs.add(item.timelineItemRef())
                    || item.sequence() != index + 1L || item.projectionVersion() < 1
                    || item.projectionVersion() < lastVersion || item.projectionVersion() > projection.projectionVersion()
                    || item.stateCode() == null || blank(item.userMessageCode())) return false;
            if (item.projectionVersion() == lastVersion && lastState != null && item.stateCode() != lastState) {
                return false;
            }
            lastVersion = item.projectionVersion(); lastState = item.stateCode();
        }
        TimelineItem last = projection.timeline().get(projection.timeline().size() - 1);
        return last.projectionVersion() == projection.projectionVersion()
                && last.stateCode() == projection.stateCode();
    }

    private static boolean validActions(Projection projection) {
        List<String> expected = new ArrayList<>(); expected.add("REFRESH_ORDER_DETAIL");
        boolean openSupport = SUPPORT_STATES.contains(projection.stateCode()) && !blank(projection.supportRef());
        if (openSupport) expected.add("OPEN_SUPPORT"); expected.add("SAFE_BACK");
        if (projection.allowedActions().size() != expected.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            AllowedAction action = projection.allowedActions().get(index);
            if (action == null || !expected.get(index).equals(action.actionCode()) || !action.enabled()
                    || action.actionBindingVersion() != projection.projectionVersion()) return false;
            if ("OPEN_SUPPORT".equals(action.actionCode())) {
                if (!projection.supportRef().equals(action.supportRef())) return false;
            } else if (action.supportRef() != null) return false;
        }
        return true;
    }

    static String snapshotDigest(PriceSnapshotSummary price) {
        if (price == null) return "INVALID";
        return sha256(price.priceSnapshotRef(), Long.toString(price.totalMinor()), price.currency(),
                price.displayVersion(), price.maskedTarget(), price.brandDisplayName(), price.productDisplayName(),
                price.targetValueDisplay(), price.targetCurrency(), String.valueOf(price.validUntil()));
    }

    static String fixtureDigest(Fixture fixture) {
        if (fixture == null || fixture.projection() == null) return "INVALID";
        Projection projection = fixture.projection();
        return sha256(Boolean.toString(fixture.syntheticMarker()), fixture.environment(), fixture.realityEvidenceLevel(),
                fixture.projectSubjectRef(), String.valueOf(fixture.sessionRole()), Long.toString(fixture.sessionVersion()),
                fixture.authorizationSetRef(), fixture.authorizationEvidenceVersion(),
                canonicalStrings(fixture.authorizedOrderRefs()), projection.orderRef(),
                Long.toString(projection.aggregateVersion()), Long.toString(projection.projectionVersion()),
                projection.stateCode().name(), fixture.priceSnapshotDigest(), canonicalStrings(projection.confirmedItems()),
                canonicalStrings(projection.unknownItems()), projection.responsibilityCode(), String.valueOf(projection.updatedAt()),
                String.valueOf(projection.nextReviewPoint()), canonicalTimeline(projection.timeline()),
                canonicalActions(projection.allowedActions()), String.valueOf(projection.supportRef()), fixture.fixtureSchemaVersion());
    }

    private static String canonicalStrings(List<String> values) {
        if (values == null) return "<null>";
        StringBuilder result = new StringBuilder();
        for (String value : values) appendLengthPrefixed(result, value);
        return result.toString();
    }

    private static String canonicalTimeline(List<TimelineItem> values) {
        if (values == null) return "<null>";
        StringBuilder result = new StringBuilder();
        for (TimelineItem value : values) {
            if (value == null) { appendLengthPrefixed(result, null); continue; }
            appendLengthPrefixed(result, value.timelineItemRef());
            appendLengthPrefixed(result, Long.toString(value.sequence()));
            appendLengthPrefixed(result, Long.toString(value.projectionVersion()));
            appendLengthPrefixed(result, String.valueOf(value.stateCode()));
            appendLengthPrefixed(result, String.valueOf(value.occurredAt()));
            appendLengthPrefixed(result, value.userMessageCode());
        }
        return result.toString();
    }

    private static String canonicalActions(List<AllowedAction> values) {
        if (values == null) return "<null>";
        StringBuilder result = new StringBuilder();
        for (AllowedAction value : values) {
            if (value == null) { appendLengthPrefixed(result, null); continue; }
            appendLengthPrefixed(result, value.actionCode());
            appendLengthPrefixed(result, Boolean.toString(value.enabled()));
            appendLengthPrefixed(result, Long.toString(value.actionBindingVersion()));
            appendLengthPrefixed(result, value.supportRef());
        }
        return result.toString();
    }

    private static void appendLengthPrefixed(StringBuilder target, String value) {
        if (value == null) target.append("-1:");
        else target.append(value.length()).append(':').append(value);
        target.append(';');
    }

    private static String sha256(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update(nullSafe(value).getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
            }
            return java.util.HexFormat.of().withUpperCase().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static Response unavailable() { return new Response(null, "REJECTED", PROJECT_CODE_UNAVAILABLE,
            null, null, null, "NONE", null); }
    private static Response readError() { return new Response(null, "REJECTED", PROJECT_CODE_ERROR,
            null, null, null, "READ_SAFE", null); }
    private static boolean validOpaque(String value) { return value != null && OPAQUE_REF.matcher(value).matches(); }
    private static boolean blank(String... values) { for (String value : values) if (value == null || value.isBlank()) return true; return false; }
    private static String nullSafe(String value) { return value == null ? "" : value; }

    synchronized void installLocalSyntheticFixture(Fixture fixture) {
        store.installForTest(fixture, "SYN-SESSION-" + fixture.projectSubjectRef().substring("SYN-SUBJECT-".length()));
    }
    synchronized void resetForTest() { store.clearForTest(); probe.reset(); }
    synchronized Map<String, Long> countsForTest() { return probe.snapshot(); }
    synchronized int fixtureCountForTest() { return store instanceof InMemoryP021Store memory
            ? memory.sizeForTest() : 0; }

    private static final class ContractViolation extends RuntimeException {}
}
