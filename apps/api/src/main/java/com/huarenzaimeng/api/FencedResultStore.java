package com.huarenzaimeng.api;

import java.time.Instant;

interface FencedResultStore {
    FencedResultOutcome write(FencedResultCommand command, Instant now);
}
