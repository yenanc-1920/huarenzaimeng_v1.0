package com.huarenzaimeng.api.reloadly;

import java.math.BigDecimal;

interface ReloadlySandboxTopupStore {
    Begin begin(String requestRef, String fingerprint);
    void created(String requestRef, long providerTransactionId, BigDecimal amount, String currency);
    void rejected(String requestRef);
    void unknown(String requestRef);
    Record require(String requestRef);
    void observed(String requestRef, long providerTransactionId, String statusCode);

    enum Begin { NEW, EXISTING }
    record Record(String requestRef, String commandState, Long providerTransactionId,
                  BigDecimal amount, String currency, String lastStatus) {}
}

