package com.huarenzaimeng.api.config;

import java.util.concurrent.atomic.AtomicReference;

public final class ReleaseMigrationState {
    public enum Phase { MIGRATING, READY, FAILED }

    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.MIGRATING);

    public Phase phase() {
        return phase.get();
    }

    public boolean isReady() {
        return phase.get() == Phase.READY;
    }

    void ready() {
        phase.set(Phase.READY);
    }

    void failed() {
        phase.set(Phase.FAILED);
    }
}
