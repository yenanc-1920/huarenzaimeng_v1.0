package com.huarenzaimeng.api;

import java.time.Instant;

/** Single server-side construction point. It is not a controller and accepts no request object. */
final class StateAdvanceAuthorityResolver {
    private static final Capability CAPABILITY=new Capability();
    private StateAdvanceAuthorityResolver() {}
    static StateAdvanceCommand resolveControlledLocal(CommandIdentity command,String orderRef,String target,
                                                       String evidenceRef,Instant now) {
        String authRef="AUTH-LOCAL-CONTRACT-01";
        var authority=new StateAdvanceAuthority(StateAdvanceAuthority.Environment.LOCAL_SYNTHETIC,
                StateAdvanceAuthority.EvidenceLevel.L1,authRef,CAPABILITY);
        var decision=new StateAdvanceAuthority.AuthorizationDecision(authRef,authority.environment(),
                authority.evidenceLevel(),orderRef,target,evidenceRef,now.minusSeconds(1),now.plusSeconds(60),
                StateAdvanceAuthority.AuthorizationDecision.Status.ACTIVE,true);
        return new StateAdvanceCommand(command,authority,decision,"LOCAL_SYNTHETIC",target,evidenceRef,now);
    }
    static final class Capability { private Capability() {} }
}
