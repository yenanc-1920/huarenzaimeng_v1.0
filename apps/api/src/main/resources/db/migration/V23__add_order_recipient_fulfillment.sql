CREATE TABLE IF NOT EXISTS hz_quote_recipient_pending (
    quote_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient_plain VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient_masked VARCHAR(32) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (quote_ref),
    CONSTRAINT fk_hz_quote_recipient_pending_quote FOREIGN KEY (quote_ref) REFERENCES hz_release_quote_snapshot (quote_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS hz_order_recipient_fulfillment (
    order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient_plain VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    recipient_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient_masked VARCHAR(32) NOT NULL,
    retention_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    terminal_at DATETIME(3) DEFAULT NULL,
    retention_until DATETIME(3) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (order_ref),
    KEY idx_hz_order_recipient_retention (retention_state,retention_until),
    CONSTRAINT fk_hz_order_recipient_fulfillment_order FOREIGN KEY (order_ref) REFERENCES hz_release_order_snapshot (order_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
