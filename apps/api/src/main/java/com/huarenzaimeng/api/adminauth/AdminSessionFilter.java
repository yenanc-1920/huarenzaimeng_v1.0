package com.huarenzaimeng.api.adminauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
@Profile("release-mysql")
@Order(10)
public final class AdminSessionFilter extends OncePerRequestFilter {
    public static final String TRUSTED_USER = "hz.admin.user";
    public static final String TRUSTED_ROLE = "hz.admin.role";
    private final AdminAuthService auth;
    AdminSessionFilter(AdminAuthService auth) { this.auth = auth; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/admin-read/") && !path.startsWith("/admin-command/") && !path.startsWith("/admin-workflow/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String token = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(cookie -> AdminAuthController.COOKIE.equals(cookie.getName())).map(Cookie::getValue).findFirst().orElse(null);
        var user = auth.authenticate(token);
        if (user.isEmpty()) {
            response.setStatus(401); response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"UNAUTHENTICATED\"}");
            return;
        }
        request.setAttribute(TRUSTED_USER, user.get());
        request.setAttribute(TRUSTED_ROLE, user.get().roleCode());
        chain.doFilter(request, response);
    }
}
