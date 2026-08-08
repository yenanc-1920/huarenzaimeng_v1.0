package com.huarenzaimeng.api;

import java.util.List;

interface ContentStore {
    List<DirectoryContent> listPublic();
    DirectoryContent requirePublic(String contentRef, long contentVersion);
    List<DirectoryContent> listInternal();
    DirectoryContent requireInternal(String contentRef);
    DirectoryContent createForTest(DirectoryContent content);
    DirectoryContent report(String contentRef, String actorRef, String commandId, String idempotencyKey,
                            long contentVersion, long expectedAggregateVersion, String reason);
    DirectoryContent transition(String contentRef, String actorRef, ContentCommand command);
}
