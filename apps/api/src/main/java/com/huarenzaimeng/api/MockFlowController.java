package com.huarenzaimeng.api;

import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.ProjectEnvelope;
import com.huarenzaimeng.core.ProjectProjection;
import com.huarenzaimeng.core.Quote;
import com.huarenzaimeng.api.config.TestAccessTokenFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Set;

@RestController
@Profile({"mock", "test"})
@RequestMapping("/api/v1")
public class MockFlowController {
    private static final String MOCK_HEADER = "X-HZM-Mock-Only";
    private static final String MOCK_SEMANTICS_HEADER = "X-HZM-Mock-Semantics";
    private static final String SUBJECT_HEADER = "X-Project-Subject-Ref";
    private final MockFlowService service;
    private final CatalogService catalog;
    private final LocalSyntheticOrderRecoveryService orderRecovery;

    public MockFlowController(MockFlowService service, CatalogService catalog,
                              LocalSyntheticOrderRecoveryService orderRecovery) {
        this.service = service;
        this.catalog = catalog;
        this.orderRecovery = orderRecovery;
    }

    @GetMapping("/catalog")
    ResponseEntity<ProjectEnvelope<CatalogView>> catalog(
            @RequestParam(required = false) String operatorCode) {
        if (operatorCode == null || operatorCode.isBlank()) {
            return ResponseEntity.badRequest().header(MOCK_HEADER, "true")
                    .header(MOCK_SEMANTICS_HEADER, "PROJECTION_ONLY_NO_EXTERNAL_FACTS")
                    .body(new ProjectEnvelope<>("REJECTED", "OPERATOR_CODE_REQUIRED", null));
        }
        return mock(catalog.publicCatalog(operatorCode));
    }

    @PostMapping("/admin/catalog-operations")
    ResponseEntity<ProjectEnvelope<Void>> deniedCatalogWrite() {
        return ResponseEntity.status(403).header(MOCK_HEADER, "true")
                .header(MOCK_SEMANTICS_HEADER, "LOCAL_MOCK_NO_REAL_ROLE_AUTHORIZATION")
                .body(ProjectEnvelope.rejected("CATALOG_WRITE_AUTHORIZATION_REQUIRED"));
    }

    @PostMapping("/quotes")
    ResponseEntity<ProjectEnvelope<Quote>> quote(@RequestHeader(SUBJECT_HEADER) String projectSubjectRef,
                                                 @Valid @RequestBody QuoteRequest request) {
        return mock(service.createQuote(projectSubjectRef, request.phone(), request.operatorCode(),
                request.productRef(), request.denominationRef(), request.supportedOperatorSetVersion(),
                request.catalogVersion(), request.commandId(), request.idempotencyKey(), request.mnpState()));
    }

    @PostMapping("/orders")
    ResponseEntity<OrderCreationResponse> order(HttpServletRequest servletRequest,
                                                @Valid @RequestBody CreateOrderRequest request) {
        BuyerAuthorization authorization = orderRecovery.requireBuyerAuthorization(
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_SESSION_REF),
                request.sessionVersion(), request.authorizationSetRef());
        OrderCreationResponse body = service.createLocalSyntheticOrder(authorization, request.quoteRef(),
                request.orderCreationPrecondition(), request.commandId(), request.idempotencyKey());
        return ResponseEntity.ok().header(MOCK_HEADER, "true")
                .header(MOCK_SEMANTICS_HEADER, "LOCAL_SYNTHETIC_NO_EXTERNAL_FACTS").body(body);
    }

    @GetMapping("/orders")
    ResponseEntity<ProjectEnvelope<OrderListResponse>> orders(HttpServletRequest servletRequest,
            @RequestParam(required = false) Long sessionVersion,
            @RequestParam(required = false) String authorizationSetRef) {
        return localSynthetic(orderRecovery.listOrders(
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_SESSION_REF),
                sessionVersion, authorizationSetRef));
    }

    @PostMapping("/orders/{orderRef}/payment-intents")
    ResponseEntity<PaymentIntentResponse> paymentIntent(HttpServletRequest servletRequest,
                                                        @PathVariable String orderRef,
                                                        @Valid @RequestBody CreatePaymentIntentRequest request) {
        BuyerAuthorization authorization = orderRecovery.requirePaymentIntentBuyerAuthorization(
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_SESSION_REF),
                request.sessionVersion(), request.authorizationSetRef());
        PaymentIntentResponse body = service.createLocalSyntheticPaymentIntent(authorization, orderRef,
                request.paymentIntentCreationPrecondition(), request.commandId(), request.idempotencyKey(),
                request.expectedProjectionVersion(), request.expectedAggregateVersion());
        return ResponseEntity.ok().header(MOCK_HEADER, "true")
                .header(MOCK_SEMANTICS_HEADER, "LOCAL_SYNTHETIC_NO_EXTERNAL_FACTS").body(body);
    }

    @GetMapping("/orders/{orderRef}/payment-intents/result")
    ResponseEntity<PaymentIntentResultResponse> paymentIntentResult(
            HttpServletRequest servletRequest,
            @PathVariable String orderRef,
            @RequestParam(name = "commandId", required = false) String commandId,
            @RequestParam(name = "idempotencyKey", required = false) String idempotencyKey,
            @RequestParam(name = "sessionVersion", required = false) String sessionVersion,
            @RequestParam(name = "authorizationSetRef", required = false) String authorizationSetRef) {
        Set<String> allowed = Set.of("commandId", "idempotencyKey", "sessionVersion", "authorizationSetRef");
        boolean invalidQueryShape = servletRequest.getParameterMap().entrySet().stream()
                .anyMatch(entry -> !allowed.contains(entry.getKey()) || entry.getValue() == null
                        || entry.getValue().length != 1);
        PaymentIntentResultResponse body = service.queryLocalSyntheticPaymentIntentResult(orderRecovery,
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_SESSION_REF), orderRef, commandId,
                idempotencyKey, sessionVersion, authorizationSetRef, invalidQueryShape);
        return ResponseEntity.ok().header(MOCK_HEADER, "true")
                .header(MOCK_SEMANTICS_HEADER, "LOCAL_SYNTHETIC_READ_ONLY_RESULT").body(body);
    }

    @PostMapping("/recovery-cases")
    ResponseEntity<ProjectEnvelope<RecoveryCaseResponse>> recoveryCase(HttpServletRequest servletRequest,
            @Valid @RequestBody RecoveryCaseRequest request) {
        LocalSyntheticOrderRecoveryService.RecoveryCommand command =
                new LocalSyntheticOrderRecoveryService.RecoveryCommand(request.commandId(), request.idempotencyKey(),
                        request.recoveryInputFingerprint(), request.creationPrecondition(), request.orderRef(),
                        request.recoveryMaterialRef());
        return localSynthetic(orderRecovery.createRecoveryCase(
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_SESSION_REF), command));
    }

    @GetMapping("/recovery-cases/{recoveryCaseRef}")
    ResponseEntity<ProjectEnvelope<RecoveryCaseResponse>> recoveryCase(HttpServletRequest servletRequest,
            @PathVariable String recoveryCaseRef) {
        return localSynthetic(orderRecovery.queryRecoveryCase(
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_SESSION_REF), recoveryCaseRef));
    }

    @PostMapping("/local-synthetic/recovery-cases/{recoveryCaseRef}/authoritative-result")
    ResponseEntity<ProjectEnvelope<RecoveryCaseResponse>> convergeRecoveryCase(HttpServletRequest servletRequest,
            @PathVariable String recoveryCaseRef,
            @Valid @RequestBody RecoveryAuthoritativeResultRequest request) {
        return localSynthetic(orderRecovery.convergeRecoveryCase(
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_ENVIRONMENT),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_PROJECT_SUBJECT_REF),
                attribute(servletRequest, TestAccessTokenFilter.LOCAL_SESSION_REF), recoveryCaseRef,
                request.authoritativeResultRef()));
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

    @ExceptionHandler(FlowRejectedException.class)
    ResponseEntity<ProjectEnvelope<Void>> rejected(FlowRejectedException error) {
        int status = error.getMessage().endsWith("CONFLICT") ? 409
                : error.getMessage().equals("ORDER_LIST_NOT_AVAILABLE")
                || error.getMessage().equals("LOCAL_SYNTHETIC_IDENTITY_REQUIRED") ? 403 : 422;
        return ResponseEntity.status(status).header(MOCK_HEADER, "true")
                .header(MOCK_SEMANTICS_HEADER, "PROJECTION_ONLY_NO_EXTERNAL_FACTS")
                .body(ProjectEnvelope.rejected(error.getMessage()));
    }

    @ExceptionHandler(LocalSyntheticRecoveryUnavailableException.class)
    ResponseEntity<ProjectEnvelope<Void>> recoveryUnavailable() {
        return ResponseEntity.status(503).header(MOCK_HEADER, "true")
                .header(MOCK_SEMANTICS_HEADER, "LOCAL_SYNTHETIC_NO_REAL_IDENTITY")
                .body(ProjectEnvelope.rejected("RECOVERY_TEMPORARILY_UNAVAILABLE"));
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

    private static <T> ResponseEntity<ProjectEnvelope<T>> localSynthetic(T body) {
        return ResponseEntity.ok().header(MOCK_HEADER, "true")
                .header(MOCK_SEMANTICS_HEADER, "LOCAL_SYNTHETIC_NO_REAL_IDENTITY")
                .body(ProjectEnvelope.accepted(body));
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : value.toString();
    }

    record QuoteRequest(@NotBlank String phone, @NotBlank String operatorCode,
                        @NotBlank String productRef, @NotBlank String denominationRef,
                        @Min(1) @Max(ProjectApiVersion.MAX) long supportedOperatorSetVersion,
                        @Min(1) @Max(ProjectApiVersion.MAX) long catalogVersion,
                        @NotBlank String commandId, @NotBlank String idempotencyKey,
                        @NotNull MnpState mnpState) {}
    @JsonIgnoreProperties(ignoreUnknown = false)
    record CreateOrderRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                              @NotBlank String orderCreationPrecondition, @NotBlank String quoteRef,
                              @Min(1) @Max(ProjectApiVersion.MAX) long sessionVersion,
                              @NotBlank String authorizationSetRef) {
        @JsonAnySetter
        void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("unsupported order request field");
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = false)
    record CreatePaymentIntentRequest(
            @NotBlank String commandId,
            @NotBlank String idempotencyKey,
            @NotBlank String paymentIntentCreationPrecondition,
            @Min(1) @Max(ProjectApiVersion.MAX) long sessionVersion,
            @NotBlank String authorizationSetRef,
            @Min(1) @Max(ProjectApiVersion.MAX) long expectedProjectionVersion,
            @Min(1) @Max(ProjectApiVersion.MAX) long expectedAggregateVersion
    ) {
        @JsonAnySetter
        void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("unsupported payment intent request field");
        }
    }
    record RecoveryCaseRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                               @NotBlank String recoveryInputFingerprint,
                               @NotBlank String creationPrecondition, @NotBlank String orderRef,
                               @NotBlank String recoveryMaterialRef) {}
    record RecoveryAuthoritativeResultRequest(@NotBlank String authoritativeResultRef) {}
    record CommandRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                          @NotNull Long expectedProjectionVersion, @NotNull Long expectedAggregateVersion) {}
    record TopupRequest(@NotBlank String commandId, @NotBlank String idempotencyKey,
                        @NotNull Long expectedProjectionVersion, @NotNull Long expectedAggregateVersion,
                        @NotNull MnpState mnpState) {}
}
