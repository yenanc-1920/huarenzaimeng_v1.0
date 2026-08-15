package com.huarenzaimeng.api.buyerauth;

/** Trusted buyer identity established only by BuyerSessionFilter. */
public record BuyerSessionPrincipal(String subjectRef, String sessionRef) {
    public BuyerSessionPrincipal {
        if (subjectRef == null || subjectRef.isBlank() || sessionRef == null || sessionRef.isBlank()) {
            throw new IllegalArgumentException("trusted buyer identity is incomplete");
        }
    }
}
