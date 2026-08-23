package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V26BuyerClosureStatusMigrationContractTest {
    @Test void widensBothColumnsThatStoreClosureRequested() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V26__widen_buyer_closure_status_columns.sql"));
        assertThat(sql)
                .contains("ALTER TABLE buyer_identity", "status_code VARCHAR(24)")
                .contains("ALTER TABLE buyer_consent_state", "consent_state VARCHAR(24)")
                .doesNotContain("DROP ", "DELETE ", "TRUNCATE ", "UPDATE ");
    }
}
