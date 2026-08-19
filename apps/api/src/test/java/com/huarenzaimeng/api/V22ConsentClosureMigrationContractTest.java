package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V22ConsentClosureMigrationContractTest {
    @Test void addsOnlyCandidateConsentClosureAndAuditedExceptionStructures() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V22__add_buyer_consent_closure_and_self_approval.sql"));
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS buyer_consent_state")
                .contains("CREATE TABLE IF NOT EXISTS buyer_consent_acceptance")
                .contains("CREATE TABLE IF NOT EXISTS buyer_account_closure_request")
                .contains("CREATE TABLE IF NOT EXISTS buyer_pii_cleanup_task")
                .contains("policy_type","policy_version","request_digest","accepted_at")
                .contains("closure_state","blocker_count","aggregate_version")
                .contains("attempt_count","next_attempt_at","lease_owner","lease_until","last_error_code")
                .contains("self_approved","exception_policy_version")
                .contains("information_schema.columns","PREPARE v22_statement")
                .doesNotContain("DROP TABLE","DROP COLUMN","TRUNCATE","DELETE FROM");
    }
}
