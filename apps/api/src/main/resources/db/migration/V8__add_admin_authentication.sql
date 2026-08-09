CREATE TABLE admin_user (
    user_id CHAR(36) NOT NULL,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    status_code VARCHAR(16) NOT NULL,
    failed_login_count INT UNSIGNED NOT NULL DEFAULT 0,
    locked_until TIMESTAMP(3) NULL,
    password_changed_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_admin_user_username (username),
    CONSTRAINT chk_admin_user_role CHECK (role_code IN ('SUPER_ADMIN', 'CS', 'FIN', 'CONTENT')),
    CONSTRAINT chk_admin_user_status CHECK (status_code IN ('ACTIVE', 'LOCKED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_session (
    session_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    token_digest CHAR(64) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    last_seen_at TIMESTAMP(3) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    revoked_at TIMESTAMP(3) NULL,
    PRIMARY KEY (session_id),
    UNIQUE KEY uk_admin_session_token_digest (token_digest),
    KEY idx_admin_session_user_expiry (user_id, expires_at),
    CONSTRAINT fk_admin_session_user FOREIGN KEY (user_id) REFERENCES admin_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_auth_audit (
    audit_id CHAR(36) NOT NULL,
    event_code VARCHAR(40) NOT NULL,
    user_id CHAR(36) NULL,
    username_fingerprint CHAR(64) NULL,
    result_code VARCHAR(24) NOT NULL,
    request_id VARCHAR(80) NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (audit_id),
    KEY idx_admin_auth_audit_occurred (occurred_at),
    KEY idx_admin_auth_audit_user (user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
