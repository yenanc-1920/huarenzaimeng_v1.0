package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import com.huarenzaimeng.core.ProjectEnvelope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ReleasePublicControllerTest {
    private final ReleasePublicController controller = new ReleasePublicController(
            mock(CatalogService.class), mock(ContentService.class),
            Clock.fixed(Instant.parse("2026-08-15T06:00:00Z"), ZoneOffset.UTC),
            mock(V1DevelopmentDataService.class));

    @Test void eligibilityIsReadOnlyAndNormalizesFormatting() {
        var accepted=controller.eligibility("+880 1712-345678");
        assertEquals(200,accepted.getStatusCode().value());
        ProjectEnvelope<?> envelope=(ProjectEnvelope<?>)accepted.getBody();
        assertEquals("UNKNOWN",((Map<?,?>)envelope.data()).get("outcome"));
        assertEquals("GRAMEENPHONE",((Map<?,?>)envelope.data()).get("operatorCode"));
        var localNumber=controller.eligibility("1312345678");
        ProjectEnvelope<?> localEnvelope=(ProjectEnvelope<?>)localNumber.getBody();
        assertEquals("GRAMEENPHONE",((Map<?,?>)localEnvelope.data()).get("operatorCode"));
        var rejected=controller.eligibility("NOT-A-PHONE");
        assertEquals(400,rejected.getStatusCode().value());
    }

    @Test void overviewUsesPersistentDevelopmentDataService() {
        var response=controller.overview();
        assertEquals(200,response.getStatusCode().value());
        assertNotNull(response.getBody());
    }
}
