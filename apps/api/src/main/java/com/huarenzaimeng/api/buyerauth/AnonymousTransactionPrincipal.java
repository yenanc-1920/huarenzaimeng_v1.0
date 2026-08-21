package com.huarenzaimeng.api.buyerauth;

public record AnonymousTransactionPrincipal(String subjectRef, String sessionRef) implements TransactionPrincipal {
    public AnonymousTransactionPrincipal {
        if (subjectRef == null || subjectRef.isBlank() || sessionRef == null || sessionRef.isBlank()) {
            throw new IllegalArgumentException("trusted anonymous identity is incomplete");
        }
    }
    @Override public Type type() { return Type.ANONYMOUS; }
}
