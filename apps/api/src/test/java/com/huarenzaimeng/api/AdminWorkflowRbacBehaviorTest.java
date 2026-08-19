package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminWorkflowRbacBehaviorTest {
    @Test void operatorSubmitsAndReviewerDecidesButLegacyContentCannotDecide() throws Exception {
        AdminWorkflowService service=mock(AdminWorkflowService.class);
        when(service.submitReview(anyString(),any(),anyString())).thenReturn(Map.of("state","PENDING"));
        when(service.decideReview(anyString(),anyString(),any(),anyString())).thenReturn(Map.of("state","APPROVED"));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new AdminWorkflowController(service)).build();
        mvc.perform(post("/admin-workflow/v1/reviews").header("Idempotency-Key","REVIEW-SUBMIT-0001")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"CONTENT_OPERATOR").requestAttr(AdminSessionFilter.TRUSTED_USER,"OP-1")
                .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/admin-workflow/v1/reviews/R-1/decision").header("Idempotency-Key","REVIEW-DECIDE-0001")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"CONTENT_REVIEWER").requestAttr(AdminSessionFilter.TRUSTED_USER,"REV-1")
                .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/admin-workflow/v1/reviews/R-1/decision").header("Idempotency-Key","REVIEW-DECIDE-0002")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"CONTENT").requestAttr(AdminSessionFilter.TRUSTED_USER,"LEGACY-1")
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verify(service).submitReview(anyString(),any(),eq("OP-1"));
        verify(service).decideReview(anyString(),anyString(),any(),eq("REV-1"));
    }
}
