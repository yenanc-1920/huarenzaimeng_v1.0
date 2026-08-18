package com.huarenzaimeng.api.topup;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class JdbcProviderExchangeStoreTest {
    JdbcTemplate jdbc; JdbcProviderExchangeStore store; Instant now=Instant.parse("2026-08-18T00:00:00Z");
    @BeforeEach void setup(){JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:exchange"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE hz_provider_exchange_attempt(exchange_ref VARCHAR(64) PRIMARY KEY,provider_code VARCHAR(32),operation_code VARCHAR(24),merchant_order_ref VARCHAR(64),provider_request_ref VARCHAR(128),local_amount_minor DECIMAL(20),local_currency VARCHAR(3),provider_amount_raw VARCHAR(96),provider_currency_raw VARCHAR(16),provider_sku VARCHAR(128),recipient_digest VARCHAR(64),canonical_request_digest VARCHAR(64),raw_response_digest VARCHAR(64),provider_ref VARCHAR(128),contract_version VARCHAR(32),observed_at TIMESTAMP,normalized_amount_minor DECIMAL(20),normalized_currency VARCHAR(3),normalization_status VARCHAR(40),created_at TIMESTAMP,UNIQUE(provider_code,provider_request_ref))");store=new JdbcProviderExchangeStore(jdbc);}
    @Test void exactReplayDoesNotUpdateRawAndDifferentParametersConflict(){var first=attempt("REQ-1","100.00","a".repeat(64));assertThat(store.record(first)).isEqualTo(ProviderExchangeStore.SaveResult.CREATED);assertThat(store.record(first)).isEqualTo(ProviderExchangeStore.SaveResult.REPLAY);var drift=attempt("REQ-1","101.00","b".repeat(64));assertThatThrownBy(()->store.record(drift)).hasMessage("PROVIDER_REQUEST_REF_CONFLICT");assertThat(jdbc.queryForObject("SELECT provider_amount_raw FROM hz_provider_exchange_attempt",String.class)).isEqualTo("100.00");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_provider_exchange_attempt",Integer.class)).isOne();}
    @Test void validatedSubmitCanBeReadAsImmutableOriginal(){store.record(attempt("REQ-1","100.00","a".repeat(64)));var original=store.requireOriginal("WINLA","ORDER-1");assertThat(original.localAmountMinor()).isEqualTo(10000);assertThat(original.providerSku()).isEqualTo("SKU-1");}
    private ProviderExchangeStore.Attempt attempt(String request,String amount,String responseDigest){return new ProviderExchangeStore.Attempt("EX-1","WINLA",ProviderExchangeStore.Operation.SUBMIT,"ORDER-1",request,10000,"BDT",amount,"BDT","SKU-1","c".repeat(64),"d".repeat(64),responseDigest,"P-1","FAKE-V1",now,10000L,"BDT",ProviderExchangeStore.NormalizationStatus.VALIDATED);}
}
