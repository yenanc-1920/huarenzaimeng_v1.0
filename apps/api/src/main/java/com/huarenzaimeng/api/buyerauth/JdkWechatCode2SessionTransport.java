package com.huarenzaimeng.api.buyerauth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
@Profile("release-mysql")
final class JdkWechatCode2SessionTransport implements WechatCode2SessionTransport {
    @Override
    public Response execute(Request request) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(request.connectTimeout())
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            HttpRequest httpRequest = HttpRequest.newBuilder(requestUri(request))
                    .timeout(request.readTimeout()).GET().build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Failure(FailureKind.UNAVAILABLE);
        } catch (Exception unavailable) {
            throw new Failure(classify(unavailable));
        }
    }

    static FailureKind classify(Throwable failure) {
        boolean tlsFailure = false;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof java.net.http.HttpTimeoutException) return FailureKind.TIMEOUT;
            if (current instanceof java.net.UnknownHostException
                    || current instanceof java.nio.channels.UnresolvedAddressException) return FailureKind.DNS;
            if (current instanceof java.security.cert.CertificateExpiredException) {
                return FailureKind.TLS_CERTIFICATE_EXPIRED;
            }
            if (current instanceof java.security.cert.CertificateNotYetValidException) {
                return FailureKind.TLS_CERTIFICATE_NOT_YET_VALID;
            }
            if (current instanceof java.security.cert.CertPathValidatorException validatorFailure) {
                java.security.cert.CertPathValidatorException.Reason reason = validatorFailure.getReason();
                if (reason == java.security.cert.CertPathValidatorException.BasicReason.EXPIRED) {
                    return FailureKind.TLS_CERTIFICATE_EXPIRED;
                }
                if (reason == java.security.cert.CertPathValidatorException.BasicReason.NOT_YET_VALID) {
                    return FailureKind.TLS_CERTIFICATE_NOT_YET_VALID;
                }
                if (reason == java.security.cert.CertPathValidatorException.BasicReason.REVOKED) {
                    return FailureKind.TLS_CERTIFICATE_REVOKED;
                }
                if (reason == java.security.cert.CertPathValidatorException.BasicReason.UNDETERMINED_REVOCATION_STATUS) {
                    return FailureKind.TLS_CERTIFICATE_REVOCATION_UNDETERMINED;
                }
                if (reason == java.security.cert.CertPathValidatorException.BasicReason.ALGORITHM_CONSTRAINED) {
                    return FailureKind.TLS_CERTIFICATE_ALGORITHM_CONSTRAINED;
                }
                return FailureKind.TLS_CERTIFICATE;
            }
            // The JDK's PKIX builder type is internal and cannot be imported. Its stable
            // simple name is sufficient for a non-sensitive diagnostic classification.
            if ("SunCertPathBuilderException".equals(current.getClass().getSimpleName())) {
                return FailureKind.TLS_CERTIFICATE_PATH_BUILD;
            }
            if (current instanceof java.security.cert.CertificateException
                    && isHostnameMismatch(current.getMessage())) {
                return FailureKind.TLS_HOSTNAME_MISMATCH;
            }
            if (current instanceof java.security.cert.CertificateException
                    || current instanceof javax.net.ssl.SSLPeerUnverifiedException) return FailureKind.TLS_CERTIFICATE;
            if (current instanceof javax.net.ssl.SSLException) tlsFailure = true;
            if (current instanceof java.net.ConnectException) return FailureKind.CONNECTION;
        }
        return tlsFailure ? FailureKind.TLS_HANDSHAKE : FailureKind.UNAVAILABLE;
    }

    private static boolean isHostnameMismatch(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("subject alternative")
                || normalized.contains("no name matching")
                || normalized.contains("doesn't match")
                || normalized.contains("does not match");
    }

    static URI requestUri(Request request) {
        if (!WechatCode2SessionClient.OFFICIAL_ENDPOINT.equals(request.endpoint())) {
            throw new Failure(FailureKind.UNAVAILABLE);
        }
        String query = "appid=" + encode(request.appId())
                + "&secret=" + encode(request.appSecret())
                + "&js_code=" + encode(request.oneTimeCode())
                + "&grant_type=authorization_code";
        return URI.create(request.endpoint() + "?" + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
