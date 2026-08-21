package com.huarenzaimeng.api.config;

import com.huarenzaimeng.api.DataMigrationOracleVerifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ReleaseFlywayMigrationRunnerTest {
    private static final String SHA_A = "A".repeat(64);
    private static final String SHA_B = "B".repeat(64);
    private static final String SHA_C = "C".repeat(64);
    private static final String SHA_D = "D".repeat(64);
    private static final String SHA_E = "E".repeat(64);
    private static final String SHA_F = "F".repeat(64);
    private static final String UUID = "e70767e4-9719-11f1-9bfa-02505527b2b0";
    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    @TempDir Path temp;

    @Test void exactTwoStageChainOpensGateOnlyAfterV11AndV12Oracles() throws Exception {
        Fixture fixture = fixture("RUN-000001", NOW.minusSeconds(1), NOW.plusSeconds(60), identity());
        try (var oracle = oracle(DataMigrationOracleVerifier.State.PRE_V10,
                DataMigrationOracleVerifier.State.MID_V11, DataMigrationOracleVerifier.State.POST_V12)) {
            fixture.runner().execute();
        }
        assertThat(fixture.stages().targets).containsExactly("11", "12");
        assertThat(fixture.state().phase()).isEqualTo(ReleaseMigrationState.Phase.MIGRATING);
        assertThat(Files.readString(fixture.marker())).isEqualTo("SUCCEEDED\n");
    }

    @Test void exactMidV11RecoveryUsesNewAuthorizationAndRunsOnlyV12() throws Exception {
        Fixture fixture = fixture("RUN-000008", NOW.minusSeconds(1), NOW.plusSeconds(60), identity(),
                ReleaseMigrationAuthorization.StartState.MID_V11,
                ReleaseMigrationAuthorization.AllowedTarget.V12_ONLY);
        try (var oracle = oracle(DataMigrationOracleVerifier.State.MID_V11,
                DataMigrationOracleVerifier.State.POST_V12)) {
            fixture.runner().execute();
        }
        assertThat(fixture.stages().targets).containsExactly("12");
        assertThat(fixture.state().phase()).isEqualTo(ReleaseMigrationState.Phase.MIGRATING);
    }

    @Test void postV12NewAuthorizationRunsV13ThenV14AndOnlyV14OpensGate() throws Exception {
        Fixture fixture=fixture("RUN-000014",NOW.minusSeconds(1),NOW.plusSeconds(60),identity(),
                ReleaseMigrationAuthorization.StartState.POST_V12,
                ReleaseMigrationAuthorization.AllowedTarget.V14_VIA_V13);
        try(var oracle=oracle(DataMigrationOracleVerifier.State.POST_V12,
                DataMigrationOracleVerifier.State.POST_V13,DataMigrationOracleVerifier.State.POST_V14)){
            fixture.runner().execute();
        }
        assertThat(fixture.stages().targets).containsExactly("13","14");
        assertThat(fixture.state().phase()).isEqualTo(ReleaseMigrationState.Phase.READY);
    }

    @Test void preflightMismatchDoesNotConsumeAuthorization() throws Exception {
        Fixture fixture = fixture("RUN-000009", NOW.minusSeconds(1), NOW.plusSeconds(60), identity());
        try (var oracle = oracle(DataMigrationOracleVerifier.State.NO_GO_PARTIAL_OR_DRIFT)) {
            assertThatThrownBy(fixture.runner()::execute).hasMessage("MIGRATION_START_ORACLE_MISMATCH");
        }
        assertThat(Files.exists(fixture.marker())).isFalse();
        assertThat(fixture.stages().targets).isEmpty();
    }

    @Test void v11FailureStopsBeforeV12AndSealsRunId() throws Exception {
        Fixture fixture = fixture("RUN-000002", NOW.minusSeconds(1), NOW.plusSeconds(60), identity());
        fixture.stages().failTarget = "11";
        try (var oracle = oracle(DataMigrationOracleVerifier.State.PRE_V10)) {
            assertThatThrownBy(fixture.runner()::execute).hasMessage("SYNTHETIC_TARGET_FAILURE");
        }
        assertThat(fixture.stages().targets).containsExactly("11");
        assertFailedAndSealed(fixture);
    }

    @Test void midDriftStopsBeforeV12AndNeverOpensGate() throws Exception {
        Fixture fixture = fixture("RUN-000003", NOW.minusSeconds(1), NOW.plusSeconds(60), identity());
        try (var oracle = oracle(DataMigrationOracleVerifier.State.PRE_V10,
                DataMigrationOracleVerifier.State.NO_GO_PARTIAL_OR_DRIFT)) {
            assertThatThrownBy(fixture.runner()::execute).hasMessage("MIGRATION_AFTER_V11_ORACLE_MISMATCH");
        }
        assertThat(fixture.stages().targets).containsExactly("11");
        assertFailedAndSealed(fixture);
    }

    @Test void v12FailureKeepsBusinessGateClosedAndSealsRunId() throws Exception {
        Fixture fixture = fixture("RUN-000004", NOW.minusSeconds(1), NOW.plusSeconds(60), identity());
        fixture.stages().failTarget = "12";
        try (var oracle = oracle(DataMigrationOracleVerifier.State.PRE_V10, DataMigrationOracleVerifier.State.MID_V11)) {
            assertThatThrownBy(fixture.runner()::execute).hasMessage("SYNTHETIC_TARGET_FAILURE");
        }
        assertThat(fixture.stages().targets).containsExactly("11", "12");
        assertFailedAndSealed(fixture);
    }

    @Test void duplicateRunIdAndProcessRestartCannotExecuteAgain() throws Exception {
        Fixture first = fixture("RUN-000005", NOW.minusSeconds(1), NOW.plusSeconds(60), identity());
        try (var oracle = oracle(DataMigrationOracleVerifier.State.PRE_V10,
                DataMigrationOracleVerifier.State.MID_V11, DataMigrationOracleVerifier.State.POST_V12)) {
            first.runner().execute();
        }
        FakeStages restartedStages = new FakeStages();
        var restarted = new ReleaseFlywayMigrationRunner(restartedStages, new ReleaseMigrationState(),
                ReleaseMigrationAuthorizationStore.forTest(first.authorization().getParent(), fixedClock()),
                identityProvider(identity()));
        try (var oracle = oracle(DataMigrationOracleVerifier.State.PRE_V10)) {
            assertThatThrownBy(restarted::execute).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        }
        assertThat(restartedStages.targets).isEmpty();
    }

    @Test void expiredAuthorizationFailsBeforeConsumptionAndMigration() throws Exception {
        Fixture fixture = fixture("RUN-000006", NOW.minusSeconds(120), NOW.minusSeconds(60), identity());
        assertThatThrownBy(fixture.runner()::execute).hasMessage("MIGRATION_AUTHORIZATION_INVALID");
        assertThat(fixture.stages().targets).isEmpty();
        assertThat(Files.exists(fixture.marker())).isFalse();
    }

    @Test void crossProcessReadyRevalidatesConsumedIdentityAndPostV12WithoutRequiringUnexpiredWindow() throws Exception {
        Fixture fixture = fixture("RUN-000010", NOW.minusSeconds(1), NOW.plusSeconds(5), identity());
        try (var oracle = oracle(DataMigrationOracleVerifier.State.PRE_V10,
                DataMigrationOracleVerifier.State.MID_V11, DataMigrationOracleVerifier.State.POST_V12)) {
            fixture.runner().execute();
        }
        Path root = fixture.marker().getParent().getParent();
        var later = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);
        var store = ReleaseMigrationAuthorizationStore.forTest(root, later);
        ReleaseMigrationState restartedState = new ReleaseMigrationState();
        try (var oracle = oracle(DataMigrationOracleVerifier.State.POST_V12)) {
            new ReleaseMigrationReadyVerifier(store, identityProvider(identity()), new FakeStages(), restartedState).run(null);
        }
        assertThat(restartedState.phase()).isEqualTo(ReleaseMigrationState.Phase.MIGRATING);
    }

    @Test void forgedCrossProcessReadyNeverOpensGate() throws Exception {
        Fixture fixture = fixture("RUN-000011", NOW.minusSeconds(1), NOW.plusSeconds(60), identity());
        try (var oracle = oracle(DataMigrationOracleVerifier.State.PRE_V10,
                DataMigrationOracleVerifier.State.MID_V11, DataMigrationOracleVerifier.State.POST_V12)) {
            fixture.runner().execute();
        }
        Path root = fixture.marker().getParent().getParent();
        Files.writeString(root.resolve("READY.properties"), "status=READY\nrunId=FORGED-RUN\n");
        ReleaseMigrationState restartedState = new ReleaseMigrationState();
        new ReleaseMigrationReadyVerifier(
                ReleaseMigrationAuthorizationStore.forTest(root, fixedClock()),
                identityProvider(identity()), new FakeStages(), restartedState).run(null);
        assertThat(restartedState.phase()).isEqualTo(ReleaseMigrationState.Phase.MIGRATING);
    }

    @Test void concurrentSingleUseConsumptionHasExactlyOneWinner() throws Exception {
        Fixture fixture = fixture("RUN-000012", NOW.minusSeconds(1), NOW.plusSeconds(60), identity());
        Path root = fixture.marker().getParent().getParent();
        var store = ReleaseMigrationAuthorizationStore.forTest(root, fixedClock());
        var authorization = store.preview();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Runnable attempt = () -> {
            try {
                start.await();
                store.consume(authorization, identity());
                accepted.incrementAndGet();
            } catch (java.nio.file.FileAlreadyExistsException expected) {
                rejected.incrementAndGet();
            } catch (Exception unexpected) {
                throw new RuntimeException(unexpected);
            }
        };
        Thread left = new Thread(attempt); Thread right = new Thread(attempt);
        left.start(); right.start(); start.countDown(); left.join(); right.join();
        assertThat(accepted.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
    }

    @Test void anyBoundIdentityDriftFailsBeforeConsumptionAndMigration() throws Exception {
        var drifted = new ReleaseMigrationAuthorization.ExecutionIdentity(SHA_B, SHA_F,
                DataMigrationOracleVerifier.DEPLOYMENT_DATABASE, UUID, SHA_B, SHA_C, SHA_D, SHA_E, SHA_F);
        Fixture fixture = fixture("RUN-000007", NOW.minusSeconds(1), NOW.plusSeconds(60), drifted);
        assertThatThrownBy(fixture.runner()::execute).hasMessage("MIGRATION_AUTHORIZATION_INVALID");
        assertThat(fixture.stages().targets).isEmpty();
        assertThat(Files.exists(fixture.marker())).isFalse();
    }

    @Test void nonzeroRetryConfigurationIsRejected() {
        assertThatThrownBy(() -> new ReleaseFlywayConfiguration().releaseFlyway(
                "jdbc:mysql://127.0.0.1:1/noop", "u", "p", "noop", "classpath:db/migration", 1, true, false))
                .isInstanceOf(IllegalStateException.class).hasMessage("FLYWAY_CONNECT_RETRIES_MUST_BE_ZERO");
    }

    @Test void expectedDatabaseIsTheFlywayDefaultSchema() {
        var flyway = new ReleaseFlywayConfiguration().releaseFlyway(
                "jdbc:mysql://127.0.0.1:1/noop", "u", "p", "noop", "classpath:db/migration", 0, true, false);

        assertThat(flyway.getConfiguration().getDefaultSchema()).isEqualTo("noop");
    }

    private Fixture fixture(String runId, Instant validFrom, Instant validUntil,
                            ReleaseMigrationAuthorization.ExecutionIdentity actualIdentity) throws Exception {
        return fixture(runId, validFrom, validUntil, actualIdentity,
                ReleaseMigrationAuthorization.StartState.PRE_V10,
                ReleaseMigrationAuthorization.AllowedTarget.V12_VIA_V11);
    }

    private Fixture fixture(String runId, Instant validFrom, Instant validUntil,
                            ReleaseMigrationAuthorization.ExecutionIdentity actualIdentity,
                            ReleaseMigrationAuthorization.StartState startState,
                            ReleaseMigrationAuthorization.AllowedTarget allowedTarget) throws Exception {
        Path root = temp.resolve(runId);
        Files.createDirectories(root);
        Path authorization = root.resolve("authorization.properties");
        Files.writeString(authorization, authorization(runId, validFrom, validUntil, startState, allowedTarget));
        FakeStages stages = new FakeStages();
        ReleaseMigrationState state = new ReleaseMigrationState();
        var store = ReleaseMigrationAuthorizationStore.forTest(root, fixedClock());
        var runner = new ReleaseFlywayMigrationRunner(stages, state, store, identityProvider(actualIdentity));
        return new Fixture(runner, stages, state, authorization, root.resolve("consumed").resolve(runId + ".consumed"));
    }

    private static ReleaseMigrationRuntimeIdentityProvider identityProvider(
            ReleaseMigrationAuthorization.ExecutionIdentity identity) throws Exception {
        ReleaseMigrationRuntimeIdentityProvider provider = mock(ReleaseMigrationRuntimeIdentityProvider.class);
        when(provider.current()).thenReturn(identity);
        return provider;
    }

    private static ReleaseMigrationAuthorization.ExecutionIdentity identity() {
        return new ReleaseMigrationAuthorization.ExecutionIdentity(SHA_A, SHA_F,
                DataMigrationOracleVerifier.DEPLOYMENT_DATABASE, UUID, SHA_B, SHA_C, SHA_D, SHA_E, SHA_F);
    }

    private static String authorization(String runId, Instant from, Instant until,
                                        ReleaseMigrationAuthorization.StartState startState,
                                        ReleaseMigrationAuthorization.AllowedTarget allowedTarget) {
        return String.join("\n", "authorizationRef=AUTH-0001", "runId=" + runId,
                "artifactSha256=" + SHA_A, "candidateManifestSha256=" + SHA_F,
                "databaseName=" + DataMigrationOracleVerifier.DEPLOYMENT_DATABASE,
                "expectedServerUuid=" + UUID, "grantSnapshotIdentity=" + SHA_B,
                "backupEvidenceSha256=" + SHA_C, "restoreEvidenceSha256=" + SHA_D,
                "oracleManifestSha256=" + SHA_E, "migrationInventorySha256=" + SHA_F,
                "startState=" + startState, "allowedTarget=" + allowedTarget,
                "validFrom=" + from, "validUntil=" + until) + "\n";
    }

    private static Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }

    private static org.mockito.MockedStatic<DataMigrationOracleVerifier> oracle(DataMigrationOracleVerifier.State... states) {
        var oracle = mockStatic(DataMigrationOracleVerifier.class);
        var sequence = oracle.when(() -> DataMigrationOracleVerifier.verify(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()));
        if (states.length == 1) sequence.thenReturn(states[0]);
        else if (states.length == 2) sequence.thenReturn(states[0], states[1]);
        else sequence.thenReturn(states[0], states[1], states[2]);
        return oracle;
    }

    private static void assertFailedAndSealed(Fixture fixture) throws Exception {
        assertThat(fixture.state().phase()).isEqualTo(ReleaseMigrationState.Phase.FAILED);
        assertThat(Files.readString(fixture.marker())).isEqualTo("FAILED\n");
    }

    private static final class FakeStages implements ReleaseFlywayMigrationRunner.StageExecutor {
        private final List<String> targets = new ArrayList<>();
        private final Connection connection = mock(Connection.class);
        private String failTarget;
        @Override public Connection openConnection() { return connection; }
        @Override public void migrateTo(String target) {
            targets.add(target);
            if (target.equals(failTarget)) throw new IllegalStateException("SYNTHETIC_TARGET_FAILURE");
        }
    }

    private record Fixture(ReleaseFlywayMigrationRunner runner, FakeStages stages,
                           ReleaseMigrationState state, Path authorization, Path marker) {}
}
