package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMigrationContractTest {
    private static final Path V4 = Path.of("src/main/resources/db/migration/V4__add_versioned_operator_and_preset_catalog.sql");
    private static final Path RUNBOOK = Path.of("src/main/resources/db/migration/V4_CATALOG_FORWARD_RUNBOOK.md");

    @Test void v4IsAdditiveAfterContentV3AndContainsVersionedCatalogKeys() throws Exception {
        String sql = Files.readString(V4);
        assertThat(sql).contains("CREATE TABLE hz_operator_support_batch", "CREATE TABLE hz_operator_membership",
                "CREATE TABLE hz_product_catalog", "CREATE TABLE hz_catalog_item",
                "supported_operator_set_version BIGINT UNSIGNED NOT NULL",
                "catalog_version BIGINT UNSIGNED NOT NULL", "denomination_ref", "amount_minor BIGINT UNSIGNED",
                "ALTER TABLE hz_quote");
        assertThat(sql).doesNotContain("DROP TABLE", "TRUNCATE", "INSERT INTO hz_operator_membership");
        assertThat(V4.getFileName().toString()).startsWith("V4__");
        assertThat(Files.readString(RUNBOOK)).contains("V1→V2→V3", "STATIC_CONTRACT_ONLY/NOT_RUN",
                "PENDING_OPERATOR_BATCH_APPROVAL", "AuthorizationRef");
    }
}
