package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
@ConditionalOnProperty(name = "hz.life-content.mode", havingValue = "local-synthetic")
class LifeContentReadService {
    static final String ENVIRONMENT = "LOCAL_SYNTHETIC";
    static final String SCHEMA_VERSION = "LIFE_CONTENT_READ_V1";
    static final String VISIBILITY_RULE_VERSION = "SYN-LIFE-VISIBILITY-V1";
    private static final Set<String> SELECTED_CATEGORIES = Set.of("LIFE_REMINDER", "HOLIDAY_EXPLANATION");
    private static final Set<String> COVER_STATES = Set.of("AVAILABLE", "NOT_CONFIGURED", "IMAGE_UNAVAILABLE");
    private static final Set<String> REVIEW_STATES = Set.of("PUBLISHED", "UNDER_REVIEW", "UNPUBLISHED", "REMOVED");
    private static final Set<String> RIGHTS_STATES = Set.of("VALID", "EXPIRED", "REVOKED", "UNKNOWN", "CONFLICT");
    private static final Set<String> CONFLICT_STATES = Set.of("NONE", "UNKNOWN", "CONFLICT");

    private List<LifeContentFixture> fixtures = List.of();
    private Map<String, String> authoritativeCurrentVersionByContentRef = Map.of();
    private LifeContentListReadCompleteness completeness = LifeContentListReadCompleteness.COMPLETE;
    private long fixtureRevision;
    private long requestSequence;
    private final LifeContentSideEffectProbe sideEffects;

    LifeContentReadService(LifeContentSideEffectProbe sideEffects) { this.sideEffects = sideEffects; }

    synchronized LifeContentListResponse list() {
        sideEffects.observeQuery();
        long call = ++requestSequence;
        String requestRef = requestRef(call);
        if (completeness == LifeContentListReadCompleteness.ERROR) {
            return listResponse(requestRef, LifeContentViewState.ERROR, List.of());
        }
        if (completeness == LifeContentListReadCompleteness.UNKNOWN) {
            return listResponse(requestRef, LifeContentViewState.UNKNOWN, List.of());
        }

        List<QualifiedFixture> qualified = fixtures.stream()
                .filter(this::isAuthoritativeCurrentVersion)
                .map(value -> new QualifiedFixture(value, qualify(value)))
                .toList();
        if (qualified.stream().anyMatch(value -> value.state() == LifeContentViewState.ERROR)) {
            return listResponse(requestRef, LifeContentViewState.ERROR, List.of());
        }
        if (qualified.stream().anyMatch(value -> value.state() == LifeContentViewState.UNKNOWN)) {
            return listResponse(requestRef, LifeContentViewState.UNKNOWN, List.of());
        }
        List<LifeContentSummary> ready = qualified.stream()
                .filter(value -> value.state() == LifeContentViewState.READY)
                .map(value -> summary(value.fixture()))
                .sorted(Comparator.comparing(LifeContentSummary::contentRef)
                        .thenComparing(LifeContentSummary::contentVersion))
                .toList();
        if (!ready.isEmpty()) return listResponse(requestRef, LifeContentViewState.READY, ready);
        for (LifeContentViewState state : List.of(LifeContentViewState.UNDER_REVIEW,
                LifeContentViewState.EXPIRED, LifeContentViewState.REMOVED)) {
            if (qualified.stream().anyMatch(value -> value.state() == state)) {
                return listResponse(requestRef, state, List.of());
            }
        }
        return listResponse(requestRef, LifeContentViewState.EMPTY, List.of());
    }

    synchronized LifeContentDetailResponse detail(String contentRef, String contentVersion) {
        sideEffects.observeQuery();
        long call = ++requestSequence;
        String requestRef = requestRef(call);
        if (!present(contentRef) || !present(contentVersion)) {
            return detailResponse(requestRef, LifeContentViewState.ERROR, safe(contentRef), safe(contentVersion), null);
        }
        if (completeness == LifeContentListReadCompleteness.ERROR) {
            return detailResponse(requestRef, LifeContentViewState.ERROR, contentRef, contentVersion, null);
        }
        if (completeness == LifeContentListReadCompleteness.UNKNOWN) {
            return detailResponse(requestRef, LifeContentViewState.UNKNOWN, contentRef, contentVersion, null);
        }
        LifeContentFixture exact = fixtures.stream()
                .filter(value -> contentRef.equals(value.contentRef()) && contentVersion.equals(value.contentVersion()))
                .findFirst().orElse(null);
        if (exact == null || !isAuthoritativeCurrentVersion(exact)) {
            return detailResponse(requestRef, LifeContentViewState.REMOVED, contentRef, contentVersion, null);
        }
        LifeContentViewState state = qualify(exact);
        LifeContentDetail item = state == LifeContentViewState.READY ? detail(exact) : null;
        return detailResponse(requestRef, state, contentRef, contentVersion, item);
    }

    synchronized LifeContentListResponse invalidListRequest() {
        sideEffects.observeQuery();
        return listResponse(requestRef(++requestSequence), LifeContentViewState.ERROR, List.of());
    }

    synchronized void installFixturesForTest(LifeContentListReadCompleteness value,
                                             List<LifeContentFixture> installed) {
        installFixturesForTest(value, installed, deriveUniqueCurrentVersions(installed));
    }

    synchronized void installFixturesForTest(LifeContentListReadCompleteness value,
                                             List<LifeContentFixture> installed,
                                             Map<String, String> authoritativeCurrentVersions) {
        if (value == null || installed == null || installed.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("explicit synthetic fixture inputs are required");
        }
        validateAuthoritativeCurrentVersions(installed, authoritativeCurrentVersions);
        completeness = value;
        fixtures = List.copyOf(installed);
        authoritativeCurrentVersionByContentRef = Map.copyOf(authoritativeCurrentVersions);
        fixtureRevision++;
        requestSequence = 0;
        sideEffects.resetForTest();
    }

    synchronized LifeContentReadSnapshot snapshotForTest() {
        String authority = new TreeMap<>(authoritativeCurrentVersionByContentRef).entrySet().stream()
                .map(entry -> safe(entry.getKey()) + "\u0000" + safe(entry.getValue()))
                .reduce((left, right) -> left + "\n" + right).orElse("");
        String canonical = completeness + "\nAUTHORITATIVE_CURRENT_VERSIONS\n" + authority + "\nFIXTURES\n"
                + fixtures.stream()
                .sorted(Comparator.comparing(value -> safe(value.contentRef()) + "\u0000" + safe(value.contentVersion())))
                .map(LifeContentReadService::canonicalFixture)
                .reduce((left, right) -> left + "\n" + right).orElse("");
        return new LifeContentReadSnapshot(fixtureRevision, fixtures.size(), sha256(canonical),
                sideEffects.snapshot());
    }

    private LifeContentViewState qualify(LifeContentFixture value) {
        if (!SELECTED_CATEGORIES.contains(value.category())) return LifeContentViewState.ERROR;
        if (!fixtureShapeValid(value)) return LifeContentViewState.ERROR;
        if (!completeness.name().equals(value.listReadCompleteness())) return LifeContentViewState.ERROR;
        if (value.referenceInstant().isAfter(value.effectiveTo())) return LifeContentViewState.EXPIRED;
        Instant freshnessLimit;
        try {
            freshnessLimit = value.updatedAt().plusSeconds(value.freshnessWindowSeconds());
        } catch (RuntimeException error) {
            return LifeContentViewState.ERROR;
        }
        if (value.referenceInstant().isAfter(freshnessLimit)) return LifeContentViewState.UNKNOWN;
        if ("UNDER_REVIEW".equals(value.reviewState())) return LifeContentViewState.UNDER_REVIEW;
        if ("UNPUBLISHED".equals(value.reviewState()) || "REMOVED".equals(value.reviewState())) {
            return LifeContentViewState.REMOVED;
        }
        if (!"PUBLISHED".equals(value.reviewState())
                || value.referenceInstant().isBefore(value.effectiveFrom())
                || !"VALID".equals(value.rightsEvidenceState())
                || !"NONE".equals(value.sourceConflictState())
                || !"NONE".equals(value.rightsConflictState())
                || !VISIBILITY_RULE_VERSION.equals(value.visibilityRuleVersion())) {
            return LifeContentViewState.UNKNOWN;
        }
        return LifeContentViewState.READY;
    }

    private boolean isAuthoritativeCurrentVersion(LifeContentFixture value) {
        return value != null && value.contentVersion() != null
                && value.contentVersion().equals(authoritativeCurrentVersionByContentRef.get(value.contentRef()));
    }

    private static Map<String, String> deriveUniqueCurrentVersions(List<LifeContentFixture> installed) {
        if (installed == null) throw new IllegalArgumentException("explicit synthetic fixture inputs are required");
        Map<String, String> result = new LinkedHashMap<>();
        for (LifeContentFixture fixture : installed) {
            if (fixture == null || !present(fixture.contentRef()) || !present(fixture.contentVersion())) {
                throw new IllegalArgumentException("fixture read keys must be explicit");
            }
            String prior = result.putIfAbsent(fixture.contentRef(), fixture.contentVersion());
            if (prior != null && !prior.equals(fixture.contentVersion())) {
                throw new IllegalArgumentException("multiple versions require an explicit authoritative current version");
            }
        }
        return Map.copyOf(result);
    }

    private static void validateAuthoritativeCurrentVersions(List<LifeContentFixture> installed,
                                                             Map<String, String> authoritativeCurrentVersions) {
        if (authoritativeCurrentVersions == null) {
            throw new IllegalArgumentException("authoritative current versions are required");
        }
        Set<String> fixtureRefs = installed.stream().map(LifeContentFixture::contentRef).collect(java.util.stream.Collectors.toSet());
        if (!fixtureRefs.equals(authoritativeCurrentVersions.keySet())) {
            throw new IllegalArgumentException("every contentRef requires exactly one authoritative current version");
        }
        for (Map.Entry<String, String> entry : authoritativeCurrentVersions.entrySet()) {
            if (!present(entry.getKey()) || !present(entry.getValue())) {
                throw new IllegalArgumentException("authoritative current read keys must be non-empty");
            }
            long matches = installed.stream().filter(value -> entry.getKey().equals(value.contentRef())
                    && entry.getValue().equals(value.contentVersion())).count();
            if (matches != 1) {
                throw new IllegalArgumentException("authoritative current version must identify one installed record");
            }
        }
    }

    private static boolean fixtureShapeValid(LifeContentFixture value) {
        if (!present(value.fixtureId()) || !present(value.fixtureVersion())
                || !ENVIRONMENT.equals(value.environment()) || !value.syntheticMarker()
                || !present(value.contentRef()) || !present(value.contentVersion())
                || !present(value.title()) || !present(value.summary()) || !present(value.body())
                || !COVER_STATES.contains(value.coverState())
                || ("AVAILABLE".equals(value.coverState()) ? !present(value.coverRef()) : value.coverRef() != null)
                || !present(value.sourceRef()) || !present(value.sourceType())
                || !present(value.rightsEvidenceRef()) || !RIGHTS_STATES.contains(value.rightsEvidenceState())
                || !present(value.jurisdiction()) || !present(value.applicableAudience())
                || value.publishedAt() == null || value.updatedAt() == null
                || !present(value.verifiedBy()) || value.verifiedAt() == null
                || !REVIEW_STATES.contains(value.reviewState())
                || value.effectiveFrom() == null || value.effectiveTo() == null
                || value.effectiveTo().isBefore(value.effectiveFrom())
                || !present(value.evidenceVersion()) || !present(value.visibilityRuleVersion())
                || !present(value.freshnessRuleVersion())
                || value.freshnessWindowSeconds() == null || value.freshnessWindowSeconds() <= 0
                || value.referenceInstant() == null
                || !CONFLICT_STATES.contains(value.sourceConflictState())
                || !CONFLICT_STATES.contains(value.rightsConflictState())
                || !Set.of("COMPLETE", "UNKNOWN", "ERROR").contains(value.listReadCompleteness())
                || !"NOT_VERIFIED".equals(value.realityEvidenceLevel())
                || value.productionPublicationEligibility() == null
                || value.productionPublicationEligibility() != 0) {
            return false;
        }
        return true;
    }

    private static LifeContentSummary summary(LifeContentFixture value) {
        return new LifeContentSummary(value.contentRef(), value.contentVersion(), value.category(), value.title(),
                value.summary(), value.sourceType(), value.jurisdiction(), value.applicableAudience(),
                value.publishedAt(), value.updatedAt(), value.effectiveFrom(), value.effectiveTo(), "CURRENT",
                value.coverState(), value.coverRef());
    }

    private static LifeContentDetail detail(LifeContentFixture value) {
        return new LifeContentDetail(value.contentRef(), value.contentVersion(), value.category(), value.title(),
                value.summary(), value.sourceType(), value.jurisdiction(), value.applicableAudience(),
                value.publishedAt(), value.updatedAt(), value.effectiveFrom(), value.effectiveTo(), "CURRENT",
                value.coverState(), value.coverRef(), value.body());
    }

    private static LifeContentListResponse listResponse(String requestRef, LifeContentViewState state,
                                                        List<LifeContentSummary> items) {
        return new LifeContentListResponse(requestRef, state.name(), "LIFE_CONTENT_LIST_" + state.name(),
                SCHEMA_VERSION, VISIBILITY_RULE_VERSION, List.copyOf(items), retryClass(state), null);
    }

    private static LifeContentDetailResponse detailResponse(String requestRef, LifeContentViewState state,
                                                            String contentRef, String contentVersion,
                                                            LifeContentDetail item) {
        if (state == LifeContentViewState.EMPTY) throw new IllegalArgumentException("detail cannot be EMPTY");
        return new LifeContentDetailResponse(requestRef, state.name(), "LIFE_CONTENT_DETAIL_" + state.name(),
                SCHEMA_VERSION, VISIBILITY_RULE_VERSION, contentRef, contentVersion, item,
                retryClass(state), null);
    }

    private static String retryClass(LifeContentViewState state) {
        return state == LifeContentViewState.UNKNOWN || state == LifeContentViewState.ERROR
                ? "USER_INITIATED_READ_ONLY" : "NONE";
    }

    private static String requestRef(long call) { return "SYN-LIFE-READ-" + call; }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String safe(String value) { return value == null ? "INVALID" : value; }

    private static String canonicalFixture(LifeContentFixture value) {
        List<String> fields = new ArrayList<>();
        fields.add(value.fixtureId()); fields.add(value.fixtureVersion()); fields.add(value.environment());
        fields.add(String.valueOf(value.syntheticMarker())); fields.add(value.contentRef());
        fields.add(value.contentVersion()); fields.add(value.category()); fields.add(value.title());
        fields.add(value.summary()); fields.add(value.body()); fields.add(value.coverState());
        fields.add(value.coverRef()); fields.add(value.sourceRef()); fields.add(value.sourceType());
        fields.add(value.rightsEvidenceRef()); fields.add(value.rightsEvidenceState());
        fields.add(value.jurisdiction()); fields.add(value.applicableAudience());
        fields.add(String.valueOf(value.publishedAt())); fields.add(String.valueOf(value.updatedAt()));
        fields.add(value.verifiedBy()); fields.add(String.valueOf(value.verifiedAt())); fields.add(value.reviewState());
        fields.add(String.valueOf(value.effectiveFrom())); fields.add(String.valueOf(value.effectiveTo()));
        fields.add(value.evidenceVersion()); fields.add(value.visibilityRuleVersion());
        fields.add(value.freshnessRuleVersion()); fields.add(String.valueOf(value.freshnessWindowSeconds()));
        fields.add(String.valueOf(value.referenceInstant())); fields.add(value.sourceConflictState());
        fields.add(value.rightsConflictState()); fields.add(value.listReadCompleteness());
        fields.add(value.realityEvidenceLevel());
        fields.add(String.valueOf(value.productionPublicationEligibility()));
        return String.join("\u0000", fields.stream().map(LifeContentReadService::safe).toList());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private record QualifiedFixture(LifeContentFixture fixture, LifeContentViewState state) {}
}
