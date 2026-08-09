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
                .doesNotContain("password VARCHAR", "plain_password", "access_token");
    }
}
