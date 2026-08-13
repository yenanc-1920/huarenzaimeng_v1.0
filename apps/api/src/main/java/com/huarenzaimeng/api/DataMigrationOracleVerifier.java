package com.huarenzaimeng.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.HexFormat;

public final class DataMigrationOracleVerifier {
    public static final String DEPLOYMENT_DATABASE = "huarenzaimeng_it_vnext";
    private static final String PRE_SHA = "B7AB11C95F9702221F00ED4E960D7E45598D73E480AEE503ADC5E1ECC8BE41C5";
    private static final String MID_SHA = "E648F2AC6214E418B2EA5973D5C5D1B3C850CBCCB132FDBE52AA50176355D01E";
    private static final String POST_SHA = "A0305BB9E506EA8CE1C6269D62C33D7EF92E203B993F30700D11017940770801";
    public enum State { PRE_V10, MID_V11, POST_V12, NO_GO_PARTIAL_OR_DRIFT, NO_GO_ORACLE_UNAVAILABLE }

    private DataMigrationOracleVerifier() {}

    public static State verify(Connection connection, String expectedDatabaseName, String expectedServerUuid) {
        try {
            if (expectedDatabaseName == null || expectedDatabaseName.isBlank()) return State.NO_GO_ORACLE_UNAVAILABLE;
            if (expectedServerUuid == null || expectedServerUuid.isBlank()) return State.NO_GO_ORACLE_UNAVAILABLE;
            var identity = identity(connection);
            if (!expectedDatabaseName.equals(identity.databaseName())
                    || !expectedServerUuid.equals(identity.serverUuid())) return State.NO_GO_PARTIAL_OR_DRIFT;
            return classify(collect(connection));
        } catch (Exception unavailable) {
            return State.NO_GO_ORACLE_UNAVAILABLE;
        }
    }

    private static DatabaseIdentity identity(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT DATABASE(), @@server_uuid")) {
            return result.next() ? new DatabaseIdentity(result.getString(1), result.getString(2)) : new DatabaseIdentity(null, null);
        }
    }

    private record DatabaseIdentity(String databaseName, String serverUuid) {}

    static State classify(List<String> actual) throws Exception {
        if (phaseSha("PRE_V10", 10, actual).equals(PRE_SHA)) return State.PRE_V10;
        if (phaseSha("MID_V11", 11, actual).equals(MID_SHA)) return State.MID_V11;
        if (phaseSha("POST_V12", 12, actual).equals(POST_SHA)) return State.POST_V12;
        return State.NO_GO_PARTIAL_OR_DRIFT;
    }

    static List<String> collect(Connection connection) throws Exception {
        List<String> lines = new ArrayList<>();
        collect(connection, lines, "H", "SELECT installed_rank,version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank", 7);
        collect(connection, lines, "C", "SELECT table_name,ordinal_position,column_name,column_type,is_nullable,column_default,extra,character_set_name,collation_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('hz_order','hz_state_advance_authority_fact') ORDER BY table_name,ordinal_position", 9);
        collect(connection, lines, "I", "SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('hz_order','hz_state_advance_authority_fact') AND (table_name='hz_state_advance_authority_fact' OR index_name='PRIMARY') ORDER BY table_name,index_name,seq_in_index", 5);
        collect(connection, lines, "F", "SELECT k.table_name,k.constraint_name,k.ordinal_position,k.column_name,k.referenced_table_name,k.referenced_column_name,r.update_rule,r.delete_rule FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema AND r.constraint_name=k.constraint_name AND r.table_name=k.table_name WHERE k.table_schema=DATABASE() AND k.table_name IN ('hz_order','hz_state_advance_authority_fact') ORDER BY k.table_name,k.constraint_name,k.ordinal_position", 8);
        return List.copyOf(lines);
    }

    private static String phaseSha(String phase, int target, List<String> actual) throws Exception {
        List<String> payload = new ArrayList<>();
        payload.add("PHASE|" + phase);
        payload.add("TARGET|" + target);
        payload.addAll(actual);
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", payload).getBytes(StandardCharsets.UTF_8)));
    }

    private static void collect(Connection connection, List<String> output, String kind, String sql, int columns) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                StringBuilder line = new StringBuilder(kind);
                for (int i = 1; i <= columns; i++) line.append('|').append(normalize(result.getString(i)));
                output.add(line.toString());
            }
        }
    }

    private static String normalize(String value) { return value == null ? "N" : "V" + value.length() + ":" + value; }
}
