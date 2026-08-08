package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Path;

/**
 * Outer Maven entry for the zero-scenario host preflight.
 *
 * <p>It deliberately does not manufacture an authorization or a RunId. The
 * approved caller must bind both values as JVM properties; the controlled host
 * then reloads and validates the original authorization bytes.</p>
 */
class P021SameChainHostPreflightExecutionTest {
    @Test
    void runsOnlyTheAuthorizedZeroScenarioHostPreflight() throws Exception {
        Assumptions.assumeTrue(hasProperty("p021.preflight.runId")
                        && hasProperty("p021.preflight.authorizationRecord")
                        && hasProperty("p021.preflight.authorizationRecordSha"),
                "host preflight execution requires explicit single-run authorization properties");
        String runId = requiredProperty("p021.preflight.runId");
        Path authorizationRecord = Path.of(requiredProperty("p021.preflight.authorizationRecord"));
        String authorizationRecordSha = requiredProperty("p021.preflight.authorizationRecordSha");

        P021ControlledEvidenceJavaHost.main(new String[]{
                "--selector", "PREFLIGHT",
                "--runId", runId,
                "--authorizationRecord", authorizationRecord.toString(),
                "--authorizationRecordSha", authorizationRecordSha
        });
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("PREFLIGHT_PROPERTY_MISSING|" + name);
        return value;
    }

    private static boolean hasProperty(String name) {
        String value = System.getProperty(name);
        return value != null && !value.isBlank();
    }
}
