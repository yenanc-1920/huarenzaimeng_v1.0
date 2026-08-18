package com.huarenzaimeng.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Boundary only. A provider adapter must pass qualification review before being wired. */
interface ProviderCatalogPort {
    CatalogSnapshot fetch();
    record CatalogSnapshot(String providerCode,String sourceDigest,Instant capturedAt,List<Item> items) {}
    record Item(String providerSku,String rawName,String rawBenefitText,BigDecimal supplierCost,
                String settlementCurrency,String availability) {}
}
