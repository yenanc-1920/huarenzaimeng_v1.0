package com.huarenzaimeng.api.config;

import com.huarenzaimeng.api.DataMigrationOracleVerifier;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

record ReleaseMigrationAuthorization(String authorizationRef, String runId, String artifactSha256, String candidateManifestSha256,
                                     String databaseName, String expectedServerUuid,
                                     String grantSnapshotIdentity, String backupEvidenceSha256,
                                     String restoreEvidenceSha256, String oracleManifestSha256,
                                     String migrationInventorySha256, StartState startState, AllowedTarget allowedTarget,
                                     Instant validFrom, Instant validUntil) {
    private static final Pattern REF = Pattern.compile("[A-Z0-9][A-Z0-9_-]{7,95}");
    private static final Pattern SHA = Pattern.compile("[A-F0-9]{64}");

    ReleaseMigrationAuthorization {
        if (!REF.matcher(authorizationRef).matches() || !REF.matcher(runId).matches()) fail();
        for (String sha : List.of(artifactSha256, candidateManifestSha256, grantSnapshotIdentity, backupEvidenceSha256,
                restoreEvidenceSha256, oracleManifestSha256, migrationInventorySha256)) if (!SHA.matcher(sha).matches()) fail();
        if (!DataMigrationOracleVerifier.DEPLOYMENT_DATABASE.equals(databaseName)) fail();
        if (expectedServerUuid == null || expectedServerUuid.isBlank()) fail();
        if (startState == null || allowedTarget == null
                || (startState == StartState.PRE_V10 && allowedTarget != AllowedTarget.V12_VIA_V11)
                || (startState == StartState.MID_V11 && allowedTarget != AllowedTarget.V12_ONLY)
                || (startState == StartState.POST_V12 && allowedTarget != AllowedTarget.V14_VIA_V13)
                || (startState == StartState.POST_V13 && allowedTarget != AllowedTarget.V14_ONLY)) fail();
        if (validFrom == null || validUntil == null || !validFrom.isBefore(validUntil)) fail();
    }

    void requireMatches(ExecutionIdentity actual, Instant now) {
        if (now.isBefore(validFrom) || !now.isBefore(validUntil)) fail();
        requireIdentityMatches(actual);
    }

    void requireIdentityMatches(ExecutionIdentity actual) {
        if (!artifactSha256.equals(actual.artifactSha256)
                || !candidateManifestSha256.equals(actual.candidateManifestSha256)
                || !databaseName.equals(actual.databaseName)
                || !expectedServerUuid.equals(actual.expectedServerUuid)
                || !grantSnapshotIdentity.equals(actual.grantSnapshotIdentity)
                || !backupEvidenceSha256.equals(actual.backupEvidenceSha256)
                || !restoreEvidenceSha256.equals(actual.restoreEvidenceSha256)
                || !oracleManifestSha256.equals(actual.oracleManifestSha256)
                || !migrationInventorySha256.equals(actual.migrationInventorySha256)) fail();
    }

    private static void fail() { throw new IllegalArgumentException("MIGRATION_AUTHORIZATION_INVALID"); }

    record ExecutionIdentity(String artifactSha256, String candidateManifestSha256, String databaseName, String expectedServerUuid,
                             String grantSnapshotIdentity, String backupEvidenceSha256,
                             String restoreEvidenceSha256, String oracleManifestSha256,
                             String migrationInventorySha256) {}

    enum StartState { PRE_V10, MID_V11, POST_V12, POST_V13 }
    enum AllowedTarget { V12_VIA_V11, V12_ONLY, V14_VIA_V13, V14_ONLY }
}
