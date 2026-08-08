package com.huarenzaimeng.api;

import java.time.Instant;

final class FencedResultInputs {
    private FencedResultInputs() {}

    static void validate(FencedResultCommand command, Instant now) {
        if (command == null || blank(command.taskKey()) || blank(command.owner()) || command.fencingToken() <= 0
                || blank(command.resultKey()) || blank(command.aggregateRef())
                || blank(command.canonicalFingerprint()) || blank(command.payloadJson()) || now == null) {
            throw new IllegalArgumentException("invalid fenced result input");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
