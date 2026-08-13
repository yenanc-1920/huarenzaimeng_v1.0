package com.huarenzaimeng.api;

interface DataIntegrationReadinessProbe {
    Snapshot inspect();

    record GrantSummary(String principalClass, String status, String canonicalSha256,
                        int grantCount, boolean requiredPrivilegesPresent,
                        boolean forbiddenPrivilegesAbsent) {}

    record FlywaySummary(String status, int installedCount, String currentVersion,
                         boolean allSuccessful, String canonicalSha256) {}

    record SchemaSummary(int tableCount, int columnCount, int indexCount, int foreignKeyCount,
                         String canonicalSha256) {}

    record MigrationDiscovery(String status, int versionCount, String highestVersion,
                              String canonicalSha256) {}

    record BackupSummary(String referenceStatus, String restoreEvidenceStatus) {}

    record Snapshot(String serviceArtifactIdentity, String databaseIdentity, String databaseEngineStatus,
                    GrantSummary applicationGrant, GrantSummary flywayGrant,
                    FlywaySummary flyway, SchemaSummary schema,
                    MigrationDiscovery migrationDiscovery, BackupSummary backup,
                    boolean ready) {}
}
