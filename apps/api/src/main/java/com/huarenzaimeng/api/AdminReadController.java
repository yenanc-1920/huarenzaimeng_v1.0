package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@Profile("release-mysql")
@RequestMapping("/admin-read/v1/pages")
class AdminReadController {
    private static final Logger LOG = LoggerFactory.getLogger(AdminReadController.class);
    private static final Map<String,Set<String>> READ_ROLES=Map.of(
            "A100",Set.of("CS","SUPER_ADMIN"),
            "A110",Set.of("FIN","CS","SUPER_ADMIN"),
            "A120",Set.of("CONTENT","CONTENT_OPERATOR","CONTENT_REVIEWER","SUPER_ADMIN"),"A121",Set.of("CONTENT","CONTENT_OPERATOR","CONTENT_REVIEWER","SUPER_ADMIN"),
            "A122",Set.of("CONTENT","CONTENT_OPERATOR","CONTENT_REVIEWER","SUPER_ADMIN"),"A130",Set.of("CONTENT","CONTENT_OPERATOR","CONTENT_REVIEWER","SUPER_ADMIN"),
            "A140",Set.of("FIN","CS","SUPER_ADMIN"));
    private final AdminReadService service;
    private final AdminA140DetailService a140Details;
    private static final Pattern ORDER_REF=Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    @Autowired AdminReadController(AdminReadService service, AdminA140DetailService a140Details) {
        this.service = service;
        this.a140Details = a140Details;
    }
    AdminReadController(AdminReadService service) { this(service,null); }

    @GetMapping("/A140/{orderRef}") ResponseEntity<?> a140Detail(@PathVariable String orderRef,HttpServletRequest request) {
        String role=String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE));
        if (!READ_ROLES.get("A140").contains(role)) return ResponseEntity.status(403).build();
        if (!ORDER_REF.matcher(orderRef).matches() || !request.getParameterMap().isEmpty() || request.getContentLengthLong()>0)
            return ResponseEntity.badRequest().build();
        if (a140Details==null) return ResponseEntity.status(503).build();
        try {
            return a140Details.read(orderRef,role)
                    .<ResponseEntity<?>>map(detail->ResponseEntity.ok().header("Cache-Control","no-store").body(detail))
                    .orElseGet(()->ResponseEntity.notFound().build());
        } catch (RuntimeException unavailable) {
            LOG.warn("admin_a140_detail_failed category={}",unavailable.getClass().getSimpleName());
            return ResponseEntity.status(503).header("Cache-Control","no-store").build();
        }
    }

    @GetMapping("/{pageId}") ResponseEntity<?> read(@PathVariable String pageId, HttpServletRequest request) {
        String role=String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE));
        if (!READ_ROLES.getOrDefault(pageId,Set.of()).contains(role)) return ResponseEntity.status(403).build();
        if (!request.getParameterMap().isEmpty() || request.getContentLengthLong() > 0) return ResponseEntity.badRequest().build();
        try {
            AdminReadService.AdminProjection projection = "A130".equals(pageId)?service.read(pageId,role):service.read(pageId);
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
        if (!Set.of("CONTENT","CONTENT_OPERATOR","CONTENT_REVIEWER","SUPER_ADMIN").contains(role)) return ResponseEntity.status(403).build();
        if (!request.getParameterMap().isEmpty() || request.getContentLengthLong() > 0) return ResponseEntity.badRequest().build();
        AdminReadService.AdminProjection projection=service.cities();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(new AdminReadService.AdminProjection(
                projection.schemaVersion(),projection.projectionVersion(),projection.pageId(),role,projection.items()));
    }

    @GetMapping("/A130/channels") ResponseEntity<?> channels(HttpServletRequest request) {
        String role=String.valueOf(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE));
        if (!Set.of("CONTENT","CONTENT_OPERATOR","CONTENT_REVIEWER","SUPER_ADMIN").contains(role)) return ResponseEntity.status(403).build();
        if (!request.getParameterMap().isEmpty() || request.getContentLengthLong() > 0) return ResponseEntity.badRequest().build();
        AdminReadService.AdminProjection projection=service.channels();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(new AdminReadService.AdminProjection(
                projection.schemaVersion(),projection.projectionVersion(),projection.pageId(),role,projection.items()));
    }
}
