package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationAndConfigContractTest {

    @Test
    void v7ForeignKeysMatchV1ParentColumnDefinitions() throws IOException {
        String v1 = resource("db/migration/V1__create_core_transaction_tables.sql");
        String v7 = resource("db/migration/V7__add_order_detail_read_projection.sql");
        String expectedOrderRef = "order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL";
        String expectedQuoteRef = "quote_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL";

        assertThat(table(v1, "hz_order")).contains(expectedOrderRef);
        assertThat(table(v1, "hz_quote")).contains(expectedQuoteRef);
        assertThat(table(v7, "hz_order_detail_projection"))
                .contains(expectedOrderRef, expectedQuoteRef)
                .contains("FOREIGN KEY (order_ref) REFERENCES hz_order (order_ref)")
                .contains("FOREIGN KEY (quote_ref) REFERENCES hz_quote (quote_ref)");
    }

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
                .contains("test-access-token: ${HZ_TEST_ACCESS_TOKEN:}")
                .contains("default: mock")
                .contains("mode: in-memory")
                .contains("task-leasing-status: NOT_IMPLEMENTED");
        assertThat(mock).contains("FlywayAutoConfiguration")
                .contains("MybatisAutoConfiguration")
                .contains("enabled: false");
        assertThat(release).contains("mode: mysql")
                .contains("${HZ_DATASOURCE_URL}")
                .contains("${SPRING_FLYWAY_USER}")
                .contains("${SPRING_DATASOURCE_PASSWORD}")
                .contains("${SPRING_FLYWAY_PASSWORD}")
                .contains("test-access-token: ${HZ_TEST_ACCESS_TOKEN:}")
                .contains("content-token: ${HZ_CONTENT_ADMIN_TOKEN:}")
                .contains("enabled: false");
        String controlledFlyway = source("src/main/java/com/huarenzaimeng/api/config/ReleaseFlywayConfiguration.java");
        assertThat(controlledFlyway).contains("@Profile(\"release-mysql\")")
                .contains("@Value(\"${spring.flyway.user}\")")
                .contains("@Value(\"${spring.flyway.password}\")")
                .contains("Flyway.configure()")
                .contains(".dataSource(url, user, password)");
        String migrationRunner = source("src/main/java/com/huarenzaimeng/api/config/ReleaseFlywayMigrationRunner.java");
        assertThat(migrationRunner).contains("implements ApplicationRunner")
                .contains("flyway.migrate();")
                .doesNotContain("flyway.validate();")
                .contains("throw failure;");
        String explicitDataSource = source("src/main/java/com/huarenzaimeng/api/config/ReleaseMysqlDataSourceConfig.java");
        assertThat(explicitDataSource).contains("@Profile(\"release-mysql\")")
                .contains("@Value(\"${HZ_DATASOURCE_URL}\")")
                .contains("setJdbcUrl(jdbcUrl.strip())");
        String postProcessor = source("src/main/java/com/huarenzaimeng/api/config/CloudDatasourceEnvironmentPostProcessor.java");
        assertThat(postProcessor).contains("addFirst(new MapPropertySource")
                .contains("spring.datasource.url")
                .contains("spring.flyway.url")
                .contains("value omitted");
        assertThat(resource("META-INF/spring.factories"))
                .contains("CloudDatasourceEnvironmentPostProcessor");
        assertThat(config).doesNotContain("jdbc:mysql://")
                .doesNotContain("test-access-token: test-only-secret");
    }

    private static String resource(String path) throws IOException {
        try (var stream = MigrationAndConfigContractTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("missing test resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String source(String path) throws IOException {
        return java.nio.file.Files.readString(java.nio.file.Path.of(path), StandardCharsets.UTF_8);
    }

    private static String table(String sql, String name) {
        int start = sql.indexOf("CREATE TABLE " + name + " (");
        if (start < 0) throw new IllegalArgumentException("missing table: " + name);
        int end = sql.indexOf(";", start);
        if (end < 0) throw new IllegalArgumentException("unterminated table: " + name);
        return sql.substring(start, end);
    }
}
