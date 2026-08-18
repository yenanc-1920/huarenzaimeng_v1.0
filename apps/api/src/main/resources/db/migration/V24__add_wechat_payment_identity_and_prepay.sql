CREATE TABLE IF NOT EXISTS buyer_wechat_payment_identity (
    buyer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    app_id_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    openid_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (buyer_id),
    UNIQUE KEY uk_buyer_wechat_payment_openid (app_id_ref,openid_ref),
    CONSTRAINT fk_buyer_wechat_payment_identity FOREIGN KEY (buyer_id) REFERENCES buyer_identity (buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE hz_payment_coordination
    ADD COLUMN prepay_timestamp VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN prepay_nonce VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN prepay_package VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN prepay_sign_type VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN prepay_pay_sign VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN prepay_expires_at DATETIME(3) NULL;
