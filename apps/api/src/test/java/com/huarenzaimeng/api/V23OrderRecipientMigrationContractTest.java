package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V23OrderRecipientMigrationContractTest {
    @Test void migrationUsesDedicatedTablesAndNoSupplierPayloadPlaintext(){
        Path migration=Path.of("src/main/resources/db/migration/V23__add_order_recipient_fulfillment.sql");
        assertThat(migration).exists();
        String sql=read(migration);
        assertThat(sql).contains("hz_quote_recipient_pending","hz_order_recipient_fulfillment","recipient_plain","retention_until","recipient_masked","recipient_digest");
        assertThat(sql).doesNotContain("hz_provider_exchange_attempt").doesNotContain("hz_topup_callback");
    }
    private static String read(Path path){try{return Files.readString(path);}catch(Exception e){throw new AssertionError(e);}}
}
