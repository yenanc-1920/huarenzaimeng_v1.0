package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ReleasePublicControllerTest {
    private final ReleasePublicController controller = new ReleasePublicController(
            mock(CatalogService.class), mock(ContentService.class),
            Clock.fixed(Instant.parse("2026-08-15T06:00:00Z"), ZoneOffset.UTC));

    @Test void eligibilityIsReadOnlyAndNormalizesFormatting() {
        var accepted=controller.eligibility("+880 1712-345678");
        assertEquals(200,accepted.getStatusCode().value());
        assertEquals(Boolean.TRUE,((Map<?,?>)accepted.getBody()).get("eligible"));
        var rejected=controller.eligibility("NOT-A-PHONE");
        assertEquals(Boolean.FALSE,((Map<?,?>)rejected.getBody()).get("eligible"));
    }

    @Test void overviewUsesServerClockAndNeverClaimsHolidayAuthority() {
        var response=controller.overview();
        assertEquals(200,response.getStatusCode().value());
        assertEquals("TEMPORAL_OVERVIEW_READ_ERROR",response.getBody().projectCode());
        assertEquals("USER_INITIATED_READ_ONLY",response.getBody().retryClass());
        assertEquals("READ_ERROR",response.getBody().holidays().get("china").state());
    }
}
