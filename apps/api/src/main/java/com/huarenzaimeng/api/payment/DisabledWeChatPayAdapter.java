package com.huarenzaimeng.api.payment;

/** Default production-safe adapter: no URI, credential loader or network client exists. */
public final class DisabledWeChatPayAdapter implements WeChatPayPort {
    private static final String DISABLED = "WECHAT_PAY_ADAPTER_DISABLED";
    @Override public boolean available() { return false; }
    @Override public Result unifiedOrder(UnifiedOrder command) { return new Unknown(DISABLED); }
    @Override public NotificationResult verifyAndDecrypt(NotificationEnvelope envelope) { return new NotificationUnknown(DISABLED); }
    @Override public Result query(String merchantOrderRef) { return new Unknown(DISABLED); }
    @Override public Result close(String merchantOrderRef) { return new Unknown(DISABLED); }
    @Override public Result refund(Refund command) { return new Unknown(DISABLED); }
    @Override public RefundQueryResult queryRefundOriginal(String refundRef) { return new RefundQueryUnknown(DISABLED); }
}
