ALTER TABLE buyer_identity
    MODIFY COLUMN status_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

ALTER TABLE buyer_consent_state
    MODIFY COLUMN consent_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
