package com.huarenzaimeng.api;

import java.math.BigDecimal;
import java.time.Instant;

/** ECB-shaped boundary. No network implementation is present in the V1 local batch. */
interface FxRatePort {
    RateSnapshot latest(String settlementCurrency,String quoteCurrency);
    record RateSnapshot(String snapshotRef,String sourceCode,String baseCurrency,String quoteCurrency,
                        BigDecimal rate,String conversionPath,Instant observedAt,Instant validUntil,String sourceDigest) {}
}
