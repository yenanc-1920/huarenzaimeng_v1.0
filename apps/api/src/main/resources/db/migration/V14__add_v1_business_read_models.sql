CREATE TABLE hz_city (
    city_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    country_code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(96) NOT NULL,
    local_name VARCHAR(96) NOT NULL,
    timezone_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    city_state VARCHAR(24) NOT NULL,
    sort_order INT NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (city_code),
    KEY idx_hz_city_public (country_code, city_state, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_directory_entry (
    entry_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    city_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    category_code VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    local_address VARCHAR(500) NOT NULL,
    phone VARCHAR(48) NOT NULL,
    source_label VARCHAR(160) NOT NULL,
    verified_at DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NOT NULL,
    publish_state VARCHAR(24) NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (entry_ref),
    KEY idx_hz_directory_public (city_code, category_code, publish_state, valid_until),
    CONSTRAINT fk_hz_directory_city FOREIGN KEY (city_code) REFERENCES hz_city (city_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_holiday_rule (
    rule_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    country_code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    rule_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    start_date DATE DEFAULT NULL,
    end_date DATE DEFAULT NULL,
    weekend_days VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    source_label VARCHAR(160) NOT NULL,
    publish_state VARCHAR(24) NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    effective_from DATETIME(3) NOT NULL,
    effective_until DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (rule_ref),
    KEY idx_hz_holiday_effective (country_code, publish_state, effective_from, effective_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_news_article (
    article_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    category_code VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    body_text TEXT NOT NULL,
    source_label VARCHAR(160) NOT NULL,
    author_name VARCHAR(96) NOT NULL,
    publish_state VARCHAR(24) NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    published_at DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (article_ref),
    KEY idx_hz_news_public (category_code, publish_state, published_at, valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_platform_product (
    platform_product_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    country_code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'BD',
    operator_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    product_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    benefit_text VARCHAR(500) NOT NULL,
    denomination_bdt DECIMAL(19,2) DEFAULT NULL,
    data_allowance_mb BIGINT UNSIGNED DEFAULT NULL,
    voice_minutes INT UNSIGNED DEFAULT NULL,
    sms_count INT UNSIGNED DEFAULT NULL,
    validity_text VARCHAR(96) DEFAULT NULL,
    provider_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_sku VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    catalog_batch_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    raw_sku_name VARCHAR(255) DEFAULT NULL,
    raw_benefit_text VARCHAR(500) DEFAULT NULL,
    supplier_cost DECIMAL(19,4) DEFAULT NULL,
    settlement_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    supplier_availability VARCHAR(24) DEFAULT NULL,
    catalog_synced_at DATETIME(3) DEFAULT NULL,
    normalized_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    normalized_operator VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    mapping_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    mapping_failure_reason VARCHAR(500) DEFAULT NULL,
    channel_priority INT NOT NULL DEFAULT 100,
    phone_rule VARCHAR(160) DEFAULT NULL,
    sale_start_at DATETIME(3) DEFAULT NULL,
    sale_end_at DATETIME(3) DEFAULT NULL,
    enable_state VARCHAR(24) NOT NULL,
    source_mode VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (platform_product_ref),
    UNIQUE KEY uk_hz_platform_provider_sku (provider_code, provider_sku),
    KEY idx_hz_platform_product_public (operator_code, product_type, enable_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_provider_channel (
    channel_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    channel_priority INT NOT NULL,
    channel_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (channel_ref),
    UNIQUE KEY uk_hz_provider_channel_code (provider_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_price_version (
    price_version_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    platform_product_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    final_amount_cny DECIMAL(19,2) NOT NULL,
    supplier_cost DECIMAL(19,4) DEFAULT NULL,
    settlement_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    fx_source VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fx_snapshot_ref VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fx_direction VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    fx_rate DECIMAL(19,8) DEFAULT NULL,
    fx_updated_at DATETIME(3) DEFAULT NULL,
    fx_valid_until DATETIME(3) DEFAULT NULL,
    buffer_rate DECIMAL(9,6) DEFAULT NULL,
    markup_rate DECIMAL(9,6) DEFAULT NULL,
    wechat_fee_rate DECIMAL(9,6) DEFAULT NULL,
    tax_rate DECIMAL(9,6) DEFAULT NULL,
    minimum_margin_rate DECIMAL(9,6) DEFAULT NULL,
    rounding_rule VARCHAR(32) DEFAULT NULL,
    promotion_bearer VARCHAR(32) DEFAULT NULL,
    pricing_scope VARCHAR(64) DEFAULT NULL,
    price_state VARCHAR(24) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_until DATETIME(3) NOT NULL,
    enabled_by VARCHAR(96) NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (price_version_ref),
    KEY idx_hz_price_current (platform_product_ref, price_state, effective_from, effective_until),
    CONSTRAINT fk_hz_price_product FOREIGN KEY (platform_product_ref) REFERENCES hz_platform_product (platform_product_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_customer_case (
    case_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issue_type VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    related_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    priority_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_ref VARCHAR(96) DEFAULT NULL,
    case_state VARCHAR(24) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    data_origin VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (case_ref),
    KEY idx_hz_customer_case_queue (case_state, priority_code, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_reconciliation_case (
    reconciliation_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    difference_type VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    case_state VARCHAR(24) NOT NULL,
    owner_ref VARCHAR(96) DEFAULT NULL,
    data_origin VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    discovered_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (reconciliation_ref),
    UNIQUE KEY uk_hz_reconciliation_order_type (order_ref, difference_type),
    KEY idx_hz_reconciliation_queue (case_state, discovered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_directory_report (
    report_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entry_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    description VARCHAR(500) NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    report_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (report_ref),
    UNIQUE KEY uk_hz_directory_report_idempotency (idempotency_key),
    KEY idx_hz_directory_report_entry (entry_ref,created_at),
    CONSTRAINT fk_hz_directory_report_entry FOREIGN KEY (entry_ref) REFERENCES hz_directory_entry (entry_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_v1_admin_command (
    command_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    command_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resulting_version BIGINT UNSIGNED DEFAULT NULL,
    audit_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (command_ref),
    UNIQUE KEY uk_hz_v1_admin_idempotency (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_v1_admin_audit (
    audit_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_ref VARCHAR(96) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    before_version BIGINT UNSIGNED NOT NULL,
    after_version BIGINT UNSIGNED NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (audit_ref),
    KEY idx_hz_v1_admin_audit_object (object_type, object_ref, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hz_v1_dev_seed_registry (
    object_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seed_version INT UNSIGNED NOT NULL,
    source_mode VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (object_type,object_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
