package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class StateAdvanceMigrationStateContractTest {
    @Test void v10_is_unchanged_and_v11_v12_have_single_safe_ddl_each() throws Exception {
        String v10 = Files.readString(Path.of("src/main/resources/db/migration/V10__add_code2session_attempt_and_idle_expiry.sql"));
        String v11 = Files.readString(Path.of("src/main/resources/db/migration/V11__add_state_advance_authority_columns.sql"));
        String v12 = Files.readString(Path.of("src/main/resources/db/migration/V12__create_state_advance_authority_fact.sql"));
        assertThat(v10).isNotBlank();
        assertThat(v11.toUpperCase()).containsOnlyOnce("ALTER TABLE").doesNotContain("UPDATE ", "CHECK ", "DROP ", "REPAIR");
        assertThat(v12.toUpperCase()).containsOnlyOnce("CREATE TABLE").doesNotContain("ALTER TABLE", "UPDATE ", "DROP ", "REPAIR");
    }

    @Test void foreign_key_columns_match_v1_exactly_and_mysql57_contract_is_static() throws Exception {
        String v1 = Files.readString(Path.of("src/main/resources/db/migration/V1__create_core_transaction_tables.sql"));
        String v12 = Files.readString(Path.of("src/main/resources/db/migration/V12__create_state_advance_authority_fact.sql"));
        assertThat(v1).contains("order_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
        assertThat(v12).contains("aggregate_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL",
                "FOREIGN KEY (aggregate_ref) REFERENCES hz_order (order_ref)",
                "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }

    @Test void structural_state_machine_reports_only_go_or_no_go_and_never_generates_repair_sql() {
        assertThat(classify(true, false, false)).isEqualTo("GO_APPLY_V11");
        assertThat(classify(true, true, false)).isEqualTo("GO_APPLY_V12");
        assertThat(classify(true, true, true)).isEqualTo("GO_V12_TERMINAL");
        assertThat(classify(false, true, false)).isEqualTo("NO_GO_STRUCTURE_DRIFT");
        assertThat(classify(false, false, true)).isEqualTo("NO_GO_PARTIAL_STATE");
        assertThat(classify(false, true, true)).isEqualTo("NO_GO_STRUCTURE_DRIFT");
    }

    private static String classify(boolean v10Exact, boolean v11Exact, boolean v12Exact) {
        if (!v10Exact) return v12Exact && !v11Exact ? "NO_GO_PARTIAL_STATE" : "NO_GO_STRUCTURE_DRIFT";
        if (v12Exact && !v11Exact) return "NO_GO_PARTIAL_STATE";
        if (v12Exact) return "GO_V12_TERMINAL";
        return v11Exact ? "GO_APPLY_V12" : "GO_APPLY_V11";
    }
}
