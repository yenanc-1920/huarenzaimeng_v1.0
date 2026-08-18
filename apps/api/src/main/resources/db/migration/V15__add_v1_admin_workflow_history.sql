SET @v15_column_exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hz_customer_case' AND column_name='aggregate_version');
SET @v15_ddl := IF(@v15_column_exists=0,'ALTER TABLE hz_customer_case ADD COLUMN aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1 AFTER data_origin','SELECT 1');
PREPARE v15_statement FROM @v15_ddl;
EXECUTE v15_statement;
DEALLOCATE PREPARE v15_statement;

SET @v15_column_exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hz_reconciliation_case' AND column_name='aggregate_version');
SET @v15_ddl := IF(@v15_column_exists=0,'ALTER TABLE hz_reconciliation_case ADD COLUMN aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1 AFTER data_origin','SELECT 1');
PREPARE v15_statement FROM @v15_ddl;
EXECUTE v15_statement;
DEALLOCATE PREPARE v15_statement;

SET @v15_column_exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hz_price_version' AND column_name='supplier_source_ref');
SET @v15_ddl := IF(@v15_column_exists=0,'ALTER TABLE hz_price_version ADD COLUMN supplier_source_ref VARCHAR(196) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER settlement_currency','SELECT 1');
PREPARE v15_statement FROM @v15_ddl;
EXECUTE v15_statement;
DEALLOCATE PREPARE v15_statement;

CREATE TABLE IF NOT EXISTS hz_customer_case_event (
    event_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    case_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    note_text VARCHAR(1000) NOT NULL,
    evidence_ref VARCHAR(160) DEFAULT NULL,
    from_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    to_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_ref VARCHAR(96) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (event_ref),
    UNIQUE KEY uk_hz_customer_case_event_idempotency (idempotency_key),
    KEY idx_hz_customer_case_event_history (case_ref,created_at),
    CONSTRAINT fk_hz_customer_case_event_case FOREIGN KEY (case_ref) REFERENCES hz_customer_case (case_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_reconciliation_event (
    event_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reconciliation_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_ref VARCHAR(160) DEFAULT NULL,
    note_text VARCHAR(1000) NOT NULL,
    from_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    to_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_ref VARCHAR(96) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (event_ref),
    UNIQUE KEY uk_hz_reconciliation_event_idempotency (idempotency_key),
    KEY idx_hz_reconciliation_event_history (reconciliation_ref,created_at),
    CONSTRAINT fk_hz_reconciliation_event_case FOREIGN KEY (reconciliation_ref) REFERENCES hz_reconciliation_case (reconciliation_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_content_review_task (
    review_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_version BIGINT UNSIGNED NOT NULL,
    submitter_ref VARCHAR(96) NOT NULL,
    priority_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    review_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reviewer_ref VARCHAR(96) DEFAULT NULL,
    decision_idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    decision_request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    decision_reason VARCHAR(500) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL,
    decided_at DATETIME(3) DEFAULT NULL,
    PRIMARY KEY (review_ref),
    UNIQUE KEY uk_hz_content_review_idempotency (idempotency_key),
    UNIQUE KEY uk_hz_content_review_decision_idempotency (decision_idempotency_key),
    UNIQUE KEY uk_hz_content_review_version (object_type,object_ref,object_version),
    KEY idx_hz_content_review_queue (review_state,priority_code,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_content_version_history (
    history_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_version BIGINT UNSIGNED NOT NULL,
    snapshot_json TEXT NOT NULL,
    action_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_ref VARCHAR(96) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (history_ref),
    UNIQUE KEY uk_hz_content_history_version (object_type,object_ref,object_version),
    KEY idx_hz_content_history_object (object_type,object_ref,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_supplier_catalog_batch_snapshot (
    batch_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_count INT UNSIGNED NOT NULL,
    batch_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    captured_at DATETIME(3) NOT NULL,
    actor_ref VARCHAR(96) NOT NULL,
    PRIMARY KEY (batch_ref),
    UNIQUE KEY uk_hz_supplier_catalog_digest (provider_code,source_digest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_supplier_catalog_item_snapshot (
    batch_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_sku VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    raw_name VARCHAR(255) NOT NULL,
    raw_benefit_text VARCHAR(500) NOT NULL,
    supplier_cost DECIMAL(19,4) NOT NULL,
    settlement_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    availability VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    normalized_operator VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    mapping_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mapping_failure_reason VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (batch_ref,provider_sku),
    CONSTRAINT fk_hz_supplier_catalog_item_batch FOREIGN KEY (batch_ref) REFERENCES hz_supplier_catalog_batch_snapshot (batch_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_fx_rate_snapshot (
    fx_snapshot_ref VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quote_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    rate_value DECIMAL(19,8) NOT NULL,
    conversion_path VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    observed_at DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NOT NULL,
    source_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (fx_snapshot_ref),
    UNIQUE KEY uk_hz_fx_rate_observation (source_code,base_currency,quote_currency,observed_at),
    KEY idx_hz_fx_rate_current (base_currency,quote_currency,valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
