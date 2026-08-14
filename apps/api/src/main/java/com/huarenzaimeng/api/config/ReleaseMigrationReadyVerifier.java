package com.huarenzaimeng.api.config;

import com.huarenzaimeng.api.DataMigrationOracleVerifier;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** Read-only cross-process gate opener; it never runs Flyway. */
final class ReleaseMigrationReadyVerifier implements ApplicationRunner {
    private static final Set<String> KEYS = Set.of("status", "runId", "authorizationRef", "artifactSha256",
            "candidateManifestSha256", "databaseName", "expectedServerUuid", "oracleManifestSha256",
            "migrationInventorySha256", "consumptionSha256");
    private final ReleaseMigrationAuthorizationStore store;
    private final ReleaseMigrationRuntimeIdentityProvider identities;
    private final ReleaseFlywayMigrationRunner.StageExecutor stages;
    private final ReleaseMigrationState state;

    ReleaseMigrationReadyVerifier(ReleaseMigrationAuthorizationStore store,
                                   ReleaseMigrationRuntimeIdentityProvider identities,
                                  ReleaseFlywayMigrationRunner.StageExecutor stages,
                                  ReleaseMigrationState state) {
        this.store = store; this.identities = identities; this.stages = stages; this.state = state;
    }

    @Override public void run(ApplicationArguments args) {
        try {
            Map<String, String> ready = parse(store.readReady());
            ReleaseMigrationAuthorization authorization = store.preview();
            var identity = identities.current();
            store.validateConsumedIdentity(authorization, identity);
            if (!"READY".equals(ready.get("status"))
                    || !authorization.runId().equals(ready.get("runId"))
                    || !authorization.authorizationRef().equals(ready.get("authorizationRef"))
                    || !identity.artifactSha256().equals(ready.get("artifactSha256"))
                    || !identity.candidateManifestSha256().equals(ready.get("candidateManifestSha256"))
                    || !identity.databaseName().equals(ready.get("databaseName"))
                    || !identity.expectedServerUuid().equals(ready.get("expectedServerUuid"))
                    || !identity.oracleManifestSha256().equals(ready.get("oracleManifestSha256"))
                    || !identity.migrationInventorySha256().equals(ready.get("migrationInventorySha256"))
                    || !ReleaseMigrationAuthorizationStore.sha256(store.readConsumption(authorization.runId()))
                        .equals(ready.get("consumptionSha256"))) return;
            try (var connection = stages.openConnection()) {
                if (DataMigrationOracleVerifier.verify(connection, identity.databaseName(), identity.expectedServerUuid())
                        != DataMigrationOracleVerifier.State.POST_V12) return;
            }
            state.ready();
        } catch (Exception unavailableOrDrift) {
            // Fail closed without leaking identities or exception details.
        }
    }

    private static Map<String, String> parse(byte[] bytes) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\n", -1)) {
            if (line.isEmpty()) continue;
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) throw new IllegalArgumentException();
            String key = line.substring(0, separator);
            if (!KEYS.contains(key) || values.putIfAbsent(key, line.substring(separator + 1)) != null)
                throw new IllegalArgumentException();
        }
        if (!values.keySet().equals(KEYS)) throw new IllegalArgumentException();
        return values;
    }
}
