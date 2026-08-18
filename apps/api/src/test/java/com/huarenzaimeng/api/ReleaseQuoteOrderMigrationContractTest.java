package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReleaseQuoteOrderMigrationContractTest {
    @Test void v17AddsOnlyImmutableReleaseSnapshotTables() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V17__add_release_quote_order_snapshots.sql"));
        assertThat(sql).contains("CREATE TABLE hz_release_quote_snapshot","CREATE TABLE hz_release_order_snapshot",
                "phone_digest","entitlement_snapshot JSON","price_version_ref","snapshot_digest",
                "final_amount_minor","currency CHAR(3)","price_snapshot_digest","quote_snapshot_digest",
                "UNIQUE KEY uk_hz_release_order_quote");
        assertThat(sql).doesNotContain("ALTER TABLE","DROP TABLE","createLocalSynthetic","MockFlowService");
    }

    @Test void formalQuoteOrderPathHasNoMockOrSyntheticDependency() throws Exception {
        String controller=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/ReleaseBuyerFlowController.java"));
        String service=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/ReleaseQuoteOrderService.java"));
        assertThat(controller+service).doesNotContain("MockFlowService","createLocalSynthetic","CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT");
    }
}
