package com.huarenzaimeng.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Profile("release-mysql")
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ReleaseMigrationGateFilter extends OncePerRequestFilter {
    static final String NOT_READY_BODY = "{\"status\":\"SERVICE_STARTING\"}";
    private final ReleaseMigrationState state;

    ReleaseMigrationGateFilter(ReleaseMigrationState state) {
        this.state = state;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/actuator/health")
                || path.equals("/actuator/health/liveness")
                || path.equals("/actuator/health/readiness")
                || path.equals("/admin-read/v1/data-integration/readiness");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!state.isReady()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", "5");
            response.getWriter().write(NOT_READY_BODY);
            return;
        }
        chain.doFilter(request, response);
    }
}
