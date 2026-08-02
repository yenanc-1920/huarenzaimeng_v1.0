package com.huarenzaimeng.api.config;

import com.huarenzaimeng.api.LocalSyntheticIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public final class TestAccessTokenFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-HZM-Test-Access-Token";
    public static final String LOCAL_PROJECT_SUBJECT_REF = "hz.trusted.local.projectSubjectRef";
    public static final String LOCAL_SESSION_REF = "hz.trusted.local.sessionRef";
    public static final String LOCAL_ENVIRONMENT = "hz.trusted.local.environment";
    private static final byte[] UNAUTHORIZED_BODY =
            "{\"status\":\"UNAUTHORIZED\"}".getBytes(StandardCharsets.UTF_8);

    private final byte[] configuredTokenDigest;
    private final LocalSyntheticIdentity localIdentity;

    TestAccessTokenFilter(@Value("${hz.test-access-token:}") String configuredToken) {
        this.configuredTokenDigest = configuredToken.isBlank() ? null : digest(configuredToken);
        this.localIdentity = configuredTokenDigest == null ? null
                : LocalSyntheticIdentity.fromDigest(configuredTokenDigest);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (configuredTokenDigest == null) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("GET".equals(request.getMethod()) && path.equals("/api/v1/home/temporal-overview")) {
            return true;
        }
        return !(path.equals("/api/v1") || path.startsWith("/api/v1/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String suppliedToken = request.getHeader(HEADER_NAME);
        boolean authorized = suppliedToken != null
                && MessageDigest.isEqual(configuredTokenDigest, digest(suppliedToken));
        if (!authorized) {
            if (isPaymentIntentResultQuery(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getOutputStream().write(UNAUTHORIZED_BODY);
            return;
        }
        request.setAttribute(LOCAL_PROJECT_SUBJECT_REF, localIdentity.projectSubjectRef());
        request.setAttribute(LOCAL_SESSION_REF, localIdentity.sessionRef());
        request.setAttribute(LOCAL_ENVIRONMENT, "LOCAL_SYNTHETIC");
        filterChain.doFilter(request, response);
    }

    private static boolean isPaymentIntentResultQuery(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "GET".equals(request.getMethod())
                && path.matches("/api/v1/orders/[^/]+/payment-intents/result");
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
