package com.huarenzaimeng.api.buyerauth;

interface WechatCode2SessionPort {
    Result exchange(Command command);

    record Command(String code, String providerCallRef) {}
    sealed interface Result permits Success, Rejected, Unknown {}
    record Success(String appIdRef, String providerSubject, String evidenceRef) implements Result {}
    record Rejected(String reasonClass) implements Result {}
    record Unknown(String reasonClass) implements Result {}
}
