package com.huarenzaimeng.api;

import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.OrderProjection;
import com.huarenzaimeng.core.Quote;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class MockFlowController {
    private static final String MOCK_HEADER = "X-HZM-Mock-Only";
    private final MockFlowService service;

    public MockFlowController(MockFlowService service) { this.service = service; }

    @PostMapping("/quotes")
    ResponseEntity<Quote> quote(@Valid @RequestBody QuoteRequest request) {
        return mock(service.createQuote(request.phone(), request.operatorCode(), request.productCode(), request.mnpState()));
    }

    @PostMapping("/orders")
    ResponseEntity<OrderProjection> order(@Valid @RequestBody CreateOrderRequest request) {
        return mock(service.createOrder(request.quoteRef(), request.commandId()));
    }

    @PostMapping("/orders/{orderRef}/mock-payment")
    ResponseEntity<OrderProjection> payment(@PathVariable String orderRef, @Valid @RequestBody CommandRequest request) {
        return mock(service.confirmMockPayment(orderRef, request.commandId()));
    }

    @PostMapping("/orders/{orderRef}/mock-topup")
    ResponseEntity<OrderProjection> topup(@PathVariable String orderRef, @Valid @RequestBody TopupRequest request) {
        return mock(service.completeMockTopup(orderRef, request.mnpState()));
    }

    @GetMapping("/orders/{orderRef}")
    ResponseEntity<OrderProjection> recover(@PathVariable String orderRef) { return mock(service.getOrder(orderRef)); }

    @ExceptionHandler(MockFlowService.FlowRejectedException.class)
    ResponseEntity<Map<String, String>> rejected(MockFlowService.FlowRejectedException error) {
        return ResponseEntity.unprocessableEntity().header(MOCK_HEADER, "true")
                .body(Map.of("status", "REJECTED", "code", error.getMessage()));
    }

    private static <T> ResponseEntity<T> mock(T body) {
        return ResponseEntity.ok().header(MOCK_HEADER, "true").body(body);
    }

    record QuoteRequest(@NotBlank String phone, @NotBlank String operatorCode,
                        @NotBlank String productCode, @NotNull MnpState mnpState) {}
    record CreateOrderRequest(@NotBlank String quoteRef, @NotBlank String commandId) {}
    record CommandRequest(@NotBlank String commandId) {}
    record TopupRequest(@NotNull MnpState mnpState) {}
}
