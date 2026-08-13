package com.huarenzaimeng.api;

interface DataIntegrationReadinessProbe {
    Snapshot inspect();

    record GrantSummary(int usageCount, boolean usageExact, int expectedRequiredCount,
                        boolean expectedRequiredComplete, boolean expectedRequiredExact,
                        int platformAdditionalCount, boolean platformAdditionalApprovedOnly,
                        int unknownCount, boolean unknownAbsent, boolean parserCompatible,
                        boolean permissionAdjustmentRequired, boolean grantBoundarySatisfied) {}

    record GrantUnknownBreakdown(int unexpectedPrivilegeCount, int unexpectedScopeCount,
                                 int duplicateRequiredCount, int duplicateUsageCount,
                                 int malformedCount, int otherUnknownCount) {
        int total() {
            return Math.addExact(Math.addExact(Math.addExact(unexpectedPrivilegeCount, unexpectedScopeCount),
                    Math.addExact(duplicateRequiredCount, duplicateUsageCount)),
                    Math.addExact(malformedCount, otherUnknownCount));
        }
    }

    record FlywaySummary(String status, int installedCount, String currentVersion,
                         boolean allSuccessful, String canonicalSha256) {}

    record SchemaSummary(int tableCount, int columnCount, int indexCount, int foreignKeyCount,
                         String canonicalSha256) {}

    record MigrationDiscovery(String status, int versionCount, String highestVersion,
                              String canonicalSha256) {}

    record MigrationOracleSummary(String state, boolean terminalMatched) {}

    record BackupSummary(String referenceStatus, String restoreEvidenceStatus) {}

    record Snapshot(String serviceArtifactIdentity, String databaseIdentity, String databaseEngineStatus,
                    GrantSummary applicationGrant, GrantSummary flywayGrant,
                    GrantUnknownBreakdown applicationGrantUnknownBreakdown,
                    GrantUnknownBreakdown flywayGrantUnknownBreakdown,
                    FlywaySummary flyway, SchemaSummary schema,
                    MigrationDiscovery migrationDiscovery, MigrationOracleSummary migrationOracle,
                    BackupSummary backup,
                    boolean ready) {}
}
