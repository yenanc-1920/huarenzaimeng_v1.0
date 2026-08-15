package com.huarenzaimeng.api.reloadly;

import java.math.BigDecimal;

interface ReloadlySandboxTopupPort {
    CreateResult create(String requestRef);
    StatusResult status(long providerTransactionId);

    sealed interface CreateResult permits Created, Rejected, Unknown {}
    record Created(long providerTransactionId, BigDecimal amount, String currency) implements CreateResult {}
    record Rejected(String code) implements CreateResult {}
    record Unknown(String code) implements CreateResult {}

    sealed interface StatusResult permits Observed, StatusUnknown {}
    record Observed(String statusCode) implements StatusResult {}
    record StatusUnknown(String code) implements StatusResult {}
}

