package com.huarenzaimeng.api.buyerauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class WechatCode2SessionClientTest {
    private static final String APP = "wx1234567890abcdef";
    private static final String SECRET = "secret_1234567890abcdef1234567890";
    private static final String CODE = "one-time-code+/=";
    private static final String CALL = "CALL-12345678";

    @Test void disabledIsZeroTransportAndNeedsNoCredentials() {
        FakeTransport transport = new FakeTransport(new WechatCode2SessionTransport.Response(200, "{}"));
        WechatCode2SessionClient client = client(transport, false, "", "", 2000, 3000);
        assertThat(client.exchange(new WeChatIdentityPort.Command(CODE, CALL)))
                .isEqualTo(new WeChatIdentityPort.Unknown("REAL_PROVIDER_ADAPTER_DISABLED"));
        assertThat(transport.calls).isZero();
    }

    @Test void enabledConfigurationIsStrictAndOfficialEndpointOnly() {
        FakeTransport transport = new FakeTransport(null);
        assertThatThrownBy(() -> new WechatCode2SessionClient(transport,new ObjectMapper(),true,
                "http://api.weixin.qq.com/sns/jscode2session",APP,SECRET,2000,3000))
                .hasMessage("WECHAT_IDENTITY_CONFIGURATION_INCOMPLETE");
        assertThatThrownBy(() -> client(transport,true,"",SECRET,2000,3000)).hasMessage("WECHAT_IDENTITY_CONFIGURATION_INCOMPLETE");
        assertThatThrownBy(() -> client(transport,true,APP,"",2000,3000)).hasMessage("WECHAT_IDENTITY_CONFIGURATION_INCOMPLETE");
        assertThatThrownBy(() -> client(transport,true,APP,SECRET,99,3000)).hasMessage("WECHAT_IDENTITY_CONFIGURATION_INCOMPLETE");
        assertThatThrownBy(() -> client(transport,true,APP,SECRET,2000,10001)).hasMessage("WECHAT_IDENTITY_CONFIGURATION_INCOMPLETE");
        assertThat(transport.calls).isZero();
    }

    @Test void successBindsOfficialRequestAppIdOpenIdAndEvidenceWithoutLeakingUnionOrSessionKey() {
        String body="{\"openid\":\"openid_12345678\",\"unionid\":\"unionid_12345678\",\"session_key\":\"provider-session-secret\",\"errcode\":0}";
        FakeTransport transport=new FakeTransport(new WechatCode2SessionTransport.Response(200,body));
        WeChatIdentityPort.Result result=client(transport,true,APP,SECRET,2000,3000)
                .exchange(new WeChatIdentityPort.Command(CODE,CALL));
        assertThat(result).isEqualTo(new WeChatIdentityPort.Success(APP,"openid_12345678",CALL));
        assertThat(transport.calls).isOne();
        WechatCode2SessionTransport.Request request=transport.last;
        assertThat(request.endpoint()).isEqualTo(WechatCode2SessionClient.OFFICIAL_ENDPOINT);
        assertThat(request.appId()).isEqualTo(APP);
        assertThat(request.appSecret()).isEqualTo(SECRET);
        assertThat(request.oneTimeCode()).isEqualTo(CODE);
        assertThat(request.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(request.readTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(request.toString()).contains("[REDACTED]").doesNotContain(APP,SECRET,CODE);
        URI uri=JdkWechatCode2SessionTransport.requestUri(request);
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("api.weixin.qq.com");
        assertThat(uri.getPath()).isEqualTo("/sns/jscode2session");
        assertThat(uri.getRawQuery()).contains("appid="+APP,"secret=secret_1234567890abcdef1234567890",
                "js_code=one-time-code%2B%2F%3D","grant_type=authorization_code");
        assertThat(result.toString()).doesNotContain(SECRET,CODE,"provider-session-secret","unionid_12345678");
    }

    @Test void timeoutUnavailableAndHttpFailuresAreControlledAndRedacted() {
        FakeTransport timeout=new FakeTransport(new WechatCode2SessionTransport.Failure(WechatCode2SessionTransport.FailureKind.TIMEOUT));
        assertThat(client(timeout,true,APP,SECRET,2000,3000).exchange(command()))
                .isEqualTo(new WeChatIdentityPort.Unknown("WECHAT_PROVIDER_TIMEOUT"));
        assertThat(client(new FakeTransport(new WechatCode2SessionTransport.Failure(WechatCode2SessionTransport.FailureKind.DNS)),true,APP,SECRET,2000,3000).exchange(command()))
                .isEqualTo(new WeChatIdentityPort.Unknown("WECHAT_PROVIDER_DNS_FAILURE"));
        assertThat(client(new FakeTransport(new WechatCode2SessionTransport.Failure(WechatCode2SessionTransport.FailureKind.TLS_CERTIFICATE)),true,APP,SECRET,2000,3000).exchange(command()))
                .isEqualTo(new WeChatIdentityPort.Unknown("WECHAT_PROVIDER_TLS_CERTIFICATE_FAILURE"));
        assertThat(client(new FakeTransport(new WechatCode2SessionTransport.Failure(WechatCode2SessionTransport.FailureKind.TLS_CERTIFICATE_PATH_BUILD)),true,APP,SECRET,2000,3000).exchange(command()))
                .isEqualTo(new WeChatIdentityPort.Unknown("WECHAT_PROVIDER_TLS_CERTIFICATE_PATH_BUILD_FAILURE"));
        assertThat(client(new FakeTransport(new WechatCode2SessionTransport.Failure(WechatCode2SessionTransport.FailureKind.TLS_HOSTNAME_MISMATCH)),true,APP,SECRET,2000,3000).exchange(command()))
                .isEqualTo(new WeChatIdentityPort.Unknown("WECHAT_PROVIDER_TLS_HOSTNAME_MISMATCH"));
        assertThat(client(new FakeTransport(new WechatCode2SessionTransport.Failure(WechatCode2SessionTransport.FailureKind.TLS_HANDSHAKE)),true,APP,SECRET,2000,3000).exchange(command()))
                .isEqualTo(new WeChatIdentityPort.Unknown("WECHAT_PROVIDER_TLS_HANDSHAKE_FAILURE"));
        assertThat(client(new FakeTransport(new WechatCode2SessionTransport.Failure(WechatCode2SessionTransport.FailureKind.CONNECTION)),true,APP,SECRET,2000,3000).exchange(command()))
                .isEqualTo(new WeChatIdentityPort.Unknown("WECHAT_PROVIDER_CONNECTION_FAILED"));
        FakeTransport unavailable=new FakeTransport(new IllegalStateException("leak "+SECRET+" "+CODE));
        WeChatIdentityPort.Result unavailableResult=client(unavailable,true,APP,SECRET,2000,3000).exchange(command());
        assertThat(unavailableResult).isEqualTo(new WeChatIdentityPort.Unknown("WECHAT_PROVIDER_UNAVAILABLE"));
        assertThat(unavailableResult.toString()).doesNotContain(SECRET,CODE,"leak");
        assertThat(client(new FakeTransport(new WechatCode2SessionTransport.Response(503,"secret="+SECRET)),true,APP,SECRET,2000,3000).exchange(command()))
                .isEqualTo(new WeChatIdentityPort.Unknown("WECHAT_HTTP_STATUS_UNKNOWN"));
    }

    @Test void certificateFailuresAreClassifiedWithoutExposingCertificateDetails() {
        assertThat(JdkWechatCode2SessionTransport.classify(new java.security.cert.CertificateExpiredException("sensitive")))
                .isEqualTo(WechatCode2SessionTransport.FailureKind.TLS_CERTIFICATE_EXPIRED);
        assertThat(JdkWechatCode2SessionTransport.classify(new java.security.cert.CertificateNotYetValidException("sensitive")))
                .isEqualTo(WechatCode2SessionTransport.FailureKind.TLS_CERTIFICATE_NOT_YET_VALID);
        assertThat(JdkWechatCode2SessionTransport.classify(new java.security.cert.CertPathValidatorException(
                "sensitive", null, null, -1, java.security.cert.CertPathValidatorException.BasicReason.ALGORITHM_CONSTRAINED)))
                .isEqualTo(WechatCode2SessionTransport.FailureKind.TLS_CERTIFICATE_ALGORITHM_CONSTRAINED);
        assertThat(JdkWechatCode2SessionTransport.classify(new SunCertPathBuilderException()))
                .isEqualTo(WechatCode2SessionTransport.FailureKind.TLS_CERTIFICATE_PATH_BUILD);
        javax.net.ssl.SSLHandshakeException hostnameMismatch =
                new javax.net.ssl.SSLHandshakeException("certificate validation failed");
        hostnameMismatch.initCause(new java.security.cert.CertificateException(
                "No subject alternative DNS name matching api.weixin.qq.com found."));
        assertThat(JdkWechatCode2SessionTransport.classify(hostnameMismatch))
                .isEqualTo(WechatCode2SessionTransport.FailureKind.TLS_HOSTNAME_MISMATCH);
    }

    @Test void invalidJsonMissingIdentityAndOversizedBodyFailClosed() {
        assertResult("not-json",new WeChatIdentityPort.Unknown("WECHAT_RESPONSE_INVALID"));
        assertResult("{}",new WeChatIdentityPort.Unknown("WECHAT_IDENTITY_INVALID"));
        assertResult("{\"openid\":123}",new WeChatIdentityPort.Unknown("WECHAT_IDENTITY_INVALID"));
        assertResult("{\"openid\":\"openid_12345678\",\"unionid\":\"bad value\"}",new WeChatIdentityPort.Unknown("WECHAT_IDENTITY_INVALID"));
        assertResult("x".repeat(16_385),new WeChatIdentityPort.Unknown("WECHAT_RESPONSE_INVALID"));
    }

    @Test void providerErrcodeDistinguishesBusyRejectedAndMalformed() {
        assertResult("{\"errcode\":-1,\"errmsg\":\"busy secret\"}",new WeChatIdentityPort.Unknown("WECHAT_PROVIDER_BUSY"));
        assertResult("{\"errcode\":40029,\"errmsg\":\"invalid code\"}",new WeChatIdentityPort.Rejected("WECHAT_PROVIDER_REJECTED"));
        assertResult("{\"errcode\":\"40029\"}",new WeChatIdentityPort.Unknown("WECHAT_RESPONSE_INVALID"));
    }

    @Test void malformedCommandNeverCallsTransport() {
        FakeTransport transport=new FakeTransport(new AssertionError("must not call"));
        WechatCode2SessionClient client=client(transport,true,APP,SECRET,2000,3000);
        assertThat(client.exchange(null)).isEqualTo(new WeChatIdentityPort.Rejected("WECHAT_REQUEST_INVALID"));
        assertThat(client.exchange(new WeChatIdentityPort.Command("\n",CALL))).isEqualTo(new WeChatIdentityPort.Rejected("WECHAT_REQUEST_INVALID"));
        assertThat(client.exchange(new WeChatIdentityPort.Command(CODE,"bad"))).isEqualTo(new WeChatIdentityPort.Rejected("WECHAT_REQUEST_INVALID"));
        assertThat(transport.calls).isZero();
    }

    private static void assertResult(String body,WeChatIdentityPort.Result expected){
        assertThat(client(new FakeTransport(new WechatCode2SessionTransport.Response(200,body)),true,APP,SECRET,2000,3000).exchange(command())).isEqualTo(expected);
    }
    private static WeChatIdentityPort.Command command(){return new WeChatIdentityPort.Command(CODE,CALL);}
    private static WechatCode2SessionClient client(WechatCode2SessionTransport transport,boolean enabled,String appId,String secret,long connect,long read){
        return new WechatCode2SessionClient(transport,new ObjectMapper(),enabled,WechatCode2SessionClient.OFFICIAL_ENDPOINT.toString(),appId,secret,connect,read);
    }
    private static final class FakeTransport implements WechatCode2SessionTransport {
        final Object outcome; int calls; Request last;
        FakeTransport(Object outcome){this.outcome=outcome;}
        @Override public Response execute(Request request){calls++;last=request;if(outcome instanceof Error e)throw e;if(outcome instanceof RuntimeException e)throw e;return (Response)outcome;}
    }
    private static final class SunCertPathBuilderException extends Exception {}
}
