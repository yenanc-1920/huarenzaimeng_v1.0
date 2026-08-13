package com.huarenzaimeng.api;

import java.time.Instant;
import java.util.Objects;

/** Server-trusted authority for a single aggregate transition. Never a request DTO. */
final class StateAdvanceAuthority {
    enum Environment { LOCAL_SYNTHETIC, SANDBOX, PREPRODUCTION, PRODUCTION }
    enum EvidenceLevel { L1, L2, L3, L4 }

    private final Environment environment;
    private final EvidenceLevel evidenceLevel;
    private final String authorizationRef;

    StateAdvanceAuthority(Environment environment, EvidenceLevel evidenceLevel, String authorizationRef,
                          StateAdvanceAuthorityResolver.Capability capability) {
        if (capability == null) throw new FlowRejectedException("STATE_ADVANCE_AUTHORITY_UNTRUSTED");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.evidenceLevel = Objects.requireNonNull(evidenceLevel, "evidenceLevel");
        if (authorizationRef == null || !authorizationRef.matches("AUTH-[A-Z0-9_-]{8,80}"))
            throw new FlowRejectedException("STATE_ADVANCE_AUTHORITY_INVALID");
        this.authorizationRef = authorizationRef;
    }

    Environment environment() { return environment; }
    EvidenceLevel evidenceLevel() { return evidenceLevel; }
    String authorizationRef() { return authorizationRef; }

    record AuthorizationDecision(String authorizationRef, Environment environment, EvidenceLevel evidenceLevel,
                                 String aggregateRef, String targetTransition, String evidenceRef,
                                 Instant validFrom, Instant validUntil, Status status, boolean controlledFixture) {
        enum Status { ACTIVE, REVOKED, UNKNOWN }
        AuthorizationDecision {
            Objects.requireNonNull(environment); Objects.requireNonNull(evidenceLevel); Objects.requireNonNull(status);
        }
    }
}
