CREATE TABLE buyer_login_attempt (
    attempt_ref CHAR(42) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    environment_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    app_id_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_code VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL,
    evidence_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL,
    created_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL DEFAULT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (attempt_ref),
    UNIQUE KEY uk_buyer_login_attempt_code (environment_code, app_id_ref, code_digest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE buyer_session
    ADD COLUMN idle_expires_at DATETIME(3) NULL AFTER expires_at,
    ADD COLUMN aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1 AFTER idle_expires_at,
    MODIFY COLUMN last_seen_at TIMESTAMP(3) NULL DEFAULT NULL,
    ADD KEY idx_buyer_session_active_expiry (token_digest, status_code, expires_at, idle_expires_at);
