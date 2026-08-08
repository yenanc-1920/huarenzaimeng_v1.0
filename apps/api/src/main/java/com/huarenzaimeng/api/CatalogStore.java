package com.huarenzaimeng.api;

interface CatalogStore {
    CatalogView read(String operatorCode);
    CatalogSelection requirePreset(long supportedOperatorSetVersion, long catalogVersion, String operatorCode,
                                   String productRef, String denominationRef);
    long mutationCount();
}
