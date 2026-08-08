package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContentMigrationContractTest {
    @Test
    void mysql57MigrationKeepsContentEvidenceVersionAuditAndIdempotencyExplicit() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/"
                + "V3__add_self_operated_directory_content.sql"));

        assertThat(sql).contains("CREATE TABLE hz_content_item", "ENGINE=InnoDB", "DEFAULT CHARSET=utf8mb4",
                "ownership_mode", "source_ref", "verification_scope", "verified_at", "valid_until",
                "aggregate_version", "complaint_pending", "CREATE TABLE hz_content_command",
                "UNIQUE KEY uk_hz_content_command_idempotency", "CREATE TABLE hz_content_audit",
                "UNIQUE KEY uk_hz_content_audit_version");
        assertThat(sql).doesNotContain("TIMESTAMP WITH TIME ZONE", "GENERATED ALWAYS AS IDENTITY", "JSON_TABLE");
    }
}
