package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BuyerAuthenticationMigrationContractTest {
    private static final String MIGRATION = "db/migration/V9__add_buyer_authentication.sql";
    private static final String V10 = "db/migration/V10__add_code2session_attempt_and_idle_expiry.sql";

    @Test void definesIdentitySessionAndAuditWithoutPlainWechatIdentifiers() throws IOException {
        String sql = resource();
        assertThat(sql).contains("CREATE TABLE buyer_identity", "CREATE TABLE buyer_session",
                "CREATE TABLE buyer_auth_audit", "uk_buyer_identity_provider_subject",
                "fk_buyer_session_identity", "subject_fingerprint", "token_digest CHAR(64)",
                "session_fingerprint CHAR(64)");
        assertThat(sql.toLowerCase()).doesNotContain("openid", "unionid", "session_key");
    }

    @Test void fixesMysql57TimestampDefaultsAndSessionExpiryIndex() throws IOException {
        String sql = resource();
        assertThat(sql).contains("issued_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)",
                "expires_at DATETIME(3) NOT NULL",
                "revoked_at TIMESTAMP(3) NULL DEFAULT NULL",
                "idx_buyer_session_buyer_status (buyer_id, status_code, expires_at)");
        assertThat(sql).doesNotContain("CHECK (");
        assertThat(sql).doesNotContain("session_ref", "expires_at TIMESTAMP", "expires_at DATETIME(3) NOT NULL DEFAULT");
    }

    @Test void v10AddsAtomicCodeDigestAndIdleExpiryWithoutRawSecretsOrMysql8Syntax() throws IOException {
        String sql=resource(V10),lower=sql.toLowerCase();
        assertThat(sql).contains("CREATE TABLE buyer_login_attempt","uk_buyer_login_attempt_code (environment_code, app_id_ref, code_digest)",
                "code_digest CHAR(64)","ADD COLUMN idle_expires_at DATETIME(3)","aggregate_version BIGINT UNSIGNED");
        assertThat(lower).doesNotContain("openid","unionid","session_key","app_secret"," js_code","check (");
    }

    private static String resource() throws IOException {return resource(MIGRATION);}
    private static String resource(String name) throws IOException {
        try (var stream = BuyerAuthenticationMigrationContractTest.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IOException("missing " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
