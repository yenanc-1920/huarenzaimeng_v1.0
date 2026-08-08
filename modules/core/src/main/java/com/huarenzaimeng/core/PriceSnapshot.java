package com.huarenzaimeng.core;

import java.time.Instant;

public record PriceSnapshot(
        String quoteRef,
        String maskedPhone,
        String operatorCode,
        String productCode,
        String denominationRef,
        long supportedOperatorSetVersion,
        long catalogVersion,
        long totalAmountMinor,
        String currency,
        Instant expiresAt
) {
    public static PriceSnapshot from(Quote quote) {
        return new PriceSnapshot(quote.quoteRef(), quote.maskedPhone(), quote.operatorCode(), quote.productCode(),
                quote.denominationRef(), quote.supportedOperatorSetVersion(), quote.catalogVersion(),
                quote.totalAmountMinor(), quote.currency(), quote.expiresAt());
    }
}
