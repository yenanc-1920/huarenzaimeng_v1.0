package com.huarenzaimeng.api.payment;

/** Trusted local order/quote/price snapshot used to construct a provider payment command. */
public interface PaymentOrderSnapshotPort {
    Snapshot requirePayable(String merchantOrderRef);
    default Snapshot requirePayable(String merchantOrderRef,String buyerSubjectRef) {
        Snapshot snapshot=requirePayable(merchantOrderRef);
        if(buyerSubjectRef!=null&&!buyerSubjectRef.equals(snapshot.buyerSubjectRef()))
            throw new WeChatPayCoordinator.Conflict("PAYMENT_ORDER_OWNERSHIP_CONFLICT");
        return snapshot;
    }
    record Snapshot(String merchantOrderRef,String buyerSubjectRef,String quoteRef,String priceSnapshotDigest,
                    long amountMinor,String currency) {}
}
