CREATE TABLE hz_operator_support_batch (
    supported_operator_set_version BIGINT UNSIGNED NOT NULL,
    batch_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approval_ref VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    batch_state VARCHAR(24) NOT NULL,
    qualification_known TINYINT(1) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    rollback_version BIGINT UNSIGNED DEFAULT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (supported_operator_set_version),
    UNIQUE KEY uk_hz_operator_batch_ref (batch_ref),
    KEY idx_hz_operator_batch_active (batch_state, effective_from, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_operator_membership (
    supported_operator_set_version BIGINT UNSIGNED NOT NULL,
    operator_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    membership_state VARCHAR(24) NOT NULL,
    evidence_ref VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (supported_operator_set_version, operator_code),
    KEY idx_hz_operator_membership_state (supported_operator_set_version, membership_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_product_catalog (
    catalog_version BIGINT UNSIGNED NOT NULL,
    supported_operator_set_version BIGINT UNSIGNED NOT NULL,
    catalog_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approval_ref VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    catalog_state VARCHAR(24) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (catalog_version),
    UNIQUE KEY uk_hz_product_catalog_ref (catalog_ref),
    KEY idx_hz_product_catalog_active (supported_operator_set_version, catalog_state, effective_from, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_catalog_item (
    catalog_version BIGINT UNSIGNED NOT NULL,
    operator_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    product_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    denomination_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_kind VARCHAR(32) NOT NULL,
    amount_minor BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_state VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (catalog_version, product_ref, denomination_ref),
    KEY idx_hz_catalog_item_operator (catalog_version, operator_code, item_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE hz_quote
    ADD COLUMN denomination_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER product_code,
    ADD COLUMN supported_operator_set_version BIGINT UNSIGNED DEFAULT NULL AFTER denomination_ref,
    ADD COLUMN catalog_version BIGINT UNSIGNED DEFAULT NULL AFTER supported_operator_set_version,
    ADD KEY idx_hz_quote_catalog_versions (supported_operator_set_version, catalog_version);
