package com.huarenzaimeng.core;

import java.time.Instant;

public record Quote(
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
) {}
