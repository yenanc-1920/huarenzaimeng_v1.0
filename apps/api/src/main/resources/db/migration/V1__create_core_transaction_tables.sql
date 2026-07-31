CREATE TABLE hz_quote (
    quote_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    phone_masked VARCHAR(32) NOT NULL,
    operator_code VARCHAR(64) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    mnp_state VARCHAR(32) NOT NULL,
    total_amount DECIMAL(19, 4) NOT NULL,
    total_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    price_snapshot JSON NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (quote_ref),
    KEY idx_hz_quote_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_order (
    order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quote_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_state VARCHAR(32) NOT NULL,
    payment_state VARCHAR(32) NOT NULL,
    upstream_debit_state VARCHAR(32) NOT NULL,
    delivery_state VARCHAR(32) NOT NULL,
    refund_state VARCHAR(32) NOT NULL,
    projection_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    allowed_action VARCHAR(64) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (order_ref),
    UNIQUE KEY uk_hz_order_quote_ref (quote_ref),
    KEY idx_hz_order_updated_at (updated_at),
    CONSTRAINT fk_hz_order_quote FOREIGN KEY (quote_ref) REFERENCES hz_quote (quote_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_semantic_action (
    semantic_action_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    case_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action_kind VARCHAR(48) NOT NULL,
    approved_branch VARCHAR(48) NOT NULL,
    action_state VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (semantic_action_key),
    UNIQUE KEY uk_hz_semantic_action_case (case_key, action_kind, approved_branch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_external_fact (
    external_fact_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    case_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fact_type VARCHAR(48) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_fact_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(19, 4) DEFAULT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    evidence_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    observed_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (external_fact_id),
    UNIQUE KEY uk_hz_external_fact_provider_ref (provider, provider_fact_ref),
    KEY idx_hz_external_fact_case_key (case_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_ledger_entry (
    ledger_entry_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    entry_set_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    case_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (ledger_entry_id),
    KEY idx_hz_ledger_entry_set_ref (entry_set_ref),
    KEY idx_hz_ledger_case_key (case_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
