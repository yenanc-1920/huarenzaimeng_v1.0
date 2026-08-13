package com.huarenzaimeng.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class DataMigrationOracleVerifierContractTest {
    private record Scenario(String name, String phase, Consumer<List<String>> mutation,
                            DataMigrationOracleVerifier.State expected) {}

    @TestFactory
    Stream<DynamicTest> fixed_oracle_matrix_has_exactly_24_cases() {
        List<Scenario> scenarios = new ArrayList<>();
        scenarios.add(exact("pre_exact", "PRE_V10", DataMigrationOracleVerifier.State.PRE_V10));
        scenarios.add(exact("mid_exact", "MID_V11", DataMigrationOracleVerifier.State.MID_V11));
        scenarios.add(exact("post_exact", "POST_V12", DataMigrationOracleVerifier.State.POST_V12));

        scenarios.add(drift("history_checksum", lines -> alter(lines, "H|", "|CHECKSUM_DRIFT")));
        scenarios.add(drift("history_missing", lines -> remove(lines, "H|")));
        scenarios.add(drift("history_extra", lines -> lines.add("H|EXTRA")));
        scenarios.add(drift("history_version", lines -> alter(lines, "H|", "|VERSION_DRIFT")));
        scenarios.add(drift("history_type", lines -> alter(lines, "H|", "|TYPE_DRIFT")));
        scenarios.add(drift("history_script", lines -> alter(lines, "H|", "|SCRIPT_DRIFT")));
        scenarios.add(drift("history_success", lines -> alter(lines, "H|", "|SUCCESS_DRIFT")));
        scenarios.add(drift("order_column_missing", lines -> remove(lines, "C|V8:hz_order|")));
        scenarios.add(drift("order_column_extra", lines -> lines.add("C|V8:hz_order|V2:99|V12:oracle_drift|V3:int|V3:YES|N|V0:|N|N")));
        scenarios.add(drift("order_column_type", lines -> alter(lines, "C|V8:hz_order|", "|TYPE_DRIFT")));
        scenarios.add(drift("order_column_nullable", lines -> alter(lines, "C|V8:hz_order|", "|NULLABLE_DRIFT")));
        scenarios.add(drift("order_column_default", lines -> alter(lines, "C|V8:hz_order|", "|DEFAULT_DRIFT")));
        scenarios.add(drift("order_column_charset", lines -> alter(lines, "C|V8:hz_order|", "|CHARSET_DRIFT")));
        scenarios.add(drift("order_column_collation", lines -> alter(lines, "C|V8:hz_order|", "|COLLATION_DRIFT")));
        scenarios.add(drift("order_column_ordinal", lines -> alter(lines, "C|V8:hz_order|", "|ORDINAL_DRIFT")));
        scenarios.add(drift("fact_column_missing", lines -> remove(lines, "C|V31:hz_state_advance_authority_fact|")));
        scenarios.add(drift("fact_column_extra", lines -> lines.add("C|V31:hz_state_advance_authority_fact|EXTRA")));
        scenarios.add(drift("index_missing", lines -> remove(lines, "I|V31:hz_state_advance_authority_fact|")));
        scenarios.add(drift("index_extra", lines -> lines.add("I|V31:hz_state_advance_authority_fact|EXTRA")));
        scenarios.add(drift("foreign_key_missing", lines -> remove(lines, "F|")));
        scenarios.add(drift("foreign_key_rule", lines -> alter(lines, "F|", "|RULE_DRIFT")));

        assertEquals(24, scenarios.size());
        return scenarios.stream().map(scenario -> DynamicTest.dynamicTest(scenario.name(), () -> {
            List<String> actual = oracle(scenario.phase());
            scenario.mutation().accept(actual);
            assertEquals(scenario.expected(), DataMigrationOracleVerifier.classify(actual));
        }));
    }

    private static Scenario exact(String name, String phase, DataMigrationOracleVerifier.State state) {
        return new Scenario(name, phase, ignored -> {}, state);
    }

    private static Scenario drift(String name, Consumer<List<String>> mutation) {
        return new Scenario(name, "POST_V12", mutation, DataMigrationOracleVerifier.State.NO_GO_PARTIAL_OR_DRIFT);
    }

    private static List<String> oracle(String phase) throws Exception {
        var resource = DataMigrationOracleVerifierContractTest.class.getResourceAsStream(
                "/data-migration-oracle/" + phase + ".txt");
        if (resource == null) throw new IllegalStateException("ORACLE_RESOURCE_MISSING");
        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.startsWith("PHASE|") && !line.startsWith("TARGET|")
                            && !line.startsWith("PAYLOAD_SHA256|"))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
    }

    private static void alter(List<String> lines, String prefix, String suffix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                lines.set(i, lines.get(i) + suffix);
                return;
            }
        }
        throw new IllegalStateException("MUTATION_TARGET_MISSING");
    }

    private static void remove(List<String> lines, String prefix) {
        if (!lines.removeIf(line -> line.startsWith(prefix))) {
            throw new IllegalStateException("MUTATION_TARGET_MISSING");
        }
    }
}
