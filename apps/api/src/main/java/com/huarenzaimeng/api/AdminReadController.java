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
import java.util.Map;
import java.util.Set;

@RestController
@Profile("release-mysql")
@RequestMapping("/admin-read/v1/pages")
class AdminReadController {
    private static final Logger LOG = LoggerFactory.getLogger(AdminReadController.class);
    private static final Map<String,Set<String>> READ_ROLES=Map.of(
            "A100",Set.of("CS","SUPER_ADMIN"),
            "A110",Set.of("FIN","CS","SUPER_ADMIN"),
            "A120",Set.of("CONTENT","SUPER_ADMIN"),"A121",Set.of("CONTENT","SUPER_ADMIN"),
            "A122",Set.of("CONTENT","SUPER_ADMIN"),"A130",Set.of("CONTENT","SUPER_ADMIN"),
            "A140",Set.of("FIN","CS","SUPER_ADMIN"));
    private final AdminReadService service;
    AdminReadController(AdminReadService service) { this.service = service; }

    @GetMapping("/{pageId}") ResponseEntity<?> read(@PathVariable String pageId, HttpServletRequest request) {
        String role=String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE));
        if (!READ_ROLES.getOrDefault(pageId,Set.of()).contains(role)) return ResponseEntity.status(403).build();
        if (!request.getParameterMap().isEmpty() || request.getContentLengthLong() > 0) return ResponseEntity.badRequest().build();
        try {
            AdminReadService.AdminProjection projection = service.read(pageId);
            return projection == null ? ResponseEntity.notFound().build()
                    : ResponseEntity.ok().header("Cache-Control", "no-store").body(new AdminReadService.AdminProjection(
                            projection.schemaVersion(),projection.projectionVersion(),projection.pageId(),role,projection.items()));
        } catch (RuntimeException unavailable) {
            LOG.warn("admin_read_failed pageId={} category={}", pageId, unavailable.getClass().getSimpleName());
            return ResponseEntity.status(503).header("Cache-Control", "no-store").build();
        }
    }

    @GetMapping("/directory/cities") ResponseEntity<?> cities(HttpServletRequest request) {
        String role=String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE));
        if (!Set.of("CONTENT","SUPER_ADMIN").contains(role)) return ResponseEntity.status(403).build();
        if (!request.getParameterMap().isEmpty() || request.getContentLengthLong() > 0) return ResponseEntity.badRequest().build();
        AdminReadService.AdminProjection projection=service.cities();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(new AdminReadService.AdminProjection(
                projection.schemaVersion(),projection.projectionVersion(),projection.pageId(),role,projection.items()));
    }

    @GetMapping("/A130/channels") ResponseEntity<?> channels(HttpServletRequest request) {
        String role=String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE));
        if (!Set.of("CONTENT","SUPER_ADMIN").contains(role)) return ResponseEntity.status(403).build();
        if (!request.getParameterMap().isEmpty() || request.getContentLengthLong() > 0) return ResponseEntity.badRequest().build();
        AdminReadService.AdminProjection projection=service.channels();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(new AdminReadService.AdminProjection(
                projection.schemaVersion(),projection.projectionVersion(),projection.pageId(),role,projection.items()));
    }
}
