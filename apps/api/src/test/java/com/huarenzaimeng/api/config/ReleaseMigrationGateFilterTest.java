package com.huarenzaimeng.api.config;

import jakarta.servlet.FilterChain;
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

    @Test void blocksEveryHttpPathBeforeMigrationIsReady() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).isEqualTo(ReleaseMigrationGateFilter.NOT_READY_BODY);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        verifyNoInteractions(chain);
    }

    @Test void continuesOnlyAfterMigrationIsReady() throws Exception {
        state.ready();
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-read/v1/pages/A120");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
