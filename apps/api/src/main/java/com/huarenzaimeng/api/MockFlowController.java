package com.huarenzaimeng.api;

import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.ProjectEnvelope;
import com.huarenzaimeng.core.ProjectProjection;
import com.huarenzaimeng.core.Quote;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestController
@RequestMapping("/api/v1")
public class MockFlowController {
    private static final String MOCK_HEADER = "X-HZM-Mock-Only";
    private static final String MOCK_SEMANTICS_HEADER = "X-HZM-Mock-Semantics";
    private static final String SUBJECT_HEADER = "X-Project-Subject-Ref";
    private final MockFlowService service;

    public MockFlowController(MockFlowService service) { this.service = service; }

    @PostMapping("/quotes")
    ResponseEntity<ProjectEnvelope<Quote>> quote(@RequestHeader(SUBJECT_HEADER) String projectSubjectRef,
                                                 @Valid @RequestBody QuoteRequest request) {
        return mock(service.createQuote(projectSubjectRef, request.phone(), request.operatorCode(),
                request.productCode(), request.mnpState()));
    }

    @PostMapping("/orders")
    ResponseEntity<ProjectEnvelope<ProjectProjection>> order(@RequestHeader(SUBJECT_HEADER) String projectSubjectRef,
                                                             @Valid @RequestBody CreateOrderRequest request) {
        return mock(service.createOrder(projectSubjectRef, request.quoteRef(), request.commandId(),
                request.idempotencyKey()));
    }

    @PostMapping("/orders/{orderRef}/mock-payment")
    ResponseEntity<ProjectEnvelope<ProjectProjection>> payment(@RequestHeader(SUBJECT_HEADER) String projectSubjectRef,
                                                               @PathVariable String orderRef,
                                                               @Valid @RequestBody CommandRequest request) {
        return mock(service.confirmMockPayment(projectSubjectRef, orderRef, request.commandId(),
                request.idempotencyKey(), request.expectedProjectionVersion(), request.expectedAggregateVersion()));
    }

    @PostMapping("/orders/{orderRef}/mock-topup")
    ResponseEntity<ProjectEnvelope<ProjectProjection>> topup(@RequestHeader(SUBJECT_HEADER) String projectSubjectRef,
                                                             @PathVariable String orderRef,
                                                             @Valid @RequestBody TopupRequest request) {
        return mock(service.completeMockTopup(projectSubjectRef, orderRef, request.commandId(),
                request.idempotencyKey(), request.expectedProjectionVersion(), request.expectedAggregateVersion(),
                request.mnpState()));
    }

    @GetMapping({"/orders/{orderRef}", "/orders/{orderRef}/projection"})
    ResponseEntity<ProjectEnvelope<ProjectProjection>> recover(@RequestHeader(SUBJECT_HEADER) String projectSubjectRef,
                                                               @PathVariable String orderRef) {
        return mock(service.getOrder(projectSubjectRef, orderRef));
    }

    @ExceptionHandler(FlowRejectedException.class)
    ResponseEntity<ProjectEnvelope<Void>> rejected(FlowRejectedException error) {
        int status = error.getMessage().endsWith("CONFLICT") ? 409 : 422;
        return ResponseEntity.status(status).header(MOCK_HEADER, "true")
                .header(MOCK_SEMANTICS_HEADER, "PROJECTION_ONLY_NO_EXTERNAL_FACTS")
                .body(ProjectEnvelope.rejected(error.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingRequestHeaderException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ProjectEnvelope<Void>> invalidRequest(Exception error) {
        return ResponseEntity.badRequest().header(MOCK_HEADER, "true")
                .header(MOCK_SEMANTICS_HEADER, "PROJECTION_ONLY_NO_EXTERNAL_FACTS")
                .body(ProjectEnvelope.rejected("INVALID_REQUEST"));
    }

    private static <T> ResponseEntity<ProjectEnvelope<T>> mock(T body) {
        return ResponseEntity.ok().header(MOCK_HEADER, "true")
                .header(MOCK_SEMANTICS_HEADER, "PROJECTION_ONLY_NO_EXTERNAL_FACTS")
                .body(ProjectEnvelope.accepted(body));
    }

    record QuoteRequest(@NotBlank String phone, @NotBlank String operatorCode,
                        @NotBlank String productCode, @NotNull MnpState mnpState) {}
    record CreateOrderRequest(@NotBlank String quoteRef, @NotBlank String commandId,
                              @NotBlank String idempotencyKey) {}
    record CommandRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                          @NotNull Long expectedProjectionVersion, @NotNull Long expectedAggregateVersion) {}
    record TopupRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                        @NotNull Long expectedProjectionVersion, @NotNull Long expectedAggregateVersion,
                        @NotNull MnpState mnpState) {}
}
