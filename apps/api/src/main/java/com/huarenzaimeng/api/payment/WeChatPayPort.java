package com.huarenzaimeng.api.payment;

import java.time.Instant;

/** Transport-neutral WeChat Pay boundary. Raw secrets never cross into the coordinator. */
public interface WeChatPayPort {
    default boolean available() { return true; }
    Result unifiedOrder(UnifiedOrder command);
    NotificationResult verifyAndDecrypt(NotificationEnvelope envelope);
    Result query(String merchantOrderRef);
    Result close(String merchantOrderRef);
    Result refund(Refund command);
    RefundQueryResult queryRefundOriginal(String refundRef);

    record UnifiedOrder(String merchantOrderRef, long amountMinor, String currency,
                        String payerSubjectRef, String requestDigest) {}
    record Refund(String merchantOrderRef, String refundRef, long amountMinor, String requestDigest) {}
    record NotificationEnvelope(String notificationId, String timestamp, String nonce,
                                String signature, String encryptedBody) {}
    sealed interface Result permits Accepted, Rejected, Unknown {}
    record Accepted(String providerRef, String state, String evidenceRef) implements Result {}
    record Rejected(String reasonCode) implements Result {}
    record Unknown(String reasonCode) implements Result {}
    sealed interface RefundQueryResult permits RefundObservation, RefundQueryUnknown {}
    record RefundObservation(String refundRef, String merchantOrderRef, long amountMinor, String currency,
                             String state, String evidenceRef) implements RefundQueryResult {}
    record RefundQueryUnknown(String reasonCode) implements RefundQueryResult {}
    sealed interface NotificationResult permits VerifiedNotification, InvalidNotification, NotificationUnknown {}
    record VerifiedNotification(String notificationId, String notificationDigest, String appId, String merchantId,
                                String certificateSerial, String merchantOrderRef, String providerRef,
                                long amountMinor, String currency, String state, Instant occurredAt,
                                String evidenceRef) implements NotificationResult {}
    record InvalidNotification(String reasonCode) implements NotificationResult {}
    record NotificationUnknown(String reasonCode) implements NotificationResult {}
}
