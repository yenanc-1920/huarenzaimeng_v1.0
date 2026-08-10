package com.huarenzaimeng.api.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseFlywayRealMigrationTest {
    @TempDir Path temp;

    @Test void appliedV1ToV7WithPendingV8MigratesV8AndOpensGate() throws Exception {
        Path firstSeven = Files.createDirectory(temp.resolve("first-seven"));
        Path allEight = Files.createDirectory(temp.resolve("all-eight"));
        for (int version = 1; version <= 8; version++) {
            String sql = "CREATE TABLE release_probe_v" + version + " (id INT PRIMARY KEY);";
            Path target = allEight.resolve("V" + version + "__probe_" + version + ".sql");
            Files.writeString(target, sql);
            if (version <= 7) Files.writeString(firstSeven.resolve(target.getFileName()), sql);
        }
        String jdbcUrl = "jdbc:h2:mem:pending_v8;DB_CLOSE_DELAY=-1";
        flyway(jdbcUrl, firstSeven).migrate();
        ReleaseMigrationState state = new ReleaseMigrationState();

        new ReleaseFlywayMigrationRunner(flyway(jdbcUrl, allEight), state)
                .run(new DefaultApplicationArguments());

        assertThat(state.phase()).isEqualTo(ReleaseMigrationState.Phase.READY);
        Flyway verification = flyway(jdbcUrl, allEight);
        assertThat(verification.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(verification.info().pending()).isEmpty();
    }

    @Test void checksumMismatchFailsMigrationAndKeepsGateClosed() throws Exception {
        Path applied = Files.createDirectory(temp.resolve("applied"));
        Path drifted = Files.createDirectory(temp.resolve("drifted"));
        Files.writeString(applied.resolve("V1__baseline.sql"), "CREATE TABLE checksum_probe (id INT PRIMARY KEY);");
        Files.writeString(drifted.resolve("V1__baseline.sql"),
                "CREATE TABLE checksum_probe (id INT PRIMARY KEY, changed_col INT);");
        String jdbcUrl = "jdbc:h2:mem:checksum_mismatch;DB_CLOSE_DELAY=-1";
        flyway(jdbcUrl, applied).migrate();
        ReleaseMigrationState state = new ReleaseMigrationState();

        assertThatThrownBy(() -> new ReleaseFlywayMigrationRunner(flyway(jdbcUrl, drifted), state)
                .run(new DefaultApplicationArguments()))
                .hasMessageContaining("checksum");
        assertThat(state.phase()).isEqualTo(ReleaseMigrationState.Phase.FAILED);
        assertThat(state.isReady()).isFalse();
    }

    private static Flyway flyway(String jdbcUrl, Path locations) {
        return Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("filesystem:" + locations.toAbsolutePath())
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .load();
    }
}
