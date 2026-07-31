ALTER TABLE hz_quote
    ADD COLUMN project_subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER quote_ref,
    ADD COLUMN total_amount_minor BIGINT NULL AFTER total_amount,
    ADD KEY idx_hz_quote_subject_ref (project_subject_ref, quote_ref);

UPDATE hz_quote
SET total_amount_minor = CAST(ROUND(total_amount * 100) AS SIGNED)
WHERE total_amount_minor IS NULL;

ALTER TABLE hz_quote
    MODIFY COLUMN total_amount_minor BIGINT NOT NULL;

ALTER TABLE hz_order
    ADD COLUMN project_subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER order_ref,
    ADD COLUMN aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1 AFTER projection_version,
    ADD KEY idx_hz_order_subject_ref (project_subject_ref, order_ref);

CREATE TABLE hz_command (
    project_subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    command_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    endpoint_scope VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_scope VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    semantic_action_key VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    canonical_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    command_state VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (project_subject_ref, command_id),
    UNIQUE KEY uk_hz_command_subject_idempotency
      (project_subject_ref, endpoint_scope, resource_scope, idempotency_key),
    UNIQUE KEY uk_hz_command_subject_semantic (project_subject_ref, semantic_action_key),
    KEY idx_hz_command_resource_ref (project_subject_ref, resource_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_outbox (
    outbox_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_key VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    event_state VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    published_at DATETIME(3) DEFAULT NULL,
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_hz_outbox_event_key (event_key),
    KEY idx_hz_outbox_state_id (event_state, outbox_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_task (
    task_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_key VARCHAR(192) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    task_state VARCHAR(32) NOT NULL,
    payload_json JSON NOT NULL,
    available_at DATETIME(3) NOT NULL,
    lease_owner VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    lease_until DATETIME(3) DEFAULT NULL,
    fencing_token BIGINT UNSIGNED NOT NULL DEFAULT 0,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_hz_task_key (task_key),
    KEY idx_hz_task_claim (task_state, available_at, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
