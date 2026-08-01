package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationAndConfigContractTest {

    @Test
    void v2BackfillsExistingAmountsBeforeMakingMinorAmountRequired() throws IOException {
        String sql = resource("db/migration/V2__add_subject_scoped_commands_versions_and_worker_tables.sql");

        int addNullable = sql.indexOf("ADD COLUMN total_amount_minor BIGINT NULL");
        int backfill = sql.indexOf("UPDATE hz_quote");
        int useExistingAmount = sql.indexOf("total_amount * 100");
        int makeRequired = sql.indexOf("MODIFY COLUMN total_amount_minor BIGINT NOT NULL");

        assertThat(addNullable).isGreaterThanOrEqualTo(0);
        assertThat(backfill).isGreaterThan(addNullable);
        assertThat(useExistingAmount).isGreaterThan(backfill);
        assertThat(makeRequired).isGreaterThan(useExistingAmount);
    }

    @Test
    void v2KeepsCommandOutboxAndTaskUniquenessAndFencing() throws IOException {
        String sql = resource("db/migration/V2__add_subject_scoped_commands_versions_and_worker_tables.sql");

        assertThat(sql).contains("PRIMARY KEY (project_subject_ref, command_id)")
                .contains("UNIQUE KEY uk_hz_command_subject_idempotency")
                .contains("UNIQUE KEY uk_hz_command_subject_semantic")
                .contains("canonical_fingerprint CHAR(64)")
                .contains("UNIQUE KEY uk_hz_outbox_event_key (event_key)")
                .contains("UNIQUE KEY uk_hz_task_key (task_key)")
                .contains("fencing_token BIGINT UNSIGNED NOT NULL DEFAULT 0");
    }

    @Test
    void v2RunbookKeepsMigrationAndTaskLeasingNotRun() throws IOException {
        String runbook = resource("db/migration/V2_FORWARD_RUNBOOK.md");
        assertThat(runbook).contains("NOT_RUN")
                .contains("均为空")
                .contains("NOT_IMPLEMENTED")
                .contains("release-mysql");
    }

    @Test
    void defaultConfigurationKeepsExternalBusinessNetworkAndRealAdaptersClosed() throws IOException {
        String config = resource("application.yml");
        String mock = resource("application-mock.yml");
        String release = resource("application-release-mysql.yml");

        assertThat(config).contains("external-network: DENY_ALL")
                .contains("real-adapters-enabled: false")
                .contains("default: mock")
                .contains("mode: in-memory")
                .contains("task-leasing-status: NOT_IMPLEMENTED");
        assertThat(mock).contains("FlywayAutoConfiguration")
                .contains("MybatisAutoConfiguration")
                .contains("enabled: false");
        assertThat(release).contains("mode: mysql")
                .contains("${HZ_DATASOURCE_URL}")
                .contains("${SPRING_DATASOURCE_PASSWORD}")
                .contains("${SPRING_FLYWAY_PASSWORD}")
                .contains("enabled: true");
        assertThat(config).doesNotContain("jdbc:mysql://")
                .doesNotContain("token:");
    }

    private static String resource(String path) throws IOException {
        try (var stream = MigrationAndConfigContractTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("missing test resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
