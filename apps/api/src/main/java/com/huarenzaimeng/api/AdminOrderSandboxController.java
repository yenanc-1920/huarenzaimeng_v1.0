package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("release-mysql")
@RequestMapping("/admin-read/v1/orders")
class AdminOrderSandboxController {
    private final AdminOrderSandboxService service;

    AdminOrderSandboxController(AdminOrderSandboxService service) { this.service = service; }

    @GetMapping("/{orderRef}/sandbox-status")
    ResponseEntity<?> read(@PathVariable String orderRef, HttpServletRequest request) {
        if (!"SUPER_ADMIN".equals(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE))) {
            return ResponseEntity.status(403).build();
        }
        if (!request.getParameterMap().isEmpty() || request.getContentLengthLong() > 0) {
            return ResponseEntity.badRequest().build();
        }
        try {
            AdminOrderSandboxService.OrderSandboxProjection projection = service.read(orderRef);
            return projection == null ? ResponseEntity.notFound().build()
                    : ResponseEntity.ok().header("Cache-Control", "no-store").body(projection);
        } catch (RuntimeException unavailable) {
            return ResponseEntity.status(503).header("Cache-Control", "no-store").build();
        }
    }
}
