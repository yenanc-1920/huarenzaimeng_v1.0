package com.huarenzaimeng.api;

import java.time.Instant;
import java.util.List;

enum OperatorQualification { SUPPORTED, UNSUPPORTED, UNKNOWN }

record CatalogItem(String operatorCode, String productRef, String denominationRef, String itemKind,
                   long amountMinor, String currency) {}

record CatalogView(Long supportedOperatorSetVersion, Long catalogVersion, String operatorCode,
                   OperatorQualification operatorQualification, List<CatalogItem> items,
                   String evidenceSemantics) {}

record CatalogSelection(long supportedOperatorSetVersion, long catalogVersion, String operatorCode,
                        String productRef, String denominationRef, long amountMinor, String currency) {}

record CatalogBatch(long supportedOperatorSetVersion, long catalogVersion, String approvalRef,
                    Instant effectiveFrom, Instant expiresAt, boolean qualificationKnown,
                    List<String> supportedOperators, List<CatalogItem> items) {}
