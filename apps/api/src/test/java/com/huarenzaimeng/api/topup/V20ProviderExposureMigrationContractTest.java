package com.huarenzaimeng.api.topup;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V20ProviderExposureMigrationContractTest {
    @Test void migrationDefinesVersionedScopeLimitsAndOrderIdempotentExposure() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V20__add_provider_exposure_limits.sql"));
        assertThat(sql).contains("CREATE TABLE hz_provider_limit", "max_inflight_count", "max_inflight_minor", "max_single_minor", "enabled", "aggregate_version", "reserved_count", "reserved_minor");
        assertThat(sql).contains("uk_hz_provider_limit_scope (provider_code,channel_ref,operator_code,currency)");
        assertThat(sql).contains("CREATE TABLE hz_provider_exposure", "uk_hz_provider_exposure_order (merchant_order_ref)", "limit_version", "exposure_state");
        assertThat(sql).contains("active_reserved_minor", "scope_provider_code", "scope_channel_ref", "scope_operator_code", "supplier_cost");
        assertThat(sql).doesNotContain("CREATE TABLE IF NOT EXISTS");
    }

    @Test void runtimeUsesLockedCountersRatherThanPreLockAggregateQueries() throws Exception {
        String exposure=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/topup/JdbcProviderExposureStore.java"));
        String topup=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/topup/JdbcTopupStore.java"));
        assertThat(exposure).contains("reserved_count,reserved_minor", "FOR UPDATE");
        assertThat(exposure).doesNotContain("SELECT COUNT(*)", "SUM(amount_minor)");
        assertThat(topup).contains("active_reserved_minor", "scope_provider_code", "supplier_cost", "FOR UPDATE");
        assertThat(topup).doesNotContain("SUM(amount_minor)");
    }
}
