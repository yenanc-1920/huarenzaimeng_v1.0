CREATE TABLE hz_state_advance_authority_fact (
  authority_fact_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  aggregate_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  command_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  environment VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  evidence_level VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  authorization_ref VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  target_transition VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  evidence_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  authority_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (authority_fact_id),
  UNIQUE KEY uq_state_advance_authority (aggregate_ref, command_id),
  CONSTRAINT fk_state_advance_authority_order FOREIGN KEY (aggregate_ref) REFERENCES hz_order (order_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
