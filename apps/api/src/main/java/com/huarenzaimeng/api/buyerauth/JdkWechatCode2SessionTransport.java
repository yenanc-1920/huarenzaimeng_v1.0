package com.huarenzaimeng.api.buyerauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.http.HttpHeaders;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Profile("release-mysql")
final class JdkWechatCode2SessionTransport implements WechatCode2SessionTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdkWechatCode2SessionTransport.class);
    private static final AtomicBoolean CERTIFICATE_DIAGNOSTIC_RECORDED = new AtomicBoolean();

    @Override
    public Response execute(Request request) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(request.connectTimeout())
                    .sslContext(diagnosticSslContext())
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .proxy(new DirectOnlyProxySelector())
                    .build();
            HttpRequest httpRequest = HttpRequest.newBuilder(requestUri(request))
                    .timeout(request.readTimeout()).GET().build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body(), exactOpenApiRule(response.headers()));
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

    static SSLContext diagnosticSslContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new DiagnosticTrustManager(defaultTrustManager())}, new SecureRandom());
            return context;
        } catch (GeneralSecurityException exception) {
            throw new Failure(FailureKind.UNAVAILABLE);
        }
    }

    static X509ExtendedTrustManager defaultTrustManager() throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509ExtendedTrustManager x509) return x509;
        }
        throw new GeneralSecurityException("DEFAULT_X509_TRUST_MANAGER_UNAVAILABLE");
    }

    private static void recordCertificateFailure(X509Certificate[] chain, String authType,
                                                 CertificateException failure, X509Certificate[] acceptedIssuers) {
        if (!CERTIFICATE_DIAGNOSTIC_RECORDED.compareAndSet(false, true)) return;
        try {
            CertificateDiagnostic diagnostic = summarizeCertificateFailure(chain, authType, failure, acceptedIssuers);
            LOGGER.error("WECHAT_TLS_CERTIFICATE_CHAIN_DIAGNOSTIC acceptedIssuerCount={} chainLength={} authType={} failureKind={} certificates={}",
                    diagnostic.acceptedIssuerCount(), diagnostic.chainLength(), diagnostic.authType(),
                    diagnostic.failureKind(), diagnostic.certificates());
        } catch (RuntimeException ignored) {
            LOGGER.error("WECHAT_TLS_CERTIFICATE_CHAIN_DIAGNOSTIC summary_unavailable=true");
        }
    }

    static CertificateDiagnostic summarizeCertificateFailure(X509Certificate[] chain, String authType,
                                                              CertificateException failure,
                                                              X509Certificate[] acceptedIssuers) {
        List<CertificateSummary> certificates = new ArrayList<>();
        if (chain != null) {
            for (int index = 0; index < chain.length; index++) {
                X509Certificate certificate = chain[index];
                if (certificate == null) continue;
                try {
                    String fingerprint = HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
                    certificates.add(new CertificateSummary(index,
                            sanitize(certificate.getSubjectX500Principal().getName()),
                            sanitize(certificate.getIssuerX500Principal().getName()),
                            certificate.getNotBefore().toInstant().toString(),
                            certificate.getNotAfter().toInstant().toString(), fingerprint));
                } catch (Exception ignored) {
                    certificates.add(new CertificateSummary(index, "UNAVAILABLE", "UNAVAILABLE",
                            "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE"));
                }
            }
        }
        return new CertificateDiagnostic(acceptedIssuers == null ? 0 : acceptedIssuers.length,
                chain == null ? 0 : chain.length, sanitize(authType), classify(failure).name(), certificates);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return "UNAVAILABLE";
        String sanitized = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return sanitized.length() <= 512 ? sanitized : sanitized.substring(0, 512);
    }

    record CertificateSummary(int index, String subject, String issuer, String notBefore,
                              String notAfter, String sha256) {}

    record CertificateDiagnostic(int acceptedIssuerCount, int chainLength, String authType,
                                 String failureKind, List<CertificateSummary> certificates) {
        CertificateDiagnostic { certificates = List.copyOf(certificates); }
    }

    private static final class DiagnosticTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager delegate;
        private DiagnosticTrustManager(X509ExtendedTrustManager delegate) { this.delegate = delegate; }
        @Override public void checkClientTrusted(X509Certificate[] c, String a) throws CertificateException { delegate.checkClientTrusted(c, a); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException { delegate.checkClientTrusted(c, a, s); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { delegate.checkClientTrusted(c, a, e); }
        @Override public void checkServerTrusted(X509Certificate[] c, String a) throws CertificateException {
            try { delegate.checkServerTrusted(c, a); } catch (CertificateException failure) { recordCertificateFailure(c, a, failure, getAcceptedIssuers()); throw failure; }
        }
        @Override public void checkServerTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException {
            try { delegate.checkServerTrusted(c, a, s); } catch (CertificateException failure) { recordCertificateFailure(c, a, failure, getAcceptedIssuers()); throw failure; }
        }
        @Override public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException {
            try { delegate.checkServerTrusted(c, a, e); } catch (CertificateException failure) { recordCertificateFailure(c, a, failure, getAcceptedIssuers()); throw failure; }
        }
        @Override public X509Certificate[] getAcceptedIssuers() { return delegate.getAcceptedIssuers(); }
    }

    static URI requestUri(Request request) {
        if (!WechatCode2SessionClient.OFFICIAL_ENDPOINT.equals(request.endpoint())
                && !WechatCode2SessionClient.CLOUDBASE_SAFELINK_ENDPOINT.equals(request.endpoint())) {
            throw new Failure(FailureKind.UNAVAILABLE);
        }
        String query = "appid=" + encode(request.appId())
                + "&secret=" + encode(request.appSecret())
                + "&js_code=" + encode(request.oneTimeCode())
                + "&grant_type=authorization_code";
        return URI.create(request.endpoint() + "?" + query);
    }

    static String exactOpenApiRule(HttpHeaders headers) {
        List<String> values = headers.allValues("x-openapi-rule");
        return values.size() == 1 ? values.get(0) : null;
    }

    private static final class DirectOnlyProxySelector extends ProxySelector {
        @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
        @Override public void connectFailed(URI uri, SocketAddress address, java.io.IOException failure) { }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
