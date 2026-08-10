package com.huarenzaimeng.api.buyerauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BuyerAuthControllerTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void acceptsOnlyCodeAndRequestRefAndReturnsStrictEightFieldSuccess() throws Exception {
        BuyerAuthService service=mock(BuyerAuthService.class);Instant absolute=Instant.parse("2026-08-11T12:00:00Z"),idle=Instant.parse("2026-08-10T14:00:00Z");
        when(service.establish("opaque-code-synthetic","REQUEST-001")).thenReturn(new BuyerAuthService.SessionResult("opaque-token","BUYER-SYN",absolute));
        var response=new BuyerAuthController(service).session(json.readTree("{\"code\":\"opaque-code-synthetic\",\"requestRef\":\"REQUEST-001\"}"),new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        @SuppressWarnings("unchecked") Map<String,Object> body=(Map<String,Object>)response.getBody();
        assertThat(body.keySet()).containsExactlyInAnyOrder("outcome","projectCode","requestRef","subjectRef","token","absoluteExpiresAt","retryClass");
    }

    @Test void rejectsClientIdentityFieldsBeforeServiceCall() throws Exception {
        BuyerAuthService service=mock(BuyerAuthService.class);
        var response=new BuyerAuthController(service).session(json.readTree("{\"code\":\"opaque-code-synthetic\",\"requestRef\":\"REQUEST-001\",\"openid\":\"forged\"}"),new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(400);verifyNoInteractions(service);
    }

    @Test void wrongContentTypeEmptyAndMalformedBodiesUseControlledEnvelopeWithoutServiceCall() throws Exception {
        BuyerAuthService service=mock(BuyerAuthService.class);MockMvc mvc=MockMvcBuilders.standaloneSetup(new BuyerAuthController(service)).setControllerAdvice(new BuyerAuthHttpErrorHandler()).build();
        mvc.perform(post("/buyer-auth/v1/wechat/session").contentType("text/plain").content("opaque"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.projectCode").value("LOGIN_REQUEST_INVALID"));
        mvc.perform(post("/buyer-auth/v1/wechat/session").contentType("application/json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.projectCode").value("LOGIN_REQUEST_INVALID"));
        mvc.perform(post("/buyer-auth/v1/wechat/session").contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.projectCode").value("LOGIN_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test void logoutUnknownNeverClaimsSuccess() {
        BuyerAuthService service=mock(BuyerAuthService.class);when(service.logout("opaque-token")).thenReturn(BuyerAuthStore.LogoutResult.UNKNOWN);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/buyer-auth/v1/session/logout");request.addHeader("Authorization","Bearer opaque-token");
        var response=new BuyerAuthController(service).logout(request);assertThat(response.getStatusCode().value()).isEqualTo(503);assertThat(response.getBody().toString()).contains("BUYER_LOGOUT_RESULT_UNKNOWN").doesNotContain("SUCCEEDED");
    }
}
