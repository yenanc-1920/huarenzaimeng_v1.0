package com.huarenzaimeng.api;

record CommandIdentity(
        String commandId,
        String idempotencyKey,
        String endpointScope,
        String resourceScope,
        String semanticActionKey,
        String canonicalFingerprint
) {}
