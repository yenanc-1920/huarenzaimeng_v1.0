package com.huarenzaimeng.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Profile("release-mysql")
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ReleaseMigrationGateFilter extends OncePerRequestFilter {
    static final String NOT_READY_BODY = "{\"status\":\"SERVICE_STARTING\"}";
    private static final Set<String> CLOSED_GET_ALLOWLIST = Set.of(
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/admin-read/v1/data-integration/readiness",
            "/admin/login",
            "/admin/initialize",
            "/index.html",
            "/assets/index-DnCFE54m.js",
            "/assets/index-F1aN-GtB.css",
            "/assets/logo-C5G5A9bI.png");
    private final ReleaseMigrationState state;

    ReleaseMigrationGateFilter(ReleaseMigrationState state) {
        this.state = state;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return state.isReady();
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (closedRequestIsAllowed(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "5");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(NOT_READY_BODY);
    }

    private static boolean closedRequestIsAllowed(HttpServletRequest request) throws IOException {
        if (request.getDispatcherType() != DispatcherType.REQUEST) return false;
        if (!"GET".equals(request.getMethod())) return false;
        if (request.getQueryString() != null) return false;
        if (request.getHeader("Transfer-Encoding") != null) return false;
        // This is a transport-metadata gate only: ordinary browser GETs normally have no
        // Content-Length (-1). Do not inspect or consume the request body here.
        long contentLength = request.getContentLengthLong();
        if (contentLength != -1 && contentLength != 0) return false;

        String contextPath = request.getContextPath();
        String servletPath = request.getServletPath();
        String requestUri = request.getRequestURI();
        if (contextPath == null || servletPath == null || requestUri == null) return false;
        if (!contextPath.isEmpty()) return false;
        String path = servletPath.isEmpty() ? requestUri : servletPath;
        if (!requestUri.equals(path)) return false;
        return CLOSED_GET_ALLOWLIST.contains(path);
    }
}
