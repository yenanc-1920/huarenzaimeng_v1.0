package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/** Append-only commands and history reads used by A100/A110/A120. */
@RestController
@Profile("release-mysql")
@RequestMapping("/admin-workflow/v1")
class AdminWorkflowController {
    private static final Set<String> CASE_ROLES=Set.of("CS","SUPER_ADMIN");
    private static final Set<String> REVIEW_READ_ROLES=Set.of("CONTENT","CONTENT_OPERATOR","CONTENT_REVIEWER","SUPER_ADMIN");
    private static final Set<String> REVIEW_SUBMIT_ROLES=Set.of("CONTENT","CONTENT_OPERATOR","SUPER_ADMIN");
    private static final Set<String> REVIEW_DECISION_ROLES=Set.of("CONTENT_REVIEWER","SUPER_ADMIN");
    private static final Map<String,Set<String>> RECON_ACTION_ROLES=Map.of(
            "CLAIM",Set.of("FIN","CS","SUPER_ADMIN"),
            "NOTE",Set.of("FIN","CS","SUPER_ADMIN"),
            "QUERY_WECHAT",Set.of("FIN","SUPER_ADMIN"),
            "QUERY_PROVIDER",Set.of("FIN","SUPER_ADMIN"),
            "TRANSFER_CS",Set.of("FIN","SUPER_ADMIN"),
            "TRANSFER_REVIEW",Set.of("CS","SUPER_ADMIN"),
            "RESOLVE",Set.of("FIN","SUPER_ADMIN"),
            "CLOSE",Set.of("FIN","SUPER_ADMIN"));
    private final AdminWorkflowService service;
    AdminWorkflowController(AdminWorkflowService service){this.service=service;}

    @PostMapping("/customer-cases") @Transactional ResponseEntity<?> createCase(
            @RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body,HttpServletRequest request){
        if(!allowed(request,CASE_ROLES))return ResponseEntity.status(403).build();
        return ResponseEntity.ok().body(service.createCase(key,body,user(request)));
    }
    @PostMapping("/customer-cases/{caseRef}/events") @Transactional ResponseEntity<?> appendCaseEvent(
            @PathVariable String caseRef,@RequestHeader("Idempotency-Key") String key,
            @RequestBody JsonNode body,HttpServletRequest request){
        if(!allowed(request,CASE_ROLES))return ResponseEntity.status(403).build();
        return ResponseEntity.ok().body(service.appendCaseEvent(caseRef,key,body,user(request)));
    }
    @GetMapping("/customer-cases/{caseRef}") ResponseEntity<?> caseDetail(@PathVariable String caseRef,HttpServletRequest request){
        if(!allowed(request,CASE_ROLES))return ResponseEntity.status(403).build();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.caseDetail(caseRef));
    }
    @PostMapping("/reconciliations/{reconciliationRef}/events") @Transactional ResponseEntity<?> appendReconciliationEvent(
            @PathVariable String reconciliationRef,@RequestHeader("Idempotency-Key") String key,
            @RequestBody JsonNode body,HttpServletRequest request){
        String action=body.path("actionType").asText("");
        if(!allowed(request,RECON_ACTION_ROLES.getOrDefault(action,Set.of())))return ResponseEntity.status(403).build();
        return ResponseEntity.ok().body(service.appendReconciliationEvent(reconciliationRef,key,body,user(request)));
    }
    @GetMapping("/reconciliations/{reconciliationRef}") ResponseEntity<?> reconciliationDetail(
            @PathVariable String reconciliationRef,HttpServletRequest request){
        if(!allowed(request,Set.of("FIN","CS","SUPER_ADMIN")))return ResponseEntity.status(403).build();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.reconciliationDetail(reconciliationRef));
    }
    @PostMapping("/reviews") @Transactional ResponseEntity<?> submitReview(
            @RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body,HttpServletRequest request){
        if(!allowed(request,REVIEW_SUBMIT_ROLES))return ResponseEntity.status(403).build();
        return ResponseEntity.ok().body(service.submitReview(key,body,user(request)));
    }
    @PostMapping("/reviews/{reviewRef}/decision") @Transactional ResponseEntity<?> decideReview(
            @PathVariable String reviewRef,@RequestHeader("Idempotency-Key") String key,
            @RequestBody JsonNode body,HttpServletRequest request){
        if(!allowed(request,REVIEW_DECISION_ROLES))return ResponseEntity.status(403).build();
        return ResponseEntity.ok().body(service.decideReview(reviewRef,key,body,user(request)));
    }
    @GetMapping("/reviews") ResponseEntity<?> reviews(HttpServletRequest request){
        if(!allowed(request,REVIEW_READ_ROLES))return ResponseEntity.status(403).build();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("items",service.reviews()));
    }
    @GetMapping("/content-history/{objectType}/{objectRef}") ResponseEntity<?> contentHistory(
            @PathVariable String objectType,@PathVariable String objectRef,HttpServletRequest request){
        if(!allowed(request,REVIEW_READ_ROLES))return ResponseEntity.status(403).build();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("items",service.contentHistory(objectType,objectRef)));
    }
    @GetMapping("/catalog-batches") ResponseEntity<?> catalogBatches(HttpServletRequest request){
        if(!"SUPER_ADMIN".equals(String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE))))return ResponseEntity.status(403).build();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("items",service.catalogBatches()));
    }
    @GetMapping("/fx-snapshots") ResponseEntity<?> fxSnapshots(HttpServletRequest request){
        if(!"SUPER_ADMIN".equals(String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE))))return ResponseEntity.status(403).build();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("items",service.fxSnapshots()));
    }

    @ExceptionHandler(AdminWorkflowService.WorkflowConflict.class) ResponseEntity<?> conflict(AdminWorkflowService.WorkflowConflict error){
        return ResponseEntity.status(409).body(Map.of("projectCode",error.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(IllegalArgumentException error){
        return ResponseEntity.badRequest().body(Map.of("projectCode",error.getMessage()));
    }
    private static boolean allowed(HttpServletRequest request,Set<String> roles){return roles.contains(String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE)));}
    private static String user(HttpServletRequest request){return AdminSessionFilter.trustedUserId(request);}
}
