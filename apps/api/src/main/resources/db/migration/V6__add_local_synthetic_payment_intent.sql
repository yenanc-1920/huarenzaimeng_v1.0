CREATE TABLE hz_payment_intent (
    payment_intent_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    environment VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    project_subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    business_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    semantic_action_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    price_snapshot_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payment_eligibility_decision_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    intent_scope VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (payment_intent_ref),
    UNIQUE KEY uk_hz_payment_intent_business
      (environment, project_subject_ref, order_ref),
    UNIQUE KEY uk_hz_payment_intent_business_key (business_key),
    UNIQUE KEY uk_hz_payment_intent_semantic (semantic_action_key),
    KEY idx_hz_payment_intent_subject_ref (project_subject_ref, payment_intent_ref),
    CONSTRAINT fk_hz_payment_intent_order FOREIGN KEY (order_ref) REFERENCES hz_order (order_ref),
    CONSTRAINT fk_hz_payment_intent_semantic FOREIGN KEY (semantic_action_key)
      REFERENCES hz_semantic_action (semantic_action_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
