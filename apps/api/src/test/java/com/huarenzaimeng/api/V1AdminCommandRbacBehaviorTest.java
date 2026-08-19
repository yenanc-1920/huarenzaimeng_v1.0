package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V1AdminCommandRbacBehaviorTest {
    @Test void mutatingEndpointsRetainTransactionalBoundary() throws Exception {
        assertTrue(V1AdminCommandController.class.getDeclaredMethod("create",String.class,String.class,com.fasterxml.jackson.databind.JsonNode.class,jakarta.servlet.http.HttpServletRequest.class).isAnnotationPresent(Transactional.class));
        assertTrue(V1AdminCommandController.class.getDeclaredMethod("update",String.class,String.class,String.class,com.fasterxml.jackson.databind.JsonNode.class,jakarta.servlet.http.HttpServletRequest.class).isAnnotationPresent(Transactional.class));
        assertTrue(V1AdminCommandController.class.getDeclaredMethod("transition",String.class,String.class,String.class,String.class,com.fasterxml.jackson.databind.JsonNode.class,jakarta.servlet.http.HttpServletRequest.class).isAnnotationPresent(Transactional.class));
    }
    @Test void contentRoleCanCreateAndSubmitContentInsideTransactionalCommands() throws Exception {
        JdbcTemplate jdbc=successfulJdbc();MockMvc mvc=mvc(jdbc);
        mvc.perform(post("/admin-command/v1/news").header("Idempotency-Key","CONTENT-CREATE-0001")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"CONTENT").requestAttr(AdminSessionFilter.TRUSTED_USER,"CONTENT-1")
                .contentType("application/json").content("""
                        {"ref":"NEWS-1","reason":"draft","category":"LIFE","title":"Title","summary":"Summary",
                         "bodyText":"Body","sourceLabel":"editorial","editor":"Editor","validUntil":"2027-01-01T00:00:00Z"}
                        """)).andExpect(status().isOk());
        mvc.perform(post("/admin-command/v1/news/NEWS-1/submit").header("Idempotency-Key","CONTENT-SUBMIT-0001")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"CONTENT").requestAttr(AdminSessionFilter.TRUSTED_USER,"CONTENT-1")
                .contentType("application/json").content("{"+"\"expectedVersion\":1,\"reason\":\"ready\"}"))
                .andExpect(status().isOk());
        verify(jdbc,atLeastOnce()).update(contains("hz_content_version_history"),any(Object[].class));
    }

    @Test void contentRoleCannotMutateProductsChannelsOrPrices() throws Exception {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);MockMvc mvc=mvc(jdbc);
        mvc.perform(post("/admin-command/v1/products").header("Idempotency-Key","CONTENT-PRODUCT-0001")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"CONTENT").requestAttr(AdminSessionFilter.TRUSTED_USER,"CONTENT-1")
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }

    @Test void operatorCanDraftButReviewerCannotDraftAndNeitherCanPublish() throws Exception {
        JdbcTemplate jdbc=successfulJdbc();MockMvc mvc=mvc(jdbc);
        String body="""
                {"ref":"NEWS-2","reason":"draft","category":"LIFE","title":"Title","summary":"Summary",
                 "bodyText":"Body","sourceLabel":"editorial","editor":"Editor","validUntil":"2027-01-01T00:00:00Z"}
                """;
        mvc.perform(post("/admin-command/v1/news").header("Idempotency-Key","OPERATOR-CREATE-0001")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"CONTENT_OPERATOR").requestAttr(AdminSessionFilter.TRUSTED_USER,"OP-1")
                .contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(post("/admin-command/v1/news").header("Idempotency-Key","REVIEWER-CREATE-0001")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"CONTENT_REVIEWER").requestAttr(AdminSessionFilter.TRUSTED_USER,"REV-1")
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/admin-command/v1/news/NEWS-2/publish").header("Idempotency-Key","OPERATOR-PUBLISH-0001")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"CONTENT_OPERATOR").requestAttr(AdminSessionFilter.TRUSTED_USER,"OP-1")
                .contentType("application/json").content("{\"expectedVersion\":2,\"reason\":\"publish\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void approvedContentCannotBePublishedBySubmitterOrReviewer() throws Exception {
        JdbcTemplate jdbc=successfulJdbc();
        when(jdbc.queryForList(contains("FROM hz_content_review_task"),any(Object[].class)))
                .thenReturn(List.of(Map.of("submitter_ref","CONTENT-1","reviewer_ref","CONTENT-2")));
        MockMvc mvc=mvc(jdbc);
        mvc.perform(post("/admin-command/v1/news/NEWS-1/publish").header("Idempotency-Key","CONTENT-PUBLISH-0001")
                .requestAttr(AdminSessionFilter.TRUSTED_ROLE,"SUPER_ADMIN").requestAttr(AdminSessionFilter.TRUSTED_USER,"CONTENT-2")
                .contentType("application/json").content("{"+"\"expectedVersion\":2,\"reason\":\"publish\"}"))
                .andExpect(status().isConflict());
        verify(jdbc,never()).update(startsWith("UPDATE hz_news_article"),any(Object[].class));
    }

    private static JdbcTemplate successfulJdbc(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        return jdbc;
    }
    private static MockMvc mvc(JdbcTemplate jdbc){return MockMvcBuilders.standaloneSetup(new V1AdminCommandController(jdbc)).build();}
}
