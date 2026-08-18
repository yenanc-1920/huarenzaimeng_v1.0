package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AdminWorkflowContractTest {
    @Test void caseAndReconciliationStateMachinesAreAppendOnlyAndGuarded() {
        assertEquals("IN_PROGRESS",AdminWorkflowService.caseState("OPEN","FOLLOW_UP"));
        assertEquals("RESOLVED",AdminWorkflowService.caseState("IN_PROGRESS","RESOLVE"));
        assertEquals("CLOSED",AdminWorkflowService.caseState("RESOLVED","CLOSE"));
        assertThrows(AdminWorkflowService.WorkflowConflict.class,()->AdminWorkflowService.caseState("OPEN","CLOSE"));
        assertEquals("IN_REVIEW",AdminWorkflowService.reconciliationState("OPEN","QUERY_PROVIDER"));
        assertThrows(AdminWorkflowService.WorkflowConflict.class,()->AdminWorkflowService.reconciliationState("OPEN","CLOSE"));
    }

    @Test void v15AddsHistorySnapshotsWithoutChangingPreviousMigrations() throws Exception {
        Path migration=Path.of("src/main/resources/db/migration/V15__add_v1_admin_workflow_history.sql");
        String sql=Files.readString(migration);
        for(String table:new String[]{"hz_customer_case_event","hz_reconciliation_event","hz_content_review_task",
                "hz_content_version_history","hz_supplier_catalog_batch_snapshot","hz_supplier_catalog_item_snapshot","hz_fx_rate_snapshot"})
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS "+table),table);
        assertEquals(7,sql.split("CREATE TABLE IF NOT EXISTS",-1).length-1);
        assertEquals(3,sql.split("information_schema.columns",-1).length-1);
        assertEquals(3,sql.split("EXECUTE v15_statement",-1).length-1);
        assertTrue(sql.contains("UNIQUE KEY uk_hz_customer_case_event_idempotency"));
        assertTrue(sql.contains("UNIQUE KEY uk_hz_reconciliation_event_idempotency"));
        assertTrue(sql.contains("UNIQUE KEY uk_hz_content_history_version"));
        assertTrue(sql.contains("request_digest CHAR(64)"));
        assertTrue(sql.contains("decision_request_digest CHAR(64)"));
        assertTrue(sql.contains("UNIQUE KEY uk_hz_fx_rate_observation"));
        assertFalse(sql.contains("uk_hz_fx_rate_source_digest"));
        assertTrue(sql.contains("supplier_source_ref VARCHAR(196)"));
    }

    @Test void reviewAndIdempotencyContractsAreVersionBoundAndDutySeparated() throws Exception {
        String service=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/AdminWorkflowService.java"));
        String commands=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/V1AdminCommandController.java"));
        assertTrue(service.contains("requireReviewObjectUnderReview(objectType,objectRef,version)"));
        assertTrue(service.contains("review_state='PENDING' AND submitter_ref<>?"));
        assertTrue(service.contains("REVIEW_DUTY_SEPARATION_REQUIRED"));
        assertTrue(service.contains("requireSameDigest"));
        assertTrue(commands.contains("CONTENT_APPROVAL_REQUIRED"));
        assertTrue(commands.contains("object_version=? AND review_state='APPROVED'"));
        assertTrue(commands.contains("PUBLISH_DUTY_SEPARATION_REQUIRED"));
        assertTrue(commands.contains("hz_supplier_catalog_item_snapshot"));
        assertTrue(commands.contains("SUPPLIER_SNAPSHOT_DRIFT"));
    }

    @Test void workflowDetailsUseTypedVersionedProjectionWithoutRawSelectStar() throws Exception {
        String service=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/AdminWorkflowService.java"));
        assertTrue(service.contains("WorkflowDetail<CustomerCaseView,CustomerCaseEventView>"));
        assertTrue(service.contains("DETAIL_SCHEMA_VERSION"));
        assertTrue(service.contains("DETAIL_PROJECTION_VERSION"));
        assertFalse(service.contains("SELECT * FROM hz_customer_case"));
        assertFalse(service.contains("SELECT * FROM hz_reconciliation_case"));
    }

    @Test void securityCriticalOrderingAndLocksAreExplicit() throws Exception {
        String workflow=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/AdminWorkflowService.java"));
        String commands=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/V1AdminCommandController.java"));
        String filter=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/adminauth/AdminSessionFilter.java"));
        assertTrue(filter.contains("path.startsWith(\"/admin-workflow/\")"));
        assertTrue(commands.contains("SELECT platform_product_ref FROM hz_platform_product WHERE platform_product_ref=? FOR UPDATE"));
        assertTrue(commands.indexOf("SELECT platform_product_ref FROM hz_platform_product WHERE platform_product_ref=? FOR UPDATE")
                < commands.indexOf("SELECT COUNT(*) FROM hz_price_version v JOIN hz_platform_product"));
        assertTrue(workflow.indexOf("replayCaseEventIfPresent(key,ref,requestDigest)")
                < workflow.indexOf("SELECT case_state,aggregate_version FROM hz_customer_case WHERE case_ref=? FOR UPDATE"));
        assertTrue(workflow.indexOf("replayReconciliationEventIfPresent(key,ref,requestDigest)")
                < workflow.indexOf("SELECT case_state,aggregate_version FROM hz_reconciliation_case WHERE reconciliation_ref=? FOR UPDATE"));
        assertTrue(workflow.contains("case\"products\"->\"SELECT aggregate_version,enable_state AS publish_state"));
    }
}
