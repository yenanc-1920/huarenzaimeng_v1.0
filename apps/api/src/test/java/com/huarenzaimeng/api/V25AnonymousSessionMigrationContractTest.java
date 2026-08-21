package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class V25AnonymousSessionMigrationContractTest {
    @Test void addsDigestOnlyTwentyFourHourAnonymousSessionStructureWithoutChangingHistory() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V25__add_anonymous_transaction_session.sql"));
        assertThat(sql).contains("CREATE TABLE buyer_anonymous_session","anonymous_subject_ref","request_ref","request_digest",
                        "guest_ref_digest","token_digest","absolute_expires_at","UNIQUE KEY uk_buyer_anonymous_request",
                        "UNIQUE KEY uk_buyer_anonymous_token","ENGINE=InnoDB","ascii_bin")
                .doesNotContain("guest_ref VARCHAR","token VARCHAR","DROP ","ALTER TABLE","UPDATE ","DELETE ","TRUNCATE ");
    }
}
