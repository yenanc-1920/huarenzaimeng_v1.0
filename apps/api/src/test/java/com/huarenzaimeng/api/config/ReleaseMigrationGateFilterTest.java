package com.huarenzaimeng.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

class ReleaseMigrationGateFilterTest {
    private final ReleaseMigrationState state = new ReleaseMigrationState();
    private final ReleaseMigrationGateFilter filter = new ReleaseMigrationGateFilter(state);

    @Test void allowsOnlyOperationalReadinessPathsBeforeMigrationIsReady() throws Exception {
        for (String path : java.util.List.of(
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness",
                "/admin-read/v1/data-integration/readiness",
                "/admin/login",
                "/admin/initialize",
                "/index.html",
                "/assets/index-DnCFE54m.js",
                "/assets/index-F1aN-GtB.css",
                "/assets/logo-C5G5A9bI.png")) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setContent(new byte[0]);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    @Test void rejectsUnknownLengthEvenWhenAvailableIsZero() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/login") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public ServletInputStream getInputStream() {
                return delayedStream();
            }
        };

        assertClosedWithoutChain(request);
    }

    @Test void rejectsDelayedBodyBeforeItCanArrive() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/login") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public ServletInputStream getInputStream() {
                return delayedStream();
            }
        };
        assertThat(request.getInputStream().available()).isZero();
        assertThat(request.getInputStream().isFinished()).isFalse();

        assertClosedWithoutChain(request);
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

    private void assertClosedWithoutChain(MockHttpServletRequest request) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verifyNoInteractions(chain);
    }

    private static ServletInputStream delayedStream() {
        return new ServletInputStream() {
            @Override public boolean isFinished() { return false; }
            @Override public boolean isReady() { return false; }
            @Override public void setReadListener(ReadListener readListener) { }
            @Override public int available() { return 0; }
            @Override public int read() { return 'x'; }
        };
    }
}
