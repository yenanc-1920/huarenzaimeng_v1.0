package com.huarenzaimeng.api;

import org.springframework.stereotype.Service;

@Service
class CatalogService {
    private final CatalogStore store;
    CatalogService(CatalogStore store) { this.store = store; }

    CatalogView publicCatalog(String operatorCode) { return store.read(operatorCode); }

    CatalogSelection requirePreset(long supportedOperatorSetVersion, long catalogVersion, String operatorCode,
                                   String productRef, String denominationRef) {
        return store.requirePreset(supportedOperatorSetVersion, catalogVersion, operatorCode, productRef,
                denominationRef);
    }

    long mutationCount() { return store.mutationCount(); }
}
