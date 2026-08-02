ALTER TABLE hz_command
    ADD UNIQUE KEY uk_hz_command_subject_endpoint_idempotency
      (project_subject_ref, endpoint_scope, idempotency_key);
