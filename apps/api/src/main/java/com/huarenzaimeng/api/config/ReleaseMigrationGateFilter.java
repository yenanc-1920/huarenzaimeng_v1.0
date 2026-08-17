package com.huarenzaimeng.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
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
    private static final String CLOSED_LOGIN_PATH = "/admin-auth/v1/login";
    private static final long CLOSED_LOGIN_MAX_BYTES = 4096;
    private static final Set<String> CLOSED_GET_ALLOWLIST = Set.of(
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/admin-read/v1/data-integration/readiness",
            "/admin/login",
            "/admin/initialize",
            "/index.html",
            "/assets/index-DOcW18zi.js",
            "/assets/index-wztt079N.css",
            "/assets/logo-C5G5A9bI.png");
    private final ReleaseMigrationState state;
    private final boolean nonProductionFunctionReleaseEnabled;

    ReleaseMigrationGateFilter(ReleaseMigrationState state,
            @Value("${hz.dev-function-release.enabled:false}") boolean developmentFunctionReleaseEnabled,
            @Value("${hz.environment.function-release-enabled:false}") boolean environmentFunctionReleaseEnabled) {
        this.state = state;
        this.nonProductionFunctionReleaseEnabled = developmentFunctionReleaseEnabled || environmentFunctionReleaseEnabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return nonProductionFunctionReleaseEnabled || state.isReady();
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
        if (request.getQueryString() != null) return false;
        if (request.getHeader("Transfer-Encoding") != null) return false;

        String contextPath = request.getContextPath();
        String servletPath = request.getServletPath();
        String requestUri = request.getRequestURI();
        if (contextPath == null || servletPath == null || requestUri == null) return false;
        if (!contextPath.isEmpty()) return false;
        String path = servletPath.isEmpty() ? requestUri : servletPath;
        if (!requestUri.equals(path)) return false;

        long contentLength = request.getContentLengthLong();
        if ("GET".equals(request.getMethod())) {
            // This is a transport-metadata gate only: ordinary browser GETs normally have no
            // Content-Length (-1). Do not inspect or consume the request body here.
            return (contentLength == -1 || contentLength == 0) && CLOSED_GET_ALLOWLIST.contains(path);
        }
        if ("POST".equals(request.getMethod()) && CLOSED_LOGIN_PATH.equals(path)) {
            return contentLength > 0
                    && contentLength <= CLOSED_LOGIN_MAX_BYTES
                    && "application/json".equalsIgnoreCase(request.getContentType());
        }
        return false;
    }
}
