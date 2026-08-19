package com.huarenzaimeng.api.topup;

import java.math.BigDecimal;

public interface TopupEligibilityPort {
    Snapshot requirePaidEntitlement(String merchantOrderRef);
    record Snapshot(String merchantOrderRef,String buyerSubjectRef,String providerSku,String recipient,
                    long faceValueMinor,String targetCurrency,String entitlementDigest,String channelRef,String operatorCode,
                    BigDecimal supplierCost,String settlementCurrency) {
        Snapshot(String merchantOrderRef,String buyerSubjectRef,String providerSku,String recipient,
                 long faceValueMinor,String targetCurrency,String entitlementDigest,String channelRef,String operatorCode) {
            this(merchantOrderRef,buyerSubjectRef,providerSku,recipient,faceValueMinor,targetCurrency,entitlementDigest,channelRef,operatorCode,
                    BigDecimal.valueOf(faceValueMinor,2),targetCurrency);
        }
        Snapshot(String merchantOrderRef,String buyerSubjectRef,String providerSku,String recipient,
                 long faceValueMinor,String targetCurrency,String entitlementDigest,String channelRef,String operatorCode,BigDecimal supplierCost) {
            this(merchantOrderRef,buyerSubjectRef,providerSku,recipient,faceValueMinor,targetCurrency,entitlementDigest,channelRef,operatorCode,supplierCost,targetCurrency);
        }
        Snapshot(String merchantOrderRef,String buyerSubjectRef,String providerSku,String recipient,
                 long faceValueMinor,String targetCurrency,String entitlementDigest) {
            this(merchantOrderRef,buyerSubjectRef,providerSku,recipient,faceValueMinor,targetCurrency,entitlementDigest,"UNSCOPED","UNSCOPED",
                    BigDecimal.valueOf(faceValueMinor,2),targetCurrency);
        }
    }
}
