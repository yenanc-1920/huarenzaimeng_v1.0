package com.huarenzaimeng.api;

import java.time.Instant;
import java.util.List;

record DirectoryContent(
        String contentRef,
        String title,
        String summary,
        String category,
        String ownershipMode,
        String sourceCategory,
        String sourceRef,
        String verificationScope,
        String verifiedBy,
        Instant verifiedAt,
        Instant validUntil,
        long version,
        ContentState state,
        boolean complaintPending,
        Instant updatedAt,
        List<ContentAudit> auditTrail) {
    static final String PUBLIC_SOURCE_CATEGORY = "SELF_RESEARCH";
    static final String PUBLIC_VERIFICATION_SCOPE = "NAME_AND_PUBLIC_CONTACT_CHANNELS";

    boolean publicationEligible(Instant now) {
        return "SELF_OPERATED_CHINA_COMPANY".equals(ownershipMode)
                && PUBLIC_SOURCE_CATEGORY.equals(sourceCategory)
                && present(sourceRef) && present(verificationScope) && present(verifiedBy)
                && verifiedAt != null && validUntil != null && validUntil.isAfter(now)
                && ProjectApiVersion.valid(version)
                && !complaintPending && (state == ContentState.VERIFIED || state == ContentState.PUBLISHED);
    }

    PublicContentProjection publicProjection() {
        return new PublicContentProjection(contentRef, title, summary, category, PUBLIC_SOURCE_CATEGORY,
                PUBLIC_VERIFICATION_SCOPE, verifiedAt, validUntil, version, updatedAt,
                "仅说明所列范围已在所列时间完成核验，不构成官方认证、推荐或持续有效担保");
    }

    DirectoryContent withoutAudit() {
        return new DirectoryContent(contentRef, title, summary, category, ownershipMode, sourceCategory,
                sourceRef, verificationScope, verifiedBy, verifiedAt, validUntil, version, state,
                complaintPending, updatedAt, List.of());
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
}

final class ProjectApiVersion {
    static final long MIN = 1L;
    static final long MAX = 9_007_199_254_740_991L;
    private ProjectApiVersion() {}
    static boolean valid(long value) { return value >= MIN && value <= MAX; }
}

enum ContentState { DRAFT, VERIFIED, PUBLISHED, UNPUBLISHED, UNDER_REVIEW }

record ContentAudit(long sequence, String action, String actorRef, String reason, long beforeVersion,
                    long afterVersion, Instant occurredAt, String scope, String authorizationRef,
                    String result, String evidenceRef) {}

record PublicContentProjection(String contentRef, String title, String summary, String category,
                               String sourceCategory, String verificationScope, Instant verifiedAt,
                               Instant validUntil, long version, Instant updatedAt,
                               String disclaimer) {}

record PublicContentSummary(String contentRef, String title, String summary, String category, long contentVersion,
                            Instant validUntil, Instant updatedAt, String qualification) {}

record ContentPage<T>(List<T> items, int count) {}

record ContentCommand(String commandId, String idempotencyKey, long expectedAggregateVersion,
                      String action, String reason, String sourceCategory, String sourceRef,
                      String verificationScope, String verifiedBy, Instant verifiedAt, Instant validUntil,
                      String scope, String authorizationRef, String result, String evidenceRef) {}
