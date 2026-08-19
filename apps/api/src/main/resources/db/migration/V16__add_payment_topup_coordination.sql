CREATE TABLE IF NOT EXISTS hz_payment_coordination (
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    buyer_subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quote_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    price_snapshot_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    amount_minor BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    query_budget_remaining INT UNSIGNED NOT NULL DEFAULT 0,
    query_deadline DATETIME(3) NOT NULL,
    refunded_minor BIGINT UNSIGNED NOT NULL DEFAULT 0,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (merchant_order_ref),
    UNIQUE KEY uk_hz_payment_request_digest (request_digest),
    KEY idx_hz_payment_provider (provider_ref,state_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_payment_notification (
    notification_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    notification_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    app_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mch_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    certificate_serial VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount_minor BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    observed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (notification_id),
    UNIQUE KEY uk_hz_payment_notification_digest (notification_digest),
    KEY idx_hz_payment_notification_order (merchant_order_ref,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_payment_refund (
    refund_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount_minor BIGINT UNSIGNED NOT NULL,
    state_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    original_payment_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    refund_query_budget_remaining INT UNSIGNED NOT NULL DEFAULT 0,
    refund_query_deadline DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (refund_ref), UNIQUE KEY uk_hz_payment_refund_digest (request_digest),
    KEY idx_hz_payment_refund_order (merchant_order_ref,state_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_topup_coordination (
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    buyer_subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_sku VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entitlement_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    submit_latched TINYINT(1) NOT NULL DEFAULT 0,
    unknown_queries_remaining INT UNSIGNED NOT NULL DEFAULT 0,
    state_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    reserved_minor BIGINT UNSIGNED NOT NULL,
    reserve_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (merchant_order_ref),
    UNIQUE KEY uk_hz_topup_request_ref (request_ref),
    UNIQUE KEY uk_hz_topup_request_digest (request_digest),
    KEY idx_hz_topup_provider (provider_code,provider_ref,state_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_topup_callback (
    callback_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    callback_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount_minor BIGINT UNSIGNED DEFAULT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    provider_sku VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    recipient VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    state_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    observed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (callback_id),
    UNIQUE KEY uk_hz_topup_callback_digest (callback_digest),
    KEY idx_hz_topup_callback_order (merchant_order_ref,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_order_fulfillment_snapshot (
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    buyer_subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_sku VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    face_value_minor BIGINT UNSIGNED NOT NULL, target_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entitlement_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entitlement_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (merchant_order_ref), UNIQUE KEY uk_hz_fulfillment_entitlement (entitlement_digest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_provider_balance_reservation (
    reservation_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount_minor BIGINT UNSIGNED NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reservation_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (reservation_ref),
    UNIQUE KEY uk_hz_balance_reservation_order (merchant_order_ref),
    KEY idx_hz_balance_reservation_provider (provider_code,reservation_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_provider_balance_ledger (
    entry_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entry_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount_minor BIGINT NOT NULL, currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL, PRIMARY KEY(entry_ref), KEY idx_hz_balance_ledger_order(merchant_order_ref,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_provider_balance_position (
    provider_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    confirmed_balance_minor BIGINT UNSIGNED NOT NULL,
    safety_buffer_minor BIGINT UNSIGNED NOT NULL DEFAULT 0,
    observed_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    evidence_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(provider_code,currency),KEY idx_hz_balance_position_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_provider_reconciliation_task (
    task_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(task_ref),UNIQUE KEY uk_hz_reconciliation_order(merchant_order_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_business_event_link (
    event_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parent_event_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parent_event_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    event_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    observed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (event_ref),
    UNIQUE KEY uk_hz_business_event_digest (event_digest),
    KEY idx_hz_business_event_order (merchant_order_ref,occurred_at),
    KEY idx_hz_business_event_parent (parent_event_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS buyer_login_rate_window (
    window_key_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    window_started_at DATETIME(3) NOT NULL,window_ends_at DATETIME(3) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL,failure_count INT UNSIGNED NOT NULL,updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(window_key_digest),KEY idx_buyer_login_window_expiry(window_ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_customer_case_subject_binding (
    case_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,PRIMARY KEY(case_ref),KEY idx_hz_case_subject(order_subject_ref,merchant_order_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_content_report_window (
    reporter_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    window_started_at DATETIME(3) NOT NULL,window_ends_at DATETIME(3) NOT NULL,
    report_count INT UNSIGNED NOT NULL,updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(reporter_digest,content_ref,reason_digest),KEY idx_hz_report_window_expiry(window_ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
