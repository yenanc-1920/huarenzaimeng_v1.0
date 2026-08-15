package com.huarenzaimeng.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;

class ReleaseMigrationGateFilterTest {
    private final ReleaseMigrationState state = new ReleaseMigrationState();
    private final ReleaseMigrationGateFilter filter = new ReleaseMigrationGateFilter(state, false);

    @Test void explicitDevelopmentFunctionReleaseBypassesClosedGate() throws Exception {
        ReleaseMigrationGateFilter developmentFilter = new ReleaseMigrationGateFilter(state, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-read/v1/pages/A120");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        developmentFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test void allowsExactBrowserShapedGetAllowlistWithoutContentLength() throws Exception {
        for (String path : java.util.List.of(
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness",
                "/admin-read/v1/data-integration/readiness",
                "/admin/login",
                "/admin/initialize",
                "/index.html",
                "/assets/index-DOcW18zi.js",
                "/assets/index-wztt079N.css",
                "/assets/logo-C5G5A9bI.png")) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    @Test void allowsExactAllowlistGetWithExplicitZeroContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/login");
        request.setContent(new byte[0]);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test void allowsOnlyBoundedJsonLoginPostAndPreservesControllerCookieChain() throws Exception {
        MockHttpServletRequest request = jsonLoginRequest("{\"username\":\"admin\",\"password\":\"test-only\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            ((jakarta.servlet.http.HttpServletResponse) invocation.getArgument(1))
                    .addHeader("Set-Cookie", "HZ_ADMIN_SESSION=downstream-controller; HttpOnly; Secure; SameSite=Strict");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("Set-Cookie")).startsWith("HZ_ADMIN_SESSION=");
    }

    @Test void rejectsEveryNonCanonicalLoginPostBeforeControllerChain() throws Exception {
        java.util.List<MockHttpServletRequest> invalid = new java.util.ArrayList<>();
        invalid.add(jsonRequest("POST", "/admin-auth/v1/bootstrap", "{}"));
        invalid.add(jsonRequest("PUT", "/admin-auth/v1/login", "{}"));
        MockHttpServletRequest query = jsonLoginRequest("{}"); query.setQueryString("x=1"); invalid.add(query);
        MockHttpServletRequest wrongType = jsonLoginRequest("{}"); wrongType.setContentType("text/plain"); invalid.add(wrongType);
        MockHttpServletRequest transfer = jsonLoginRequest("{}"); transfer.addHeader("Transfer-Encoding", "chunked"); invalid.add(transfer);
        invalid.add(jsonLoginRequest(""));
        invalid.add(jsonLoginRequest("x".repeat(4097)));

        for (MockHttpServletRequest request : invalid) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            verifyNoInteractions(chain);
        }
    }

    @Test void blocksBusinessPathsBeforeMigrationIsReady() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/admin-read/v1/pages/A120"), response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).isEqualTo(ReleaseMigrationGateFilter.NOT_READY_BODY);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verifyNoInteractions(chain);
    }

    @Test void blocksAsyncErrorAndForwardDispatchesBeforeMigrationIsReady() throws Exception {
        for (DispatcherType dispatcher : java.util.List.of(
                DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.FORWARD)) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/login");
            request.setDispatcherType(dispatcher);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            verifyNoInteractions(chain);
        }
    }

    @Test void rejectsQueryBodyAndPathMetadataBeforeAnyAllowedChain() throws Exception {
        java.util.List<MockHttpServletRequest> invalid = new java.util.ArrayList<>();
        MockHttpServletRequest query = new MockHttpServletRequest("GET", "/admin/login");
        query.setQueryString("x=1"); invalid.add(query);
        MockHttpServletRequest body = new MockHttpServletRequest("GET", "/admin/login");
        body.setContent("x".getBytes(java.nio.charset.StandardCharsets.UTF_8)); invalid.add(body);
        MockHttpServletRequest chunked = new MockHttpServletRequest("GET", "/admin/login");
        chunked.addHeader("Transfer-Encoding", "chunked"); invalid.add(chunked);
        MockHttpServletRequest context = new MockHttpServletRequest("GET", "/ctx/admin/login");
        context.setContextPath("/ctx"); context.setServletPath("/admin/login"); invalid.add(context);
        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", "/admin/login");
        servlet.setServletPath("/admin/initialize"); invalid.add(servlet);
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/admin/%6cogin");
        raw.setServletPath("/admin/login"); invalid.add(raw);

        for (MockHttpServletRequest request : invalid) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            verifyNoInteractions(chain);
        }
    }

    @Test void staticAllowlistIsExactAndDoesNotAdmitUnreferencedAssets() throws Exception {
        for (String path : java.util.List.of(
                "/assets/index-old.js", "/assets/other.css", "/favicon.ico", "/src/main.ts")) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", path), response, chain);
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            verifyNoInteractions(chain);
        }
    }

    @Test void continuesOnlyAfterMigrationIsReady() throws Exception {
        state.ready();
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-read/v1/pages/A120");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private static MockHttpServletRequest jsonLoginRequest(String body) {
        return jsonRequest("POST", "/admin-auth/v1/login", body);
    }

    private static MockHttpServletRequest jsonRequest(String method, String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setContentType("application/json");
        request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }

}
