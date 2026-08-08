CREATE TABLE hz_content_item (
    content_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    category VARCHAR(64) NOT NULL,
    ownership_mode VARCHAR(48) NOT NULL,
    source_category VARCHAR(64) DEFAULT NULL,
    source_ref VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    verification_scope VARCHAR(500) DEFAULT NULL,
    verified_by VARCHAR(128) DEFAULT NULL,
    verified_at DATETIME(3) DEFAULT NULL,
    valid_until DATETIME(3) DEFAULT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    content_state VARCHAR(32) NOT NULL,
    complaint_pending TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (content_ref),
    KEY idx_hz_content_public (content_state, complaint_pending, valid_until, content_ref),
    CONSTRAINT chk_hz_content_self_operated CHECK (ownership_mode = 'SELF_OPERATED_CHINA_COMPANY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_content_command (
    command_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    canonical_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (command_id),
    UNIQUE KEY uk_hz_content_command_idempotency (idempotency_key),
    KEY idx_hz_content_command_ref (content_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_content_audit (
    audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    content_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    actor_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(500) NOT NULL,
    before_version BIGINT UNSIGNED NOT NULL,
    after_version BIGINT UNSIGNED NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  scope VARCHAR(96) NOT NULL,
  authorization_ref VARCHAR(128) NOT NULL,
  result VARCHAR(32) NOT NULL,
  evidence_ref VARCHAR(160) NOT NULL,
    PRIMARY KEY (audit_id),
    UNIQUE KEY uk_hz_content_audit_version (content_ref, after_version),
    KEY idx_hz_content_audit_ref (content_ref, audit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
