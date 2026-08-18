package com.huarenzaimeng.api.buyerauth;

/**
 * Stable WeChat identity boundary. Implementations must not persist the raw one-time code
 * and must return UNKNOWN when provider completion cannot be proved.
 */
public interface WeChatIdentityPort {
    Result exchange(Command command);

    record Command(String oneTimeCode, String providerCallRef) {}

    sealed interface Result permits Success, Rejected, Unknown {}
    record Success(String appIdRef, String providerSubject, String evidenceRef) implements Result {}
    record Rejected(String reasonClass) implements Result {}
    record Unknown(String reasonClass) implements Result {}
}
