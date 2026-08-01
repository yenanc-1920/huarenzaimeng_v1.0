package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
