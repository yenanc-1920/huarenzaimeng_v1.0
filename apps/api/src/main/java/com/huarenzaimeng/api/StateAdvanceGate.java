package com.huarenzaimeng.api;

final class StateAdvanceGate {
    private StateAdvanceGate() {}
    record AggregateIdentity(StateAdvanceAuthority.Environment environment,
                             StateAdvanceAuthority.EvidenceLevel evidenceLevel,String authorityState) {}
    static void verify(String orderRef, AggregateIdentity aggregate, StateAdvanceCommand c) {
        var a=c.authority(); var d=c.authorization();
        boolean exact = a.authorizationRef().equals(d.authorizationRef())
                && a.environment()==d.environment() && a.evidenceLevel()==d.evidenceLevel()
                && aggregate!=null && a.environment()==aggregate.environment()
                && a.evidenceLevel()==aggregate.evidenceLevel()
                && "NON_PRODUCTION".equals(aggregate.authorityState())
                && a.environment().name().equals(c.aggregateEnvironment())
                && orderRef.equals(d.aggregateRef()) && c.targetTransition().equals(d.targetTransition())
                && c.evidenceRef()!=null && c.evidenceRef().equals(d.evidenceRef())
                && d.status()==StateAdvanceAuthority.AuthorizationDecision.Status.ACTIVE
                && d.validFrom()!=null && d.validUntil()!=null
                && !c.decisionInstant().isBefore(d.validFrom()) && c.decisionInstant().isBefore(d.validUntil());
        if (!exact) throw new FlowRejectedException("STATE_ADVANCE_NOT_AUTHORIZED");
        // No real authorization exists in this slice. The only reachable positive is explicitly local and synthetic.
        if (!d.controlledFixture() || a.environment()!=StateAdvanceAuthority.Environment.LOCAL_SYNTHETIC
                || a.evidenceLevel()!=StateAdvanceAuthority.EvidenceLevel.L1)
            throw new FlowRejectedException("REAL_STATE_ADVANCE_NOT_AUTHORIZED");
    }
}
