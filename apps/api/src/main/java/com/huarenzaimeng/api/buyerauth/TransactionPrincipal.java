package com.huarenzaimeng.api.buyerauth;

public sealed interface TransactionPrincipal permits BuyerSessionPrincipal, AnonymousTransactionPrincipal {
    enum Type { BUYER, ANONYMOUS }
    Type type();
    String subjectRef();
    String sessionRef();
}
