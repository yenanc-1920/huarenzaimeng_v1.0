package com.huarenzaimeng.api;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Clock;
import java.util.List;

@Service
class ContentService {
    private final ContentStore store;
    private final Clock clock;

    ContentService(ContentStore store, Clock clock) { this.store = store; this.clock = clock; }

    ContentPage<PublicContentSummary> publicList() {
        List<PublicContentSummary> items = store.listPublic().stream()
                .map(value -> new PublicContentSummary(value.contentRef(), value.title(), value.summary(),
                        value.category(), value.version(), value.validUntil(), value.updatedAt(), "ELIGIBLE"))
                .toList();
        return new ContentPage<>(items, items.size());
    }

    PublicContentProjection publicDetail(String contentRef, long contentVersion) {
        return store.requirePublic(contentRef, contentVersion).publicProjection();
    }

    ContentPage<DirectoryContent> internalList() {
        List<DirectoryContent> items = store.listInternal();
        return new ContentPage<>(items, items.size());
    }

    DirectoryContent internalDetail(String contentRef) { return store.requireInternal(contentRef); }

    DirectoryContent report(String contentRef, String actorRef, String commandId, String idempotencyKey,
                            long contentVersion, long expectedAggregateVersion, String reason) {
        return store.report(contentRef, actorRef, commandId, idempotencyKey, contentVersion,
                expectedAggregateVersion, reason);
    }

    DirectoryContent transition(String contentRef, String actorRef, ContentCommand command) {
        return store.transition(contentRef, actorRef, command);
    }

    DirectoryContent createTestFixture(String contentRef, String title, String summary, String category) {
        Instant now = clock.instant();
        return store.createForTest(new DirectoryContent(contentRef, title, summary, category,
                "SELF_OPERATED_CHINA_COMPANY", null, null, null, null, null, null, 1,
                ContentState.DRAFT, false, now, List.of()));
    }
}
