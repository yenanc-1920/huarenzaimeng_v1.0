CREATE TABLE IF NOT EXISTS hz_business_event_outbox (
    outbox_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    merchant_order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    lease_owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    lease_until DATETIME(3) DEFAULT NULL,
    delivery_attempts INT UNSIGNED NOT NULL DEFAULT 0,
    dispatched_at DATETIME(3) DEFAULT NULL,
    last_error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    PRIMARY KEY (outbox_ref),
    UNIQUE KEY uk_hz_business_event_outbox_event (event_ref),
    KEY idx_hz_business_event_outbox_dispatch (dispatched_at,lease_until,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
