package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "in-memory")
class InMemoryContentStore implements ContentStore {
    private final Map<String, DirectoryContent> contents = new HashMap<>();
    private final Map<String, CommandResult> commands = new HashMap<>();
    private final Clock clock;

    InMemoryContentStore(Clock clock) { this.clock = clock; }

    @Override
    public synchronized List<DirectoryContent> listPublic() {
        Instant now = clock.instant();
        return contents.values().stream()
                .filter(content -> content.state() == ContentState.PUBLISHED && content.publicationEligible(now))
                .sorted(Comparator.comparing(DirectoryContent::contentRef))
                .map(DirectoryContent::withoutAudit).toList();
    }

    @Override
    public synchronized DirectoryContent requirePublic(String contentRef, long contentVersion) {
        DirectoryContent content = contents.get(contentRef);
        requireCurrentPublicQualification(content, contentVersion);
        return content.withoutAudit();
    }

    private void requireCurrentPublicQualification(DirectoryContent content, long contentVersion) {
        if (content == null) throw new ContentRejectedException("RESOURCE_NOT_CONFIRMABLE");
        if (content.version() != contentVersion) throw new ContentRejectedException("CONTENT_VERSION_STALE");
        if (content.state() == ContentState.UNDER_REVIEW || content.complaintPending()) {
            throw new ContentRejectedException("CONTENT_UNDER_REVIEW");
        }
        if (content.state() == ContentState.UNPUBLISHED) {
            throw new ContentRejectedException("CONTENT_REMOVED");
        }
        if (content.state() != ContentState.PUBLISHED || !content.publicationEligible(clock.instant())) {
            throw new ContentRejectedException("CONTENT_QUALIFICATION_UNKNOWN");
        }
    }

    @Override
    public synchronized List<DirectoryContent> listInternal() {
        return contents.values().stream().sorted(Comparator.comparing(DirectoryContent::contentRef)).toList();
    }

    @Override
    public synchronized DirectoryContent requireInternal(String contentRef) {
        DirectoryContent content = contents.get(contentRef);
        if (content == null) throw new ContentRejectedException("RESOURCE_NOT_CONFIRMABLE");
        return content;
    }

    @Override
    public synchronized DirectoryContent createForTest(DirectoryContent content) {
        if (!"SELF_OPERATED_CHINA_COMPANY".equals(content.ownershipMode())) {
            throw new ContentRejectedException("CONTENT_SCOPE_NOT_ALLOWED");
        }
        if (contents.putIfAbsent(content.contentRef(), content) != null) {
            throw new ContentRejectedException("CONTENT_ALREADY_EXISTS");
        }
        return content;
    }

    @Override
    public synchronized DirectoryContent report(String contentRef, String actorRef, String commandId,
                                                String idempotencyKey, long contentVersion,
                                                long expectedAggregateVersion, String reason) {
        String fingerprint = CanonicalFingerprint.sha256("REPORT", contentRef, String.valueOf(contentVersion),
                String.valueOf(expectedAggregateVersion), reason);
        DirectoryContent replay = replay(commandId, idempotencyKey, fingerprint);
        if (replay != null) return replay;
        DirectoryContent current = requireInternal(contentRef);
        if (current.version() != contentVersion || current.version() != expectedAggregateVersion) {
            throw new ContentRejectedException("CONTENT_VERSION_STALE");
        }
        requireCurrentPublicQualification(current, contentVersion);
        long next = current.version() + 1;
        Instant now = clock.instant();
        List<ContentAudit> audits = append(current, "CONTENT_ERROR_REPORTED", actorRef, reason, next, now,
                "PUBLIC_CONTENT_REPORT", "AUTH-PUBLIC-REPORT", "ACCEPTED", "A120");
        DirectoryContent updated = new DirectoryContent(current.contentRef(), current.title(), current.summary(),
                current.category(), current.ownershipMode(), current.sourceCategory(), current.sourceRef(),
                current.verificationScope(), current.verifiedBy(), current.verifiedAt(), current.validUntil(), next,
                ContentState.UNDER_REVIEW, true, now, audits);
        contents.put(contentRef, updated);
        record(commandId, idempotencyKey, fingerprint, contentRef);
        return updated;
    }

    @Override
    public synchronized DirectoryContent transition(String contentRef, String actorRef, ContentCommand command) {
        String fingerprint = fingerprint(command);
        DirectoryContent replay = replay(command.commandId(), command.idempotencyKey(), fingerprint);
        if (replay != null) return replay;
        DirectoryContent current = requireInternal(contentRef);
        if (current.version() != command.expectedAggregateVersion()) {
            throw new ContentRejectedException("VERSION_CONFLICT");
        }
        long next = current.version() + 1;
        Instant now = clock.instant();
        DirectoryContent updated = switch (command.action()) {
            case "RECORD_REVIEW" -> reviewed(current, command, actorRef, next, now);
            case "PUBLISH" -> published(current, command, actorRef, next, now);
            case "UNPUBLISH" -> unpublished(current, command, actorRef, next, now);
            default -> throw new ContentRejectedException("CONTENT_ACTION_NOT_ALLOWED");
        };
        contents.put(contentRef, updated);
        record(command.commandId(), command.idempotencyKey(), fingerprint, contentRef);
        return updated;
    }

    private DirectoryContent reviewed(DirectoryContent current, ContentCommand command, String actor, long next,
                                      Instant now) {
        if (blank(command.sourceCategory()) || blank(command.sourceRef()) || blank(command.verificationScope())
                || blank(command.verifiedBy()) || command.verifiedAt() == null || command.validUntil() == null
                || !command.validUntil().isAfter(now)) {
            throw new ContentRejectedException("CONTENT_EVIDENCE_INCOMPLETE");
        }
        return new DirectoryContent(current.contentRef(), current.title(), current.summary(), current.category(),
                current.ownershipMode(), command.sourceCategory(), command.sourceRef(), command.verificationScope(),
                command.verifiedBy(), command.verifiedAt(), command.validUntil(), next, ContentState.VERIFIED,
                false, now, append(current, "REVIEW_RECORDED", actor, command.reason(), next, now,
                command.scope(), command.authorizationRef(), command.result(), command.evidenceRef()));
    }

    private DirectoryContent published(DirectoryContent current, ContentCommand command, String actor, long next,
                                       Instant now) {
        if (!current.publicationEligible(now)) throw new ContentRejectedException("CONTENT_NOT_ELIGIBLE");
        return new DirectoryContent(current.contentRef(), current.title(), current.summary(), current.category(),
                current.ownershipMode(), current.sourceCategory(), current.sourceRef(), current.verificationScope(),
                current.verifiedBy(), current.verifiedAt(), current.validUntil(), next, ContentState.PUBLISHED,
                false, now, append(current, "CONTENT_PUBLISHED", actor, command.reason(), next, now,
                command.scope(), command.authorizationRef(), command.result(), command.evidenceRef()));
    }

    private DirectoryContent unpublished(DirectoryContent current, ContentCommand command, String actor, long next,
                                         Instant now) {
        return new DirectoryContent(current.contentRef(), current.title(), current.summary(), current.category(),
                current.ownershipMode(), current.sourceCategory(), current.sourceRef(), current.verificationScope(),
                current.verifiedBy(), current.verifiedAt(), current.validUntil(), next, ContentState.UNPUBLISHED,
                current.complaintPending(), now,
                append(current, "CONTENT_UNPUBLISHED", actor, command.reason(), next, now,
                        command.scope(), command.authorizationRef(), command.result(), command.evidenceRef()));
    }

    private DirectoryContent replay(String commandId, String idempotencyKey, String fingerprint) {
        CommandResult byCommand = commands.get("C:" + commandId);
        CommandResult byIdem = commands.get("I:" + idempotencyKey);
        CommandResult existing = byCommand != null ? byCommand : byIdem;
        if (existing == null) return null;
        if (!existing.fingerprint().equals(fingerprint)) throw new ContentRejectedException("IDEMPOTENCY_CONFLICT");
        return requireInternal(existing.contentRef());
    }

    private void record(String commandId, String idempotencyKey, String fingerprint, String contentRef) {
        CommandResult result = new CommandResult(fingerprint, contentRef);
        commands.put("C:" + commandId, result);
        commands.put("I:" + idempotencyKey, result);
    }

    private static List<ContentAudit> append(DirectoryContent current, String action, String actor, String reason,
                                             long next, Instant now, String scope, String authorizationRef,
                                             String result, String evidenceRef) {
        List<ContentAudit> audits = new ArrayList<>(current.auditTrail());
        audits.add(new ContentAudit(audits.size() + 1L, action, actor, reason, current.version(), next, now,
                scope, authorizationRef, result, evidenceRef));
        return List.copyOf(audits);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String fingerprint(ContentCommand value) {
        return CanonicalFingerprint.sha256(value.action(), value.reason(), nullable(value.sourceCategory()),
                nullable(value.sourceRef()), nullable(value.verificationScope()), nullable(value.verifiedBy()),
                String.valueOf(value.verifiedAt()), String.valueOf(value.validUntil()),
                String.valueOf(value.expectedAggregateVersion()), value.scope(), value.authorizationRef(),
                value.result(), value.evidenceRef());
    }
    private static String nullable(String value) { return value == null ? "" : value; }
    private record CommandResult(String fingerprint, String contentRef) {}
}
