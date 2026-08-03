package com.huarenzaimeng.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class TrustedTestSessionCookieFilter extends OncePerRequestFilter {
    public static final String COOKIE_NAME = "HZM_IT_SESSION";
    public static final String TRUSTED_ADMIN_ROLE = "hz.trusted.admin.role";
    public static final String TRUSTED_SESSION_VERSION = "hz.trusted.session.version";
    public static final String TRUSTED_AUTHORIZATION_SET_REF = "hz.trusted.authorization.setRef";
    public static final String TRUSTED_AUTHORIZATION_EVIDENCE_VERSION = "hz.trusted.authorization.evidenceVersion";
    public static final String TRUSTED_AUTHORIZED_ORDER_REFS = "hz.trusted.authorization.orderRefs";
    private final byte[] buyerToken;
    private final byte[] csToken;
    private final byte[] finToken;
    private final String buyerSubjectRef;
    private final String buyerSessionRef;
    private final long buyerSessionVersion;
    private final String buyerAuthorizationSetRef;
    private final String buyerAuthorizationEvidenceVersion;
    private final List<String> buyerAuthorizedOrderRefs;

    public TrustedTestSessionCookieFilter(@Value("${hz.it-session.buyer-token:}") String buyerToken,
                                          @Value("${hz.it-session.cs-token:}") String csToken,
                                          @Value("${hz.it-session.fin-token:}") String finToken,
                                          @Value("${hz.it-session.buyer-subject-ref:}") String buyerSubjectRef,
                                          @Value("${hz.it-session.buyer-session-ref:}") String buyerSessionRef,
                                          @Value("${hz.it-session.buyer-session-version:0}") long buyerSessionVersion,
                                          @Value("${hz.it-session.buyer-authorization-set-ref:}") String buyerAuthorizationSetRef,
                                          @Value("${hz.it-session.buyer-authorization-evidence-version:}") String evidenceVersion,
                                          @Value("${hz.it-session.buyer-authorized-order-refs:}") String authorizedOrderRefs) {
        this.buyerToken = digestOrNull(buyerToken); this.csToken = digestOrNull(csToken);
        this.finToken = digestOrNull(finToken); this.buyerSubjectRef = buyerSubjectRef;
        this.buyerSessionRef = buyerSessionRef;
        this.buyerSessionVersion = buyerSessionVersion; this.buyerAuthorizationSetRef = buyerAuthorizationSetRef;
        this.buyerAuthorizationEvidenceVersion = evidenceVersion;
        this.buyerAuthorizedOrderRefs = authorizedOrderRefs.isBlank() ? List.of()
                : List.of(authorizedOrderRefs.split(",", -1)).stream().map(String::trim).toList();
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        byte[] supplied = cookie(request);
        if (matches(supplied, buyerToken) && !buyerSubjectRef.isBlank() && !buyerSessionRef.isBlank()
                && buyerSessionVersion > 0 && !buyerAuthorizationSetRef.isBlank()
                && !buyerAuthorizationEvidenceVersion.isBlank() && !buyerAuthorizedOrderRefs.isEmpty()) {
            request.setAttribute(TestAccessTokenFilter.LOCAL_ENVIRONMENT, "LOCAL_SYNTHETIC");
            request.setAttribute(TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF, buyerSubjectRef);
            request.setAttribute(TestAccessTokenFilter.LOCAL_SESSION_REF, buyerSessionRef);
            request.setAttribute(TRUSTED_SESSION_VERSION, buyerSessionVersion);
            request.setAttribute(TRUSTED_AUTHORIZATION_SET_REF, buyerAuthorizationSetRef);
            request.setAttribute(TRUSTED_AUTHORIZATION_EVIDENCE_VERSION, buyerAuthorizationEvidenceVersion);
            request.setAttribute(TRUSTED_AUTHORIZED_ORDER_REFS, buyerAuthorizedOrderRefs);
        } else if (matches(supplied, csToken)) {
            request.setAttribute(TRUSTED_ADMIN_ROLE, "CS");
        } else if (matches(supplied, finToken)) {
            request.setAttribute(TRUSTED_ADMIN_ROLE, "FIN");
        }
        chain.doFilter(request, response);
    }

    private static byte[] cookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (COOKIE_NAME.equals(cookie.getName())) return digestOrNull(cookie.getValue());
        return null;
    }
    private static boolean matches(byte[] supplied, byte[] expected) {
        return supplied != null && expected != null && MessageDigest.isEqual(supplied, expected);
    }
    private static byte[] digestOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
