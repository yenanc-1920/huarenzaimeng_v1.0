package com.huarenzaimeng.api;

import java.time.Instant;

record StateAdvanceCommand(CommandIdentity command, StateAdvanceAuthority authority,
                           StateAdvanceAuthority.AuthorizationDecision authorization,
                           String aggregateEnvironment, String targetTransition, String evidenceRef,
                           Instant decisionInstant) {
    StateAdvanceCommand {
        if (command == null || authority == null || authorization == null || decisionInstant == null)
            throw new FlowRejectedException("STATE_ADVANCE_AUTHORITY_MISSING");
    }
}
