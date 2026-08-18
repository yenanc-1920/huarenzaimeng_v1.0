CREATE TABLE hz_provider_limit (
    limit_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    channel_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operator_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    max_inflight_count BIGINT UNSIGNED NOT NULL,
    max_inflight_minor BIGINT UNSIGNED NOT NULL,
    max_single_minor BIGINT UNSIGNED NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    reserved_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reserved_minor BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (limit_ref),
    UNIQUE KEY uk_hz_provider_limit_scope (provider_code,channel_ref,operator_code,currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE hz_provider_balance_position
    ADD COLUMN active_reserved_minor BIGINT UNSIGNED NOT NULL DEFAULT 0;

ALTER TABLE hz_topup_coordination
    ADD COLUMN scope_provider_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN scope_channel_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN scope_operator_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN supplier_cost DECIMAL(19,4) NULL;

CREATE TABLE hz_provider_exposure (
    exposure_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    channel_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operator_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount_minor BIGINT UNSIGNED NOT NULL,
    exposure_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    limit_version BIGINT UNSIGNED NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (exposure_ref),
    UNIQUE KEY uk_hz_provider_exposure_order (merchant_order_ref),
    KEY idx_hz_provider_exposure_scope (provider_code,channel_ref,operator_code,currency,exposure_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
