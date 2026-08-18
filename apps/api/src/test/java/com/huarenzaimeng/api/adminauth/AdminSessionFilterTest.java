package com.huarenzaimeng.api.adminauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSessionFilterTest {
    @Test void commandWithoutSessionIsRejectedBeforeChain() throws Exception {
        AdminAuthService auth=mock(AdminAuthService.class); when(auth.authenticate(null)).thenReturn(Optional.empty());
        AdminSessionFilter filter=new AdminSessionFilter(auth); FilterChain chain=mock(FilterChain.class);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/admin-command/v1/news");
        MockHttpServletResponse response=new MockHttpServletResponse(); filter.doFilter(request,response,chain);
        assertEquals(401,response.getStatus()); verifyNoInteractions(chain);
    }

    @Test void commandWithValidSessionReceivesTrustedServerRole() throws Exception {
        AdminAuthService auth=mock(AdminAuthService.class);
        when(auth.authenticate("token")).thenReturn(Optional.of(new AdminAuthStore.AuthenticatedUser("U1","admin","Admin","SUPER_ADMIN")));
        AdminSessionFilter filter=new AdminSessionFilter(auth); FilterChain chain=mock(FilterChain.class);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/admin-command/v1/news");
        request.setCookies(new Cookie(AdminAuthController.COOKIE,"token")); MockHttpServletResponse response=new MockHttpServletResponse();
        filter.doFilter(request,response,chain);
        verify(chain).doFilter(request,response); assertEquals("SUPER_ADMIN",request.getAttribute(AdminSessionFilter.TRUSTED_ROLE));
    }

    @Test void publicRouteDoesNotInvokeAdminAuthentication() throws Exception {
        AdminAuthService auth=mock(AdminAuthService.class); AdminSessionFilter filter=new AdminSessionFilter(auth);
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/v1/catalog");
        assertTrue(filter.shouldNotFilter(request)); verifyNoInteractions(auth);
    }

    @Test void workflowRouteIsCoveredByAdminAuthentication() throws Exception {
        AdminAuthService auth=mock(AdminAuthService.class);when(auth.authenticate(null)).thenReturn(Optional.empty());
        AdminSessionFilter filter=new AdminSessionFilter(auth);FilterChain chain=mock(FilterChain.class);
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/admin-workflow/v1/reviews");
        MockHttpServletResponse response=new MockHttpServletResponse();filter.doFilter(request,response,chain);
        assertEquals(401,response.getStatus());verifyNoInteractions(chain);
    }
}
