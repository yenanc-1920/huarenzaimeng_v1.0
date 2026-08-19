package com.huarenzaimeng.api;

import com.huarenzaimeng.core.ProjectEnvelope;
import com.huarenzaimeng.api.config.ProjectAuthenticationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.context.annotation.Profile;

import java.time.Instant;

@RestController
@RequestMapping("/project-api/v1")
@Validated
@Profile({"mock","test","local-synthetic"})
class ContentController {
    private static final String MOCK_HEADER = "X-HZM-Mock-Only";
    private final ContentService service;

    ContentController(ContentService service) { this.service = service; }

    @GetMapping("/content/items")
    ResponseEntity<ProjectEnvelope<ContentPage<PublicContentSummary>>> list() {
        return publicRead(service.publicList());
    }

    @GetMapping("/content/items/{contentRef}")
    ResponseEntity<ProjectEnvelope<PublicContentProjection>> detail(@PathVariable String contentRef,
            @RequestParam @Min(ProjectApiVersion.MIN) @Max(ProjectApiVersion.MAX) long contentVersion) {
        return publicRead(service.publicDetail(contentRef, contentVersion));
    }

    @PostMapping("/content/items/{contentRef}/reports")
    ResponseEntity<ProjectEnvelope<ReportReceipt>> report(
            @PathVariable String contentRef,
            @Valid @RequestBody ReportRequest request) {
        DirectoryContent content = service.report(contentRef, "PUBLIC_REPORTER", request.commandId(),
                request.idempotencyKey(), request.contentVersion(), request.expectedAggregateVersion(),
                request.reason());
        return mock(new ReportReceipt("SUPPORT-" + content.contentRef() + "-V" + content.version(),
                "CONTENT_ERROR_REPORTED", "A120"));
    }

    @GetMapping("/internal/content/items")
    ResponseEntity<ProjectEnvelope<ContentPage<DirectoryContent>>> internalList(
            @RequestAttribute(ProjectAuthenticationFilter.ROLE) String role) {
        return mock(service.internalList());
    }

    @GetMapping("/internal/content/items/{contentRef}")
    ResponseEntity<ProjectEnvelope<DirectoryContent>> internalDetail(
            @RequestAttribute(ProjectAuthenticationFilter.ROLE) String role, @PathVariable String contentRef) {
        return mock(service.internalDetail(contentRef));
    }

    @PostMapping("/internal/content/items/{contentRef}/transitions")
    ResponseEntity<ProjectEnvelope<DirectoryContent>> transition(
            @RequestAttribute(ProjectAuthenticationFilter.ROLE) String role,
            @RequestAttribute(ProjectAuthenticationFilter.ACTOR) String actorRef,
            @RequestAttribute(ProjectAuthenticationFilter.AUTHORIZATION_REF) String authorizationRef,
            @PathVariable String contentRef,
            @Valid @RequestBody TransitionRequest request) {
        ContentCommand command = new ContentCommand(request.commandId(), request.idempotencyKey(),
                request.expectedAggregateVersion(), request.action(), request.reason(), request.sourceCategory(),
                request.sourceRef(), request.verificationScope(), request.verifiedBy(), request.verifiedAt(),
                request.validUntil(), "PUBLIC_CONTENT_ADMIN", authorizationRef,
                "ACCEPTED", "EVIDENCE-" + request.commandId());
        return mock(service.transition(contentRef, actorRef, command));
    }

    @ExceptionHandler(ContentRejectedException.class)
    ResponseEntity<ProjectEnvelope<Void>> rejected(ContentRejectedException error) {
        int status = error.getMessage().endsWith("CONFLICT") ? 409 : 422;
        if (error.getMessage().equals("RESOURCE_NOT_CONFIRMABLE")) status = 404;
        if (error.getMessage().equals("CONTENT_REMOVED")) status = 410;
        if (error.getMessage().equals("CONTENT_VERSION_STALE")
                || error.getMessage().equals("CONTENT_UNDER_REVIEW")) status = 409;
        if (error.getMessage().equals("AUTHORIZATION_REQUIRED")) status = 403;
        return ResponseEntity.status(status).header(MOCK_HEADER, "true")
                .body(ProjectEnvelope.rejected(error.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ProjectEnvelope<Void>> invalid(Exception ignored) {
        return ResponseEntity.badRequest().header(MOCK_HEADER, "true")
                .body(ProjectEnvelope.rejected("INVALID_REQUEST"));
    }

    private static <T> ResponseEntity<ProjectEnvelope<T>> mock(T body) {
        return ResponseEntity.ok().header(MOCK_HEADER, "true").body(ProjectEnvelope.accepted(body));
    }

    private static <T> ResponseEntity<ProjectEnvelope<T>> publicRead(T body) {
        return ResponseEntity.ok().header(MOCK_HEADER, "true")
                .header("Cache-Control", "no-store").body(ProjectEnvelope.accepted(body));
    }

    record ReportRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                         @NotNull @Min(ProjectApiVersion.MIN) @Max(ProjectApiVersion.MAX) Long contentVersion,
                         @NotNull @Min(ProjectApiVersion.MIN) @Max(ProjectApiVersion.MAX) Long expectedAggregateVersion,
                         @NotBlank String reason) {}
    record ReportReceipt(String supportRef, String status, String reviewTarget) {}

    record TransitionRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                             @NotNull Long expectedAggregateVersion, @NotBlank String action,
                             @NotBlank String reason, String sourceCategory, String sourceRef,
                             String verificationScope, String verifiedBy, Instant verifiedAt,
                             Instant validUntil) {}
}
