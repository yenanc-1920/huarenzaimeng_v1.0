package com.huarenzaimeng.api.buyerauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BuyerAuthControllerTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void requiresExactConsentEnvelopeAndReturnsVersionedSessionContract() throws Exception {
        BuyerAuthService service=mock(BuyerAuthService.class);Instant absolute=Instant.parse("2026-08-11T12:00:00Z"),idle=Instant.parse("2026-08-10T14:00:00Z");
        when(service.establish(argThat(c->c!=null&&c.code().equals("opaque-code-synthetic")&&c.requestRef().equals("REQUEST-001")
                &&c.guestRef().equals("GUEST-000001")&&c.userAgreementVersion().equals("UA-V1")
                &&c.privacyPolicyVersion().equals("PP-V1")&&c.userAgreementAccepted()&&c.privacyPolicyAccepted())))
                .thenReturn(new BuyerAuthService.SessionResult("opaque-token","BUYER-SYN",absolute));
        var response=new BuyerAuthController(service).session(json.readTree("{\"code\":\"opaque-code-synthetic\",\"requestRef\":\"REQUEST-001\",\"guestRef\":\"GUEST-000001\",\"consent\":{\"userAgreementVersion\":\"UA-V1\",\"privacyPolicyVersion\":\"PP-V1\",\"userAgreementAccepted\":true,\"privacyPolicyAccepted\":true}}"),new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        @SuppressWarnings("unchecked") Map<String,Object> body=(Map<String,Object>)response.getBody();
        assertThat(body.keySet()).containsExactlyInAnyOrder("outcome","projectCode","requestRef","subjectRef","token","absoluteExpiresAt","retryClass","schemaVersion","consentState");
        assertThat(body).containsEntry("schemaVersion","BUYER_SESSION_V2").containsEntry("consentState","VALID");
    }

    @Test void rejectsClientIdentityFieldsBeforeServiceCall() throws Exception {
        BuyerAuthService service=mock(BuyerAuthService.class);
        var response=new BuyerAuthController(service).session(json.readTree("{\"code\":\"opaque-code-synthetic\",\"requestRef\":\"REQUEST-001\",\"guestRef\":\"GUEST-000001\",\"consent\":{\"userAgreementVersion\":\"UA-V1\",\"privacyPolicyVersion\":\"PP-V1\",\"userAgreementAccepted\":true,\"privacyPolicyAccepted\":true},\"openid\":\"forged\"}"),new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(400);verifyNoInteractions(service);
    }

    @Test void exposesOnlySafeProviderFailureClassAsServiceUnavailable() throws Exception {
        BuyerAuthService service=mock(BuyerAuthService.class);
        when(service.establish(any())).thenThrow(new BuyerAuthService.Rejected("WECHAT_PROVIDER_TIMEOUT"));
        var response=new BuyerAuthController(service).session(json.readTree("{\"code\":\"opaque-code-synthetic\",\"requestRef\":\"REQUEST-001\",\"guestRef\":\"GUEST-000001\",\"consent\":{\"userAgreementVersion\":\"UA-V1\",\"privacyPolicyVersion\":\"PP-V1\",\"userAgreementAccepted\":true,\"privacyPolicyAccepted\":true}}"),new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().toString()).contains("WECHAT_PROVIDER_TIMEOUT").doesNotContain("opaque-code-synthetic");
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

    @Test void anonymousSessionUsesStrictConsentEnvelopeAndSevenFieldResponse() throws Exception {
        BuyerAuthService buyer=mock(BuyerAuthService.class);AnonymousSessionService anonymous=mock(AnonymousSessionService.class);
        Instant expiry=Instant.parse("2026-08-23T00:00:00Z");
        when(anonymous.establish(argThat(c->c!=null&&c.requestRef().equals("ANON-REQUEST-001")
                &&c.guestRef().equals("GUEST-000001")&&c.userAgreementVersion().equals("UA-V1")
                &&c.privacyPolicyVersion().equals("PP-V1")&&c.userAgreementAccepted()&&c.privacyPolicyAccepted())))
                .thenReturn(new AnonymousSessionService.SessionResult("ANON-REQUEST-001","ANON-SUBJECT-1","anonymous-token",expiry));
        var response=new BuyerAuthController(buyer,null,anonymous).anonymous(json.readTree("{\"requestRef\":\"ANON-REQUEST-001\",\"guestRef\":\"GUEST-000001\",\"consent\":{\"userAgreementVersion\":\"UA-V1\",\"privacyPolicyVersion\":\"PP-V1\",\"userAgreementAccepted\":true,\"privacyPolicyAccepted\":true}}"),new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        @SuppressWarnings("unchecked") Map<String,Object> body=(Map<String,Object>)response.getBody();
        assertThat(body.keySet()).containsExactlyInAnyOrder("outcome","projectCode","requestRef","subjectRef","token","absoluteExpiresAt","retryClass");
        assertThat(body).containsEntry("projectCode","ANONYMOUS_SESSION_CREATED").containsEntry("subjectRef","ANON-SUBJECT-1");
    }

    @Test void anonymousSessionRejectsAdditionalFieldsAndMalformedMediaBeforeService() throws Exception {
        BuyerAuthService buyer=mock(BuyerAuthService.class);AnonymousSessionService anonymous=mock(AnonymousSessionService.class);
        var controller=new BuyerAuthController(buyer,null,anonymous);
        var response=controller.anonymous(json.readTree("{\"requestRef\":\"ANON-REQUEST-001\",\"guestRef\":\"GUEST-000001\",\"openid\":\"forged\",\"consent\":{\"userAgreementVersion\":\"UA-V1\",\"privacyPolicyVersion\":\"PP-V1\",\"userAgreementAccepted\":true,\"privacyPolicyAccepted\":true}}"),new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new BuyerAuthHttpErrorHandler()).build();
        mvc.perform(post("/buyer-auth/v1/anonymous-sessions").contentType("text/plain").content("opaque"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.projectCode").value("ANONYMOUS_SESSION_REQUEST_INVALID"));
        mvc.perform(post("/buyer-auth/v1/anonymous-sessions").contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.projectCode").value("ANONYMOUS_SESSION_REQUEST_INVALID"));
        verifyNoInteractions(anonymous);
    }

    @Test void logoutUnknownNeverClaimsSuccess() {
        BuyerAuthService service=mock(BuyerAuthService.class);when(service.logout("opaque-token")).thenReturn(BuyerAuthStore.LogoutResult.UNKNOWN);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/buyer-auth/v1/session/logout");request.addHeader("Authorization","Bearer opaque-token");
        var response=new BuyerAuthController(service).logout(request);assertThat(response.getStatusCode().value()).isEqualTo(503);assertThat(response.getBody().toString()).contains("BUYER_LOGOUT_RESULT_UNKNOWN").doesNotContain("SUCCEEDED");
    }

    @Test void consentReadAndClosureRequestUseFrozenTypedContracts() throws Exception {
        BuyerAuthService service=mock(BuyerAuthService.class);BuyerAccountLifecycleService lifecycle=mock(BuyerAccountLifecycleService.class);
        when(service.authenticate("buyer-token")).thenReturn(Optional.of(new BuyerAuthStore.BuyerPrincipal(BuyerAuthStore.Eligibility.ELIGIBLE,"BUYER-SUBJECT","SESSION-001")));
        when(service.consentState("BUYER-SUBJECT")).thenReturn(Optional.of(new BuyerAuthStore.ConsentState("BUYER-SUBJECT","GUEST-000001","VALID","UA-V1","PP-V1",Instant.parse("2026-08-18T10:00:00Z"),1)));
        MockHttpServletRequest consentRequest=new MockHttpServletRequest("GET","/buyer-auth/v1/consent");consentRequest.addHeader("Authorization","Bearer buyer-token");
        var consent=new BuyerAuthController(service,lifecycle).consent(consentRequest);assertThat(consent.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked") Map<String,Object> consentBody=(Map<String,Object>)consent.getBody();
        assertThat(consentBody).containsEntry("schemaVersion","BUYER_CONSENT_V1").containsEntry("consentState","VALID").containsEntry("version",1L);

        var view=new BuyerAccountLifecycleService.ClosureView("BUYER_CLOSURE_V1","CLOSURE-001","REQUESTED",2,1,"CLOSURE-REQUEST-1",false);
        when(lifecycle.request(eq("BUYER-SUBJECT"),eq("IDEMPOTENCY-0001"),any())).thenReturn(view);
        MockHttpServletRequest closureRequest=new MockHttpServletRequest("POST","/buyer-auth/v1/account-closure-requests");closureRequest.addHeader("Authorization","Bearer buyer-token");
        var closure=new BuyerAuthController(service,lifecycle).requestClosure("IDEMPOTENCY-0001",json.readTree("{\"requestRef\":\"CLOSURE-REQUEST-1\",\"reason\":\"user requested closure\",\"expectedVersion\":1}"),closureRequest);
        assertThat(closure.getStatusCode().value()).isEqualTo(202);assertThat(closure.getBody()).isEqualTo(view);
    }
}
