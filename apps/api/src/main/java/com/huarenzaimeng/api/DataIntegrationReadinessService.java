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
                value.applicationGrant(), value.flywayGrant(), value.flyway(), value.schema(),
                value.migrationDiscovery(), value.backup());
    }

    record Response(String status, String projectCode, String serviceArtifactIdentity,
                    String databaseIdentity, String databaseEngineStatus,
                    DataIntegrationReadinessProbe.GrantSummary applicationGrant,
                    DataIntegrationReadinessProbe.GrantSummary flywayGrant,
                    DataIntegrationReadinessProbe.FlywaySummary flyway,
                    DataIntegrationReadinessProbe.SchemaSummary schema,
                    DataIntegrationReadinessProbe.MigrationDiscovery migrationDiscovery,
                    DataIntegrationReadinessProbe.BackupSummary backup) {}
}
