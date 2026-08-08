package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "hz.persistence.mode", havingValue = "mysql")
class MyBatisCatalogStore implements CatalogStore {
    private final CatalogMapper mapper;
    private final Clock clock;

    MyBatisCatalogStore(CatalogMapper mapper, Clock clock) { this.mapper = mapper; this.clock = clock; }

    @Override public CatalogView read(String operatorCode) {
        if (operatorCode == null || operatorCode.isBlank()) {
            return new CatalogView(null, null, operatorCode, OperatorQualification.UNKNOWN, List.of(),
                    "OPERATOR_CODE_REQUIRED");
        }
        Map<String, Object> active = mapper.selectActiveCatalog(Timestamp.from(clock.instant()));
        if (active == null || !bool(active, "qualification_known")) {
            return new CatalogView(null, null, operatorCode, OperatorQualification.UNKNOWN, List.of(),
                    "LOCAL_DATABASE_STATE_NOT_EXTERNAL_OPERATOR_FACT");
        }
        long setVersion = number(active, "supported_operator_set_version");
        long catalogVersion = number(active, "catalog_version");
        List<String> supported = mapper.selectSupportedOperators(setVersion);
        if (!supported.contains(operatorCode)) {
            return new CatalogView(setVersion, catalogVersion, operatorCode, OperatorQualification.UNSUPPORTED,
                    List.of(), "LOCAL_DATABASE_STATE_NOT_EXTERNAL_OPERATOR_FACT");
        }
        List<CatalogItem> items = mapper.selectCatalogItems(catalogVersion).stream().map(this::item)
                .filter(item -> item.operatorCode().equals(operatorCode)).toList();
        return new CatalogView(setVersion, catalogVersion, operatorCode, OperatorQualification.SUPPORTED, items,
                "LOCAL_DATABASE_STATE_NOT_EXTERNAL_OPERATOR_FACT");
    }

    @Override public CatalogSelection requirePreset(long setVersion, long catalogVersion, String operatorCode,
                                                     String productRef, String denominationRef) {
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
        if (view.catalogVersion() != catalogVersion) throw new FlowRejectedException("CATALOG_VERSION_STALE");
        CatalogItem item = view.items().stream().filter(value -> value.productRef().equals(productRef)
                        && value.denominationRef().equals(denominationRef)).findFirst()
                .orElseThrow(() -> new FlowRejectedException("PRESET_CATALOG_ITEM_NOT_FOUND"));
        return new CatalogSelection(setVersion, catalogVersion, operatorCode, productRef, denominationRef,
                item.amountMinor(), item.currency());
    }

    @Override public long mutationCount() { return 0; }

    private CatalogItem item(Map<String, Object> row) {
        return new CatalogItem(string(row, "operator_code"), string(row, "product_ref"),
                string(row, "denomination_ref"), string(row, "item_kind"), number(row, "amount_minor"),
                string(row, "currency"));
    }
    private static String string(Map<String, Object> row, String key) { return String.valueOf(row.get(key)); }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key); return value instanceof Boolean b ? b : ((Number) value).intValue() != 0;
    }
}
