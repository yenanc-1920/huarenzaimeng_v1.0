package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "hz.data-integration.readiness-enabled", havingValue = "true")
final class DataIntegrationReadinessService {
    private final DataIntegrationReadinessProbe probe;

    DataIntegrationReadinessService(DataIntegrationReadinessProbe probe) { this.probe = probe; }

    Response read() {
        DataIntegrationReadinessProbe.Snapshot value = probe.inspect();
        return new Response(value.ready() ? "READY" : "NOT_READY", "DATA_INTEGRATION_DB_READINESS",
                value.serviceArtifactIdentity(), value.databaseIdentity(), value.databaseEngineStatus(),
                publicGrant(value.applicationGrant()), publicGrant(value.flywayGrant()), value.flyway(), value.schema(),
                value.migrationDiscovery(), value.backup());
    }

    private static PublicGrantSummary publicGrant(DataIntegrationReadinessProbe.GrantSummary value) {
        return new PublicGrantSummary(value.usageCount(), value.usageExact(), value.expectedRequiredCount(),
                value.expectedRequiredComplete(), value.expectedRequiredExact(), value.platformAdditionalCount(),
                value.platformAdditionalApprovedOnly(), value.unknownCount(), value.unknownAbsent(),
                value.parserCompatible(), value.permissionAdjustmentRequired(), value.grantBoundarySatisfied());
    }

    record Response(String status, String projectCode, String serviceArtifactIdentity,
                    String databaseIdentity, String databaseEngineStatus,
                    PublicGrantSummary applicationGrant, PublicGrantSummary flywayGrant,
                    DataIntegrationReadinessProbe.FlywaySummary flyway,
                    DataIntegrationReadinessProbe.SchemaSummary schema,
                    DataIntegrationReadinessProbe.MigrationDiscovery migrationDiscovery,
                    DataIntegrationReadinessProbe.BackupSummary backup) {}

    record PublicGrantSummary(int usageCount, boolean usageExact, int expectedRequiredCount,
                              boolean expectedRequiredComplete, boolean expectedRequiredExact,
                              int platformAdditionalCount, boolean platformAdditionalApprovedOnly,
                              int unknownCount, boolean unknownAbsent, boolean parserCompatible,
                              boolean permissionAdjustmentRequired, boolean grantBoundarySatisfied) {}
}
