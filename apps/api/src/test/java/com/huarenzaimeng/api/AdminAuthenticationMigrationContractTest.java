package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthenticationMigrationContractTest {
    @Test void v8KeepsPasswordSessionAndAuditSecretsSeparated() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V8__add_admin_authentication.sql"));
        assertThat(sql).contains("CREATE TABLE admin_user", "password_hash VARCHAR(100) NOT NULL")
                .contains("UNIQUE KEY uk_admin_user_username")
                .contains("failed_login_count INT UNSIGNED NOT NULL DEFAULT 0", "locked_until TIMESTAMP(3) NULL")
                .contains("CREATE TABLE admin_session", "token_digest CHAR(64) NOT NULL")
                .contains("UNIQUE KEY uk_admin_session_token_digest")
                .contains("CREATE TABLE admin_auth_audit", "username_fingerprint CHAR(64) NULL")
                .doesNotContain("password VARCHAR", "plain_password", "access_token")
                // CynosDB/MySQL 5.7 accepts only anonymous CHECK syntax and does not
                // enforce CHECK constraints. Role and status values are therefore
                // written through the application's fixed allowlist, not a MySQL 8
                // named-CHECK clause that prevents the migration from parsing.
                .doesNotContain("CONSTRAINT chk_admin_user_role", "CONSTRAINT chk_admin_user_status")
                // MySQL 5.7 with explicit_defaults_for_timestamp disabled otherwise
                // synthesizes a zero-date default, which strict mode rejects.
                .doesNotContain("TIMESTAMP(3) NOT NULL,", "TIMESTAMP(3) NULL,");
    }
}
