package com.huarenzaimeng.api.topup;

import java.math.BigDecimal;

public interface TopupEligibilityPort {
    Snapshot requirePaidEntitlement(String merchantOrderRef);
    record Snapshot(String merchantOrderRef,String buyerSubjectRef,String providerSku,String recipient,
                    long faceValueMinor,String targetCurrency,String entitlementDigest,String channelRef,String operatorCode,
                    BigDecimal supplierCost) {
        Snapshot(String merchantOrderRef,String buyerSubjectRef,String providerSku,String recipient,
                 long faceValueMinor,String targetCurrency,String entitlementDigest,String channelRef,String operatorCode) {
            this(merchantOrderRef,buyerSubjectRef,providerSku,recipient,faceValueMinor,targetCurrency,entitlementDigest,channelRef,operatorCode,BigDecimal.ZERO);
        }
        Snapshot(String merchantOrderRef,String buyerSubjectRef,String providerSku,String recipient,
                 long faceValueMinor,String targetCurrency,String entitlementDigest) {
            this(merchantOrderRef,buyerSubjectRef,providerSku,recipient,faceValueMinor,targetCurrency,entitlementDigest,"UNSCOPED","UNSCOPED",null);
        }
    }
}
