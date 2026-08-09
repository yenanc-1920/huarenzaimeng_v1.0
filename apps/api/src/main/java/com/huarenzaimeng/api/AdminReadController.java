package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@Profile("release-mysql")
@RequestMapping("/admin-read/v1/pages")
class AdminReadController {
    private static final Logger LOG = LoggerFactory.getLogger(AdminReadController.class);
    private final AdminReadService service;
    AdminReadController(AdminReadService service) { this.service = service; }

    @GetMapping("/{pageId}") ResponseEntity<?> read(@PathVariable String pageId, HttpServletRequest request) {
        if (!"SUPER_ADMIN".equals(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE))) return ResponseEntity.status(403).build();
        if (!request.getParameterMap().isEmpty() || request.getContentLengthLong() > 0) return ResponseEntity.badRequest().build();
        try {
            AdminReadService.AdminProjection projection = service.read(pageId);
            return projection == null ? ResponseEntity.notFound().build()
                    : ResponseEntity.ok().header("Cache-Control", "no-store").body(projection);
        } catch (RuntimeException unavailable) {
            LOG.warn("admin_read_failed pageId={} category={}", pageId, unavailable.getClass().getSimpleName());
            return ResponseEntity.status(503).header("Cache-Control", "no-store").build();
        }
    }
}
