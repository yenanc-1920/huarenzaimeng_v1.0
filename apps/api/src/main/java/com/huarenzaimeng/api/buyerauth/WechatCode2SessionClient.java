package com.huarenzaimeng.api.buyerauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Fail-closed adapter for WeChat's code2Session identity exchange.
 * Raw codes, secrets, session keys and provider response bodies are never logged or returned.
 */
@Component
@Profile("release-mysql")
final class WechatCode2SessionClient implements WechatCode2SessionPort {
    static final URI OFFICIAL_ENDPOINT = URI.create("https://api.weixin.qq.com/sns/jscode2session");
    private static final int MAX_RESPONSE_CHARS = 16_384;
    private static final Pattern PROVIDER_VALUE = Pattern.compile("[A-Za-z0-9_-]{8,128}");
    private static final Pattern CALL_REF = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private final WechatCode2SessionTransport transport;
    private final ObjectMapper json;
    private final boolean enabled;
    private final String appId;
    private final String appSecret;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    @Autowired
    WechatCode2SessionClient(
            WechatCode2SessionTransport transport,
            ObjectMapper json,
            @Value("${hz.buyer-auth.wechat.enabled:false}") boolean enabled,
            @Value("${hz.buyer-auth.wechat.endpoint:https://api.weixin.qq.com/sns/jscode2session}") String endpoint,
            @Value("${hz.buyer-auth.wechat.app-id:}") String appId,
            @Value("${hz.buyer-auth.wechat.app-secret:}") String appSecret,
            @Value("${hz.buyer-auth.wechat.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${hz.buyer-auth.wechat.read-timeout-ms:3000}") long readTimeoutMs) {
        this.transport = transport;
        this.json = json;
        this.enabled = enabled;
        this.appId = appId == null ? "" : appId;
        this.appSecret = appSecret == null ? "" : appSecret;
        this.connectTimeout = Duration.ofMillis(Math.max(0, connectTimeoutMs));
        this.readTimeout = Duration.ofMillis(Math.max(0, readTimeoutMs));
        if (enabled && (!OFFICIAL_ENDPOINT.toString().equals(endpoint)
                || !validAppId(this.appId) || !validSecret(this.appSecret)
                || !validTimeout(connectTimeoutMs) || !validTimeout(readTimeoutMs))) {
            throw new IllegalStateException("WECHAT_IDENTITY_CONFIGURATION_INCOMPLETE");
        }
    }

    @Override
    public Result exchange(Command command) {
        if (!enabled) return new Unknown("REAL_PROVIDER_ADAPTER_DISABLED");
        if (command == null || !validCode(command.oneTimeCode()) || !validCallRef(command.providerCallRef())) {
            return new Rejected("WECHAT_REQUEST_INVALID");
        }
        WechatCode2SessionTransport.Response response;
        try {
            response = transport.execute(new WechatCode2SessionTransport.Request(
                    OFFICIAL_ENDPOINT, appId, appSecret, command.oneTimeCode(), connectTimeout, readTimeout));
        } catch (WechatCode2SessionTransport.Failure failure) {
            return new Unknown(failure.kind() == WechatCode2SessionTransport.FailureKind.TIMEOUT
                    ? "WECHAT_PROVIDER_TIMEOUT" : "WECHAT_PROVIDER_UNAVAILABLE");
        } catch (RuntimeException failure) {
            return new Unknown("WECHAT_PROVIDER_UNAVAILABLE");
        }
        if (response == null || response.statusCode() != 200) return new Unknown("WECHAT_HTTP_STATUS_UNKNOWN");
        String body = response.body();
        if (body == null || body.isBlank() || body.length() > MAX_RESPONSE_CHARS) {
            return new Unknown("WECHAT_RESPONSE_INVALID");
        }
        try {
            JsonNode root = json.readTree(body);
            if (root == null || !root.isObject()) return new Unknown("WECHAT_RESPONSE_INVALID");
            JsonNode errorCode = root.get("errcode");
            if (errorCode != null && !errorCode.isNull()) {
                if (!errorCode.canConvertToInt()) return new Unknown("WECHAT_RESPONSE_INVALID");
                int code = errorCode.intValue();
                if (code == -1) return new Unknown("WECHAT_PROVIDER_BUSY");
                if (code != 0) return new Rejected("WECHAT_PROVIDER_REJECTED");
            }
            String openId = text(root, "openid");
            String unionId = text(root, "unionid");
            if (!validProviderValue(openId) || (unionId != null && !validProviderValue(unionId))) {
                return new Unknown("WECHAT_IDENTITY_INVALID");
            }
            return new Success(appId, openId, command.providerCallRef());
        } catch (Exception invalidJson) {
            return new Unknown("WECHAT_RESPONSE_INVALID");
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
    private static boolean validAppId(String value) { return value != null && value.matches("[A-Za-z0-9_-]{8,64}"); }
    private static boolean validSecret(String value) { return value != null && value.matches("[A-Za-z0-9_-]{16,128}"); }
    private static boolean validTimeout(long value) { return value >= 100 && value <= 10_000; }
    private static boolean validCode(String value) { return value != null && !value.isBlank() && value.length() <= 256 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0; }
    private static boolean validCallRef(String value) { return value != null && CALL_REF.matcher(value).matches(); }
    private static boolean validProviderValue(String value) { return value != null && PROVIDER_VALUE.matcher(value).matches(); }
}
