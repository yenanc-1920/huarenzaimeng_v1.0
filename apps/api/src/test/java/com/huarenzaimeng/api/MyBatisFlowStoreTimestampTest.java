package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MyBatisFlowStoreTimestampTest {
    private static final String KEY = "expires_at";

    @Test
    void acceptsTimestampReturnedByJdbc() {
        Timestamp value = Timestamp.valueOf("2026-08-01 07:22:31.857092832");

        assertThat(MyBatisFlowStore.timestamp(Map.of(KEY, value), KEY)).isSameAs(value);
    }

    @Test
    void acceptsLocalDateTimeReturnedByMyBatis() {
        LocalDateTime value = LocalDateTime.parse("2026-08-01T07:22:31.857092832");

        assertThat(MyBatisFlowStore.timestamp(Map.of(KEY, value), KEY)).isEqualTo(Timestamp.valueOf(value));
    }

    @Test
    void acceptsInstantWithoutStringOrZoneGuessing() {
        Instant value = Instant.parse("2026-08-01T07:22:31.857092832Z");

        assertThat(MyBatisFlowStore.timestamp(Map.of(KEY, value), KEY)).isEqualTo(Timestamp.from(value));
    }

    @Test
    void missingOrUnknownTimestampTypesFailClosed() {
        assertThatThrownBy(() -> MyBatisFlowStore.timestamp(Map.of(), KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("missing timestamp column: expires_at");
        assertThatThrownBy(() -> MyBatisFlowStore.timestamp(Map.of(KEY, "2026-08-01T07:22:31Z"), KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported timestamp type for expires_at: java.lang.String");
    }

    @Test
    void legacyQuoteWithNullV4CatalogColumnsRequiresRequoteWithoutSideEffects() {
        FlowMapper mapper = mock(FlowMapper.class);
        org.springframework.transaction.support.TransactionTemplate transactions =
                mock(org.springframework.transaction.support.TransactionTemplate.class);
        MyBatisFlowStore store = new MyBatisFlowStore(mapper, transactions);
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("quote_ref", "Q-LEGACY");
        legacy.put("phone_masked", "880****000");
        legacy.put("operator_code", "SYN-OP");
        legacy.put("product_code", "SYN-PRODUCT");
        legacy.put("denomination_ref", null);
        legacy.put("supported_operator_set_version", null);
        legacy.put("catalog_version", null);
        legacy.put("total_amount_minor", 1000L);
        legacy.put("total_currency", "CNY");
        legacy.put("expires_at", Timestamp.from(Instant.parse("2026-08-01T07:22:31Z")));
        when(mapper.selectQuote("SUBJECT-LEGACY", "Q-LEGACY")).thenReturn(legacy);

        assertThatThrownBy(() -> store.requireQuote("SUBJECT-LEGACY", "Q-LEGACY"))
                .isInstanceOf(FlowRejectedException.class)
                .hasMessage("QUOTE_REQUOTE_REQUIRED_LEGACY_RECORD");
        verify(mapper).selectQuote("SUBJECT-LEGACY", "Q-LEGACY");
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(transactions);
    }
}
