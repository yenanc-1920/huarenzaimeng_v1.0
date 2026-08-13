package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("release-mysql")
@RequestMapping("/admin-read/v1/data-integration/readiness")
@ConditionalOnProperty(name = "hz.data-integration.readiness-enabled", havingValue = "true")
final class DataIntegrationReadinessController {
    private final DataIntegrationReadinessService service;

    DataIntegrationReadinessController(DataIntegrationReadinessService service) { this.service = service; }

    @GetMapping ResponseEntity<?> read(HttpServletRequest request) {
        if (!"SUPER_ADMIN".equals(request.getAttribute(AdminSessionFilter.TRUSTED_ROLE))) {
            return ResponseEntity.status(403).header("Cache-Control", "no-store").build();
        }
        if (!request.getParameterMap().isEmpty() || hasBodySignal(request)) {
            return ResponseEntity.badRequest().header("Cache-Control", "no-store").build();
        }
        try {
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.read());
        } catch (DataIntegrationReadinessStageException unavailable) {
            return unavailable(safeStage(unavailable.stage()));
        } catch (RuntimeException unavailable) {
            return unavailable("INTERNAL_SAFE");
        }
    }

    private static ResponseEntity<Failure> unavailable(String stageCode) {
        return ResponseEntity.status(503).header("Cache-Control", "no-store")
                .body(new Failure("UNAVAILABLE", "DATA_INTEGRATION_READINESS_UNAVAILABLE",
                        "SAFE_RETRY_MANUAL", stageCode));
    }

    private static String safeStage(DataIntegrationReadinessStageException.Stage stage) {
        if (stage == null) return "INTERNAL_SAFE";
        return switch (stage) {
            case APP_CONNECT -> "APP_CONNECT";
            case FLYWAY_CONNECT -> "FLYWAY_CONNECT";
            case READ_ONLY_SETUP_APP -> "READ_ONLY_SETUP_APP";
            case READ_ONLY_SETUP_FLYWAY -> "READ_ONLY_SETUP_FLYWAY";
            case DB_IDENTITY -> "DB_IDENTITY";
            case APP_GRANTS -> "APP_GRANTS";
            case FLYWAY_GRANTS -> "FLYWAY_GRANTS";
            case FLYWAY_HISTORY -> "FLYWAY_HISTORY";
            case SCHEMA -> "SCHEMA";
            case MIGRATION_DISCOVERY -> "MIGRATION_DISCOVERY";
            case BACKUP_STATUS -> "BACKUP_STATUS";
            case INTERNAL_SAFE -> "INTERNAL_SAFE";
        };
    }

    private static boolean hasBodySignal(HttpServletRequest request) {
        if (request.getHeader("Transfer-Encoding") != null || request.getContentLengthLong() > 0) return true;
        try {
            var input = request.getInputStream();
            return !input.isFinished() || input.available() > 0;
        } catch (IOException | RuntimeException unavailable) {
            return true;
        }
    }

    record Failure(String status, String projectCode, String retryClass, String stageCode) {}
}
