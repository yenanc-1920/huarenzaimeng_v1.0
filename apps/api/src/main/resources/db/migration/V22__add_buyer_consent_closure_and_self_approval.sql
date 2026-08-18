CREATE TABLE IF NOT EXISTS buyer_consent_state (
    buyer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    guest_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    consent_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_agreement_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    privacy_policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    accepted_at DATETIME(3) NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (buyer_id),
    UNIQUE KEY uk_buyer_consent_guest (guest_ref),
    CONSTRAINT fk_buyer_consent_state_identity FOREIGN KEY (buyer_id) REFERENCES buyer_identity (buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS buyer_consent_acceptance (
    acceptance_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    buyer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    guest_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    accepted_at DATETIME(3) NOT NULL,
    PRIMARY KEY (acceptance_ref),
    UNIQUE KEY uk_buyer_consent_policy_request (buyer_id,policy_type,policy_version,request_ref),
    KEY idx_buyer_consent_subject_time (subject_ref,accepted_at),
    CONSTRAINT fk_buyer_consent_acceptance_identity FOREIGN KEY (buyer_id) REFERENCES buyer_identity (buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS buyer_account_closure_request (
    closure_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    buyer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(500) NOT NULL,
    closure_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    blocker_count INT UNSIGNED NOT NULL DEFAULT 0,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    requested_at DATETIME(3) NOT NULL,
    closed_at DATETIME(3) DEFAULT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (closure_ref),
    UNIQUE KEY uk_buyer_closure_subject (buyer_id),
    UNIQUE KEY uk_buyer_closure_idempotency (idempotency_key),
    CONSTRAINT fk_buyer_closure_identity FOREIGN KEY (buyer_id) REFERENCES buyer_identity (buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS buyer_pii_cleanup_task (
    cleanup_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    closure_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    buyer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    lease_owner VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    lease_until DATETIME(3) DEFAULT NULL,
    last_error_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) DEFAULT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (cleanup_ref),
    UNIQUE KEY uk_buyer_pii_cleanup_closure (closure_ref),
    UNIQUE KEY uk_buyer_pii_cleanup_idempotency (idempotency_key),
    KEY idx_buyer_pii_cleanup_claim (task_state,next_attempt_at,lease_until),
    CONSTRAINT fk_buyer_pii_cleanup_closure FOREIGN KEY (closure_ref) REFERENCES buyer_account_closure_request (closure_ref),
    CONSTRAINT fk_buyer_pii_cleanup_identity FOREIGN KEY (buyer_id) REFERENCES buyer_identity (buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @v22_column_exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hz_content_review_task' AND column_name='self_approved');
SET @v22_ddl := IF(@v22_column_exists=0,'ALTER TABLE hz_content_review_task ADD COLUMN self_approved TINYINT(1) NOT NULL DEFAULT 0 AFTER decision_reason','SELECT 1');
PREPARE v22_statement FROM @v22_ddl;
EXECUTE v22_statement;
DEALLOCATE PREPARE v22_statement;

SET @v22_column_exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hz_content_review_task' AND column_name='exception_policy_version');
SET @v22_ddl := IF(@v22_column_exists=0,'ALTER TABLE hz_content_review_task ADD COLUMN exception_policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER self_approved','SELECT 1');
PREPARE v22_statement FROM @v22_ddl;
EXECUTE v22_statement;
DEALLOCATE PREPARE v22_statement;

SET @v22_column_exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hz_v1_admin_audit' AND column_name='self_approved');
SET @v22_ddl := IF(@v22_column_exists=0,'ALTER TABLE hz_v1_admin_audit ADD COLUMN self_approved TINYINT(1) NOT NULL DEFAULT 0 AFTER reason','SELECT 1');
PREPARE v22_statement FROM @v22_ddl;
EXECUTE v22_statement;
DEALLOCATE PREPARE v22_statement;

SET @v22_column_exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hz_v1_admin_audit' AND column_name='exception_policy_version');
SET @v22_ddl := IF(@v22_column_exists=0,'ALTER TABLE hz_v1_admin_audit ADD COLUMN exception_policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER self_approved','SELECT 1');
PREPARE v22_statement FROM @v22_ddl;
EXECUTE v22_statement;
DEALLOCATE PREPARE v22_statement;

SET @v22_column_exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hz_content_version_history' AND column_name='self_approved');
SET @v22_ddl := IF(@v22_column_exists=0,'ALTER TABLE hz_content_version_history ADD COLUMN self_approved TINYINT(1) NOT NULL DEFAULT 0 AFTER reason','SELECT 1');
PREPARE v22_statement FROM @v22_ddl;
EXECUTE v22_statement;
DEALLOCATE PREPARE v22_statement;

SET @v22_column_exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hz_content_version_history' AND column_name='exception_policy_version');
SET @v22_ddl := IF(@v22_column_exists=0,'ALTER TABLE hz_content_version_history ADD COLUMN exception_policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER self_approved','SELECT 1');
PREPARE v22_statement FROM @v22_ddl;
EXECUTE v22_statement;
DEALLOCATE PREPARE v22_statement;
