package com.huarenzaimeng.api.config;

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
    private static final byte[] UNAUTHORIZED_BODY =
            "{\"status\":\"UNAUTHORIZED\"}".getBytes(StandardCharsets.UTF_8);

    private final byte[] configuredTokenDigest;

    TestAccessTokenFilter(@Value("${hz.test-access-token:}") String configuredToken) {
        this.configuredTokenDigest = configuredToken.isBlank() ? null : digest(configuredToken);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (configuredTokenDigest == null) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals("/api/v1") || path.startsWith("/api/v1/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String suppliedToken = request.getHeader(HEADER_NAME);
        boolean authorized = suppliedToken != null
                && MessageDigest.isEqual(configuredTokenDigest, digest(suppliedToken));
        if (!authorized) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getOutputStream().write(UNAUTHORIZED_BODY);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
