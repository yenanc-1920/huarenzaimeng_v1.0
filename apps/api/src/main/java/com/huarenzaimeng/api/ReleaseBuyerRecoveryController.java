package com.huarenzaimeng.api;

import com.huarenzaimeng.api.buyerauth.BuyerSessionFilter;
import com.huarenzaimeng.api.buyerauth.BuyerSessionPrincipal;
import com.huarenzaimeng.core.ProjectEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@Profile("release-mysql")
@RequestMapping("/buyer-auth/v1/recovery-cases")
public final class ReleaseBuyerRecoveryController {
    private final JdbcTemplate jdbc;
    ReleaseBuyerRecoveryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping
    @Transactional
    ResponseEntity<ProjectEnvelope<Map<String, Object>>> create(HttpServletRequest servlet, @Valid @RequestBody Request r) {
        String subject = principal(servlet).subjectRef();
        String ref = "RC-" + UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO hz_buyer_recovery_case(recovery_case_ref,project_subject_ref,order_ref,command_id,idempotency_key,input_fingerprint,recovery_material_ref,recovery_state) VALUES (?,?,?,?,?,?,?,'PENDING')",
                    ref, subject, r.orderRef(), r.commandId(), r.idempotencyKey(), r.recoveryInputFingerprint(), r.recoveryMaterialRef());
        } catch (DuplicateKeyException replay) {
            ref = jdbc.queryForObject("SELECT recovery_case_ref FROM hz_buyer_recovery_case WHERE project_subject_ref=? AND (command_id=? OR idempotency_key=?) ORDER BY created_at LIMIT 1",
                    String.class, subject, r.commandId(), r.idempotencyKey());
        }
        return ok(Map.of("recoveryCaseRef", ref, "orderRef", r.orderRef(), "state", "PENDING", "retryClass", "READ_CASE_ONLY"));
    }

    @GetMapping("/{recoveryCaseRef}")
    ResponseEntity<ProjectEnvelope<Map<String, Object>>> read(HttpServletRequest servlet, @PathVariable String recoveryCaseRef) {
        String subject = principal(servlet).subjectRef();
        Map<String,Object> value = jdbc.queryForMap("SELECT recovery_case_ref AS recoveryCaseRef,order_ref AS orderRef,recovery_state AS state FROM hz_buyer_recovery_case WHERE project_subject_ref=? AND recovery_case_ref=?",
                subject, recoveryCaseRef);
        value.put("retryClass", "READ_CASE_ONLY");
        return ok(value);
    }

    private static BuyerSessionPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(BuyerSessionFilter.BUYER);
        if (value instanceof BuyerSessionPrincipal buyer) return buyer;
        throw new FlowRejectedException("BUYER_SESSION_REQUIRED");
    }
    private static <T> ResponseEntity<ProjectEnvelope<T>> ok(T value) { return ResponseEntity.ok().header("Cache-Control","no-store").body(ProjectEnvelope.accepted(value)); }
    record Request(@NotBlank String commandId,@NotBlank String idempotencyKey,@NotBlank String recoveryInputFingerprint,
                   @NotBlank String creationPrecondition,@NotBlank String orderRef,@NotBlank String recoveryMaterialRef) {}
}
