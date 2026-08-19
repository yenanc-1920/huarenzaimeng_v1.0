package com.huarenzaimeng.api.buyerauth;

import java.net.URI;
import java.time.Duration;

interface WechatCode2SessionTransport {
    Response execute(Request request);

    record Request(URI endpoint, String appId, String appSecret, String oneTimeCode,
                   Duration connectTimeout, Duration readTimeout) {
        @Override public String toString() {
            return "Request[endpoint=" + endpoint.getScheme() + "://" + endpoint.getHost() + endpoint.getPath()
                    + ", appId=[REDACTED], appSecret=[REDACTED], oneTimeCode=[REDACTED]"
                    + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
        }
    }
    record Response(int statusCode, String body) {}
    enum FailureKind { TIMEOUT, DNS, TLS, CONNECTION, UNAVAILABLE }
    final class Failure extends RuntimeException {
        private final FailureKind kind;
        Failure(FailureKind kind) { super("WECHAT_TRANSPORT_" + kind.name()); this.kind = kind; }
        FailureKind kind() { return kind; }
    }
}
