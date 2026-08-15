CREATE TABLE hz_buyer_recovery_case (
  recovery_case_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  project_subject_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  command_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  input_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  recovery_material_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  recovery_state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (recovery_case_ref),
  UNIQUE KEY uk_hz_buyer_recovery_command (project_subject_ref, command_id),
  UNIQUE KEY uk_hz_buyer_recovery_idempotency (project_subject_ref, idempotency_key),
  KEY ix_hz_buyer_recovery_order (project_subject_ref, order_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
