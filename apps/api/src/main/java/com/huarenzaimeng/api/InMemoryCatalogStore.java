package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "in-memory")
class InMemoryCatalogStore implements CatalogStore {
    private final Clock clock;
    private CatalogBatch current;

    InMemoryCatalogStore(Clock clock) {
        this.clock = clock;
        current = syntheticBatch();
    }

    @Override public synchronized CatalogView read(String operatorCode) {
        if (operatorCode == null || operatorCode.isBlank()) {
            return new CatalogView(null, null, operatorCode, OperatorQualification.UNKNOWN, List.of(),
                    "OPERATOR_CODE_REQUIRED");
        }
        if (current == null || !current.qualificationKnown()
                || current.effectiveFrom().isAfter(clock.instant()) || !current.expiresAt().isAfter(clock.instant())) {
            return new CatalogView(null, null, operatorCode, OperatorQualification.UNKNOWN, List.of(),
                    "LOCAL_MOCK_NO_REAL_OPERATOR_FACTS");
        }
        if (!current.supportedOperators().contains(operatorCode)) {
            return new CatalogView(current.supportedOperatorSetVersion(), current.catalogVersion(), operatorCode,
                    OperatorQualification.UNSUPPORTED, List.of(), "LOCAL_MOCK_NO_REAL_OPERATOR_FACTS");
        }
        List<CatalogItem> items = current.items().stream()
                .filter(item -> item.operatorCode().equals(operatorCode)).toList();
        return new CatalogView(current.supportedOperatorSetVersion(), current.catalogVersion(), operatorCode,
                OperatorQualification.SUPPORTED, items, "LOCAL_MOCK_NO_REAL_OPERATOR_FACTS");
    }

    @Override public synchronized CatalogSelection requirePreset(long setVersion, long catalogVersion,
                                                                  String operatorCode, String productRef,
                                                                  String denominationRef) {
        CatalogView view = read(operatorCode);
        if (view.operatorQualification() == OperatorQualification.UNKNOWN) {
            throw new FlowRejectedException("OPERATOR_QUALIFICATION_UNKNOWN");
        }
        if (view.operatorQualification() == OperatorQualification.UNSUPPORTED) {
            throw new FlowRejectedException("OPERATOR_UNSUPPORTED");
        }
        if (view.supportedOperatorSetVersion() != setVersion) {
            throw new FlowRejectedException("SUPPORTED_OPERATOR_SET_VERSION_STALE");
        }
        if (view.catalogVersion() != catalogVersion) {
            throw new FlowRejectedException("CATALOG_VERSION_STALE");
        }
        CatalogItem item = view.items().stream()
                .filter(value -> value.productRef().equals(productRef)
                        && value.denominationRef().equals(denominationRef)).findFirst()
                .orElseThrow(() -> new FlowRejectedException("PRESET_CATALOG_ITEM_NOT_FOUND"));
        return new CatalogSelection(setVersion, catalogVersion, operatorCode, productRef, denominationRef,
                item.amountMinor(), item.currency());
    }

    @Override public long mutationCount() { return 0; }

    synchronized void installForTest(CatalogBatch batch) { current = batch; }
    synchronized void resetForTest() { current = syntheticBatch(); }

    private CatalogBatch syntheticBatch() {
        java.time.Instant now = clock.instant();
        if (now == null) now = java.time.Instant.now();
        CatalogItem item = new CatalogItem("SYN-OP", "SYN-PRODUCT", "SYN-DENOM-1000", "PRESET_DENOMINATION",
                1_000L, "CNY");
        return new CatalogBatch(1L, 1L, "APPROVAL-E3-SYNTHETIC-ONLY", now.minusSeconds(60),
                now.plusSeconds(86_400), true, List.of("SYN-OP"), List.of(item));
    }
}
