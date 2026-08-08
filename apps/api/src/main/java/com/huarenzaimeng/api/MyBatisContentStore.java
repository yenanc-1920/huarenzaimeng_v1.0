package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "mysql")
class MyBatisContentStore implements ContentStore {
    private final FlowMapper mapper;
    private final TransactionTemplate transactions;
    private final Clock clock;

    MyBatisContentStore(FlowMapper mapper, TransactionTemplate transactions, Clock clock) {
        this.mapper = mapper;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override public List<DirectoryContent> listPublic() {
        Instant now = clock.instant();
        return mapper.selectPublicContent().stream().map(row -> content(row, false))
                .filter(value -> value.state() == ContentState.PUBLISHED && value.publicationEligible(now))
                .map(DirectoryContent::withoutAudit).toList();
    }

    @Override public DirectoryContent requirePublic(String contentRef, long contentVersion) {
        DirectoryContent value = requireInternal(contentRef);
        if (value.version() != contentVersion) throw new ContentRejectedException("CONTENT_VERSION_STALE");
        if (value.state() == ContentState.UNDER_REVIEW || value.complaintPending()) {
            throw new ContentRejectedException("CONTENT_UNDER_REVIEW");
        }
        if (value.state() == ContentState.UNPUBLISHED) throw new ContentRejectedException("CONTENT_REMOVED");
        if (value.state() != ContentState.PUBLISHED || !value.publicationEligible(clock.instant())) {
            throw new ContentRejectedException("CONTENT_QUALIFICATION_UNKNOWN");
        }
        return value.withoutAudit();
    }

    @Override public List<DirectoryContent> listInternal() {
        return mapper.selectAllContent().stream().map(row -> content(row, true)).toList();
    }

    @Override public DirectoryContent requireInternal(String contentRef) {
        Map<String, Object> row = mapper.selectContent(contentRef);
        if (row == null) throw new ContentRejectedException("RESOURCE_NOT_CONFIRMABLE");
        return content(row, true);
    }

    @Override public DirectoryContent createForTest(DirectoryContent content) {
        if (!"SELF_OPERATED_CHINA_COMPANY".equals(content.ownershipMode())) {
            throw new ContentRejectedException("CONTENT_SCOPE_NOT_ALLOWED");
        }
        mapper.insertContent(content.contentRef(), content.title(), content.summary(), content.category(),
                Timestamp.from(clock.instant()));
        return requireInternal(content.contentRef());
    }

    @Override public DirectoryContent report(String contentRef, String actorRef, String commandId,
                                             String idempotencyKey, long contentVersion,
                                             long expectedAggregateVersion, String reason) {
        return mutate(contentRef, actorRef, commandId, idempotencyKey,
                CanonicalFingerprint.sha256("REPORT", contentRef, String.valueOf(contentVersion),
                        String.valueOf(expectedAggregateVersion), reason),
                expectedAggregateVersion, current -> {
                    if (current.version() != contentVersion || current.state() != ContentState.PUBLISHED
                            || !current.publicationEligible(clock.instant())) {
                        throw new ContentRejectedException("CONTENT_VERSION_STALE");
                    }
                    return new Mutation(ContentState.UNDER_REVIEW, true, current.sourceCategory(),
                        current.sourceRef(), current.verificationScope(), current.verifiedBy(), current.verifiedAt(),
                        current.validUntil(), "CONTENT_ERROR_REPORTED", reason, "PUBLIC_CONTENT_REPORT",
                        "AUTH-PUBLIC-REPORT", "ACCEPTED", "A120");
                });
    }

    @Override public DirectoryContent transition(String contentRef, String actorRef, ContentCommand command) {
        return mutate(contentRef, actorRef, command.commandId(), command.idempotencyKey(), fingerprint(command),
                command.expectedAggregateVersion(), current -> switch (command.action()) {
                    case "RECORD_REVIEW" -> {
                        Instant now = clock.instant();
                        if (blank(command.sourceCategory()) || blank(command.sourceRef())
                                || blank(command.verificationScope()) || blank(command.verifiedBy())
                                || command.verifiedAt() == null || command.validUntil() == null
                                || !command.validUntil().isAfter(now)) {
                            throw new ContentRejectedException("CONTENT_EVIDENCE_INCOMPLETE");
                        }
                        yield new Mutation(ContentState.VERIFIED, false, command.sourceCategory(), command.sourceRef(),
                                command.verificationScope(), command.verifiedBy(), command.verifiedAt(),
                                command.validUntil(), "REVIEW_RECORDED", command.reason(), command.scope(),
                                command.authorizationRef(), command.result(), command.evidenceRef());
                    }
                    case "PUBLISH" -> {
                        if (!current.publicationEligible(clock.instant())) {
                            throw new ContentRejectedException("CONTENT_NOT_ELIGIBLE");
                        }
                        yield new Mutation(ContentState.PUBLISHED, false, current.sourceCategory(), current.sourceRef(),
                                current.verificationScope(), current.verifiedBy(), current.verifiedAt(),
                                current.validUntil(), "CONTENT_PUBLISHED", command.reason(), command.scope(),
                                command.authorizationRef(), command.result(), command.evidenceRef());
                    }
                    case "UNPUBLISH" -> new Mutation(ContentState.UNPUBLISHED, current.complaintPending(),
                            current.sourceCategory(), current.sourceRef(), current.verificationScope(),
                            current.verifiedBy(), current.verifiedAt(), current.validUntil(), "CONTENT_UNPUBLISHED",
                            command.reason(), command.scope(), command.authorizationRef(), command.result(),
                            command.evidenceRef());
                    default -> throw new ContentRejectedException("CONTENT_ACTION_NOT_ALLOWED");
                });
    }

    private DirectoryContent mutate(String contentRef, String actorRef, String commandId, String idempotencyKey,
                                    String fingerprint, Long expectedVersion,
                                    java.util.function.Function<DirectoryContent, Mutation> change) {
        try {
            DirectoryContent result = transactions.execute(status -> {
                DirectoryContent replay = replay(commandId, idempotencyKey, fingerprint);
                if (replay != null) return replay;
                Map<String, Object> row = mapper.selectContentForUpdate(contentRef);
                if (row == null) throw new ContentRejectedException("RESOURCE_NOT_CONFIRMABLE");
                DirectoryContent current = content(row, true);
                if (expectedVersion != null && current.version() != expectedVersion) {
                    throw new ContentRejectedException("VERSION_CONFLICT");
                }
                Mutation mutation = change.apply(current);
                long next = current.version() + 1;
                Timestamp now = Timestamp.from(clock.instant());
                int changed = mapper.updateContent(contentRef, mutation.sourceCategory(), mutation.sourceRef(),
                        mutation.verificationScope(), mutation.verifiedBy(), timestamp(mutation.verifiedAt()),
                        timestamp(mutation.validUntil()), mutation.state().name(), mutation.complaintPending(), next,
                        current.version(), now);
                if (changed != 1) throw new ContentRejectedException("VERSION_CONFLICT");
                mapper.insertContentAudit(contentRef, mutation.auditAction(), actorRef, mutation.reason(),
                        current.version(), next, now, mutation.scope(), mutation.authorizationRef(),
                        mutation.result(), mutation.evidenceRef());
                mapper.insertContentCommand(commandId, idempotencyKey, fingerprint, contentRef, now);
                return requireInternal(contentRef);
            });
            if (result == null) throw new IllegalStateException("content transaction returned no result");
            return result;
        } catch (DuplicateKeyException race) {
            DirectoryContent replay = replay(commandId, idempotencyKey, fingerprint);
            if (replay == null) throw new ContentRejectedException("IDEMPOTENCY_CONFLICT");
            return replay;
        }
    }

    private DirectoryContent replay(String commandId, String idempotencyKey, String fingerprint) {
        List<Map<String, Object>> rows = mapper.selectContentCommandsForUpdate(commandId, idempotencyKey);
        if (rows.isEmpty()) return null;
        if (rows.size() != 1 || !fingerprint.equals(string(rows.get(0), "canonical_fingerprint"))) {
            throw new ContentRejectedException("IDEMPOTENCY_CONFLICT");
        }
        return requireInternal(string(rows.get(0), "content_ref"));
    }

    private DirectoryContent content(Map<String, Object> row, boolean audits) {
        String ref = string(row, "content_ref");
        List<ContentAudit> trail = audits ? mapper.selectContentAudit(ref).stream().map(this::audit).toList() : List.of();
        return new DirectoryContent(ref, string(row, "title"), string(row, "summary"), string(row, "category"),
                string(row, "ownership_mode"), nullable(row, "source_category"), nullable(row, "source_ref"),
                nullable(row, "verification_scope"), nullable(row, "verified_by"), instant(row, "verified_at"),
                instant(row, "valid_until"), number(row, "aggregate_version"),
                ContentState.valueOf(string(row, "content_state")), bool(row, "complaint_pending"),
                instant(row, "updated_at"), trail);
    }

    private ContentAudit audit(Map<String, Object> row) {
        return new ContentAudit(number(row, "audit_id"), string(row, "action_type"), string(row, "actor_ref"),
                string(row, "reason"), number(row, "before_version"), number(row, "after_version"),
                instant(row, "occurred_at"), string(row, "scope"), string(row, "authorization_ref"),
                string(row, "result"), string(row, "evidence_ref"));
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : MyBatisFlowStore.timestamp(row, key).toInstant();
    }
    private static String string(Map<String, Object> row, String key) { return String.valueOf(row.get(key)); }
    private static String nullable(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : String.valueOf(row.get(key));
    }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Boolean b ? b : ((Number) value).intValue() != 0;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String fingerprint(ContentCommand value) {
        return CanonicalFingerprint.sha256(value.action(), value.reason(), nullableValue(value.sourceCategory()),
                nullableValue(value.sourceRef()), nullableValue(value.verificationScope()),
                nullableValue(value.verifiedBy()), String.valueOf(value.verifiedAt()),
                String.valueOf(value.validUntil()), String.valueOf(value.expectedAggregateVersion()),
                value.scope(), value.authorizationRef(), value.result(), value.evidenceRef());
    }
    private static String nullableValue(String value) { return value == null ? "" : value; }

    private record Mutation(ContentState state, boolean complaintPending, String sourceCategory, String sourceRef,
                            String verificationScope, String verifiedBy, Instant verifiedAt, Instant validUntil,
                            String auditAction, String reason, String scope, String authorizationRef,
                            String result, String evidenceRef) {}
}
