CREATE TABLE buyer_identity (
    buyer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_appid_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_subject_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (buyer_id),
    UNIQUE KEY uk_buyer_identity_subject_ref (subject_ref),
    UNIQUE KEY uk_buyer_identity_provider_subject
      (provider_code, provider_appid_digest, provider_subject_digest),
    KEY idx_buyer_identity_status (status_code, buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE buyer_session (
    session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    buyer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issued_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3) NOT NULL,
    last_seen_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    revoked_at TIMESTAMP(3) NULL DEFAULT NULL,
    PRIMARY KEY (session_id),
    UNIQUE KEY uk_buyer_session_token_digest (token_digest),
    KEY idx_buyer_session_buyer_status (buyer_id, status_code, expires_at),
    CONSTRAINT fk_buyer_session_identity FOREIGN KEY (buyer_id)
      REFERENCES buyer_identity (buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE buyer_auth_audit (
    audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_code VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL,
    session_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL,
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL,
    occurred_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (audit_id),
    KEY idx_buyer_auth_audit_event_time (event_code, occurred_at),
    KEY idx_buyer_auth_audit_subject_time (subject_fingerprint, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
