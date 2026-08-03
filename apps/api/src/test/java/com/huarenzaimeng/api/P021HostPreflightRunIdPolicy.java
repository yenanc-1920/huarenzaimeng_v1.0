package com.huarenzaimeng.api;

import java.util.Set;
import java.util.regex.Pattern;

/** Shared JDK-only RunId policy for bootstrap, outer wrapper and controlled host. */
final class P021HostPreflightRunIdPolicy {
    private static final Pattern FORMAT=Pattern.compile("^P021-TECH-HOST-PREFLIGHT-[A-Za-z0-9_-]{8,100}$");
    private static final Set<String> PERMANENTLY_DENIED=Set.of(
            "P021-TECH-HOST-PREFLIGHT-20260803-001",
            "P021-TECH-HOST-PREFLIGHT-20260803-002",
            "P021-TECH-HOST-PREFLIGHT-20260803-003");

    static String validate(String runId){
        if(runId==null||!FORMAT.matcher(runId).matches()||runId.contains("..")||runId.contains("/")||runId.contains("\\")||PERMANENTLY_DENIED.contains(runId))
            throw new IllegalStateException("PREFLIGHT_RUN_ID_INVALID_OR_DENIED");
        return runId;
    }

    static void validateAuthorizationBinding(String requestedRunId,String authorizedRunId){
        validate(requestedRunId);
        if(!requestedRunId.equals(authorizedRunId))throw new IllegalStateException("PREFLIGHT_AUTHORIZATION_RUN_ID_MISMATCH");
    }

    private P021HostPreflightRunIdPolicy(){}
}
