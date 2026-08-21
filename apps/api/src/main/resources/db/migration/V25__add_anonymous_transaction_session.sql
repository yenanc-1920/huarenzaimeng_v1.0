CREATE TABLE buyer_anonymous_session (
    anonymous_session_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    anonymous_subject_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    guest_ref_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_agreement_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    privacy_policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    absolute_expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (anonymous_session_ref),
    UNIQUE KEY uk_buyer_anonymous_subject (anonymous_subject_ref),
    UNIQUE KEY uk_buyer_anonymous_request (request_ref),
    UNIQUE KEY uk_buyer_anonymous_token (token_digest),
    KEY idx_buyer_anonymous_expiry (status_code,absolute_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
