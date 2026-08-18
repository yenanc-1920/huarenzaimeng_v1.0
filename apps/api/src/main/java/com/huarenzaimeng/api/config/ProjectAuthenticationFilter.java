package com.huarenzaimeng.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@Profile({"mock","test","local-synthetic"})
public final class ProjectAuthenticationFilter extends OncePerRequestFilter {
    public static final String ROLE = "hz.trusted.project.role";
    public static final String ACTOR = "hz.trusted.project.actor";
    public static final String AUTHORIZATION_REF = "hz.trusted.project.authorizationRef";
    private static final Logger LOG = LoggerFactory.getLogger(ProjectAuthenticationFilter.class);
    private static final byte[] REJECTED = ("{\"status\":\"REJECTED\",\"projectCode\":"
            + "\"AUTHORIZATION_REQUIRED\",\"data\":null}").getBytes(StandardCharsets.UTF_8);

    private final byte[] tokenDigest;
    private final String trustedActor;
    private final String authorizationRef;

    ProjectAuthenticationFilter(@Value("${hz.project-auth.content-token:}") String token,
                                @Value("${hz.project-auth.content-actor:CONTENT-SERVICE}") String actor,
                                @Value("${hz.project-auth.authorization-ref:AUTH-CONTENT-MOCK}") String authRef) {
        tokenDigest = token.isBlank() ? null : digest(token);
        trustedActor = actor;
        authorizationRef = authRef;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/project-api/v1/internal/content/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(TestAccessTokenFilter.HEADER_NAME);
        boolean allowed = tokenDigest != null && supplied != null
                && MessageDigest.isEqual(tokenDigest, digest(supplied));
        if (!allowed) {
            LOG.warn("controlled_authorization_audit scope=PUBLIC_CONTENT_ADMIN authorizationRef=NONE "
                    + "result=REJECTED evidenceRef=PROJECT_AUTH_FILTER");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getOutputStream().write(REJECTED);
            return;
        }
        request.setAttribute(ROLE, "ROLE-CONTENT");
        request.setAttribute(ACTOR, trustedActor);
        request.setAttribute(AUTHORIZATION_REF, authorizationRef);
        chain.doFilter(request, response);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
