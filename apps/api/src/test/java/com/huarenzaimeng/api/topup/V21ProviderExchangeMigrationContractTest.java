package com.huarenzaimeng.api.topup;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class V21ProviderExchangeMigrationContractTest {
    @Test void freezesRawAndNormalizedProviderContractWithoutRawBodyStorage() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V21__add_provider_exchange_attempt.sql"));
        assertThat(sql).contains("provider_code","operation_code","merchant_order_ref","provider_request_ref",
                "local_amount_minor","local_currency","provider_amount_raw","provider_currency_raw","provider_sku",
                "recipient_digest","canonical_request_digest","raw_response_digest","provider_ref","contract_version",
                "observed_at","normalized_amount_minor","normalized_currency","normalization_status",
                "UNIQUE KEY uk_hz_provider_exchange_request (provider_code,provider_request_ref)");
        assertThat(sql).doesNotContain("raw_response_body","recipient_raw");
    }
}
