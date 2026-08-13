package com.huarenzaimeng.api.config;

import com.huarenzaimeng.api.DataMigrationOracleVerifier;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ReleaseFlywayMigrationRunnerTest {
    private static final String UUID = "e70767e4-9719-11f1-9bfa-02505527b2b0";
    @Test void pre_then_post_opens_gate() throws Exception {
        var fixture = fixture();
        try (var oracle = mockStatic(DataMigrationOracleVerifier.class)) {
            oracle.when(() -> DataMigrationOracleVerifier.verify(
                    fixture.connection(), DataMigrationOracleVerifier.DEPLOYMENT_DATABASE, UUID))
                    .thenReturn(DataMigrationOracleVerifier.State.PRE_V10, DataMigrationOracleVerifier.State.POST_V12);
            new ReleaseFlywayMigrationRunner(fixture.flyway(), fixture.state(),
                    DataMigrationOracleVerifier.DEPLOYMENT_DATABASE, UUID).run(new DefaultApplicationArguments());
        }
        inOrder(fixture.flyway()).verify(fixture.flyway()).migrate();
        assertThat(fixture.state().phase()).isEqualTo(ReleaseMigrationState.Phase.READY);
    }

    @Test void mid_v11_never_opens_gate() throws Exception {
        var fixture = fixture();
        try (var oracle = mockStatic(DataMigrationOracleVerifier.class)) {
            oracle.when(() -> DataMigrationOracleVerifier.verify(
                    fixture.connection(), DataMigrationOracleVerifier.DEPLOYMENT_DATABASE, UUID))
                    .thenReturn(DataMigrationOracleVerifier.State.PRE_V10, DataMigrationOracleVerifier.State.MID_V11);
            assertThatThrownBy(() -> new ReleaseFlywayMigrationRunner(fixture.flyway(), fixture.state(),
                    DataMigrationOracleVerifier.DEPLOYMENT_DATABASE, UUID).run(new DefaultApplicationArguments()))
                    .hasMessage("MIGRATION_POST_ORACLE_MISMATCH");
        }
        assertThat(fixture.state().phase()).isEqualTo(ReleaseMigrationState.Phase.FAILED);
    }

    @Test void wrong_expected_database_is_rejected_before_connection() {
        assertThatThrownBy(() -> new ReleaseFlywayMigrationRunner(mock(Flyway.class),
                new ReleaseMigrationState(), "other_database", UUID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EXPECTED_DATABASE_NAME_INVALID");
    }

    @Test void missing_expected_server_uuid_is_rejected_before_connection() {
        assertThatThrownBy(() -> new ReleaseFlywayMigrationRunner(mock(Flyway.class),
                new ReleaseMigrationState(), DataMigrationOracleVerifier.DEPLOYMENT_DATABASE, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EXPECTED_SERVER_UUID_REQUIRED");
    }

    @Test void nonzero_retry_configuration_is_rejected() {
        assertThatThrownBy(() -> new ReleaseFlywayConfiguration().releaseFlyway(
                "jdbc:mysql://127.0.0.1:1/noop", "u", "p", "classpath:db/migration", 1, true, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FLYWAY_CONNECT_RETRIES_MUST_BE_ZERO");
    }

    @Test void extra_order_foreign_key_is_oracle_drift() throws Exception {
        var method = DataMigrationOracleVerifier.class.getDeclaredMethod("classify", List.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var lines = (List<String>) java.nio.file.Files.readAllLines(java.nio.file.Path.of(
                "src/test/resources/data-migration-oracle/POST_V12.txt")).stream()
                .filter(line -> !line.startsWith("PHASE|") && !line.startsWith("TARGET|")
                        && !line.startsWith("PAYLOAD_SHA256|"))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        lines.add("F|V8:hz_order|V8:extra_fk|V1:1|V9:order_ref|V8:hz_quote|V9:quote_ref|V8:RESTRICT|V8:RESTRICT");
        assertThat(method.invoke(null, lines)).isEqualTo(DataMigrationOracleVerifier.State.NO_GO_PARTIAL_OR_DRIFT);
    }

    private static Fixture fixture() throws Exception {
        Flyway flyway = mock(Flyway.class);
        var configuration = mock(org.flywaydb.core.api.configuration.Configuration.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(flyway.getConfiguration()).thenReturn(configuration);
        when(configuration.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        return new Fixture(flyway, connection, new ReleaseMigrationState());
    }

    private record Fixture(Flyway flyway, Connection connection, ReleaseMigrationState state) {}
}
