import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class FlywayTargetOracle {
    private static final String URL = "jdbc:mysql://127.0.0.1:33308/hz_oracle_v11v12_01?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Dhaka";
    private static final String USER = "root";
    private static final String PASSWORD = "";
    private static final Path MIGRATIONS = Path.of("E:/workspace/huarenzaimeng/.tmp/DATA-V11-V12-MINIMUM-ORACLE-01/apps/api/src/main/resources/db/migration");

    private FlywayTargetOracle() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("USAGE_TARGET_PHASE");
        int target = Integer.parseInt(args[0]);
        String phase = args[1];
        String expectedPhase = switch (target) { case 10 -> "PRE_V10"; case 11 -> "MID_V11"; case 12 -> "POST_V12"; default -> throw new IllegalArgumentException("TARGET_NOT_ALLOWED"); };
        if (!expectedPhase.equals(phase)) throw new IllegalArgumentException("PHASE_TARGET_MISMATCH");
        Path output = Path.of("E:/workspace/huarenzaimeng/.tmp/DATA-V11-V12-MINIMUM-ORACLE-01/apps/api/src/test/resources/data-migration-oracle/" + phase + ".txt");
        if (Files.exists(output)) throw new IllegalStateException("ORACLE_PHASE_ALREADY_EXISTS");

        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations("filesystem:" + MIGRATIONS)
                .target(Integer.toString(target))
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .connectRetries(0)
                .load();
        flyway.migrate();

        List<String> lines = new ArrayList<>();
        lines.add("PHASE|" + phase);
        lines.add("TARGET|" + target);
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            collect(connection, lines, "H", "SELECT installed_rank,version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank", 7);
            collect(connection, lines, "C", "SELECT table_name,ordinal_position,column_name,column_type,is_nullable,column_default,extra,character_set_name,collation_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('hz_order','hz_state_advance_authority_fact') ORDER BY table_name,ordinal_position", 9);
            collect(connection, lines, "I", "SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('hz_order','hz_state_advance_authority_fact') AND (table_name='hz_state_advance_authority_fact' OR index_name='PRIMARY') ORDER BY table_name,index_name,seq_in_index", 5);
            collect(connection, lines, "F", "SELECT k.table_name,k.constraint_name,k.ordinal_position,k.column_name,k.referenced_table_name,k.referenced_column_name,r.update_rule,r.delete_rule FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema AND r.constraint_name=k.constraint_name AND r.table_name=k.table_name WHERE k.table_schema=DATABASE() AND k.table_name IN ('hz_order','hz_state_advance_authority_fact') ORDER BY k.table_name,k.constraint_name,k.ordinal_position", 8);
        }
        lines.add("PAYLOAD_SHA256|" + sha256(String.join("\n", lines)));
        Files.createDirectories(output.getParent());
        Files.writeString(output, String.join("\n", lines), StandardCharsets.UTF_8);
        MigrationInfo current = flyway.info().current();
        if (current == null || !Integer.toString(target).equals(current.getVersion().getVersion())) throw new IllegalStateException("TARGET_NOT_REACHED");
        System.out.println("ORACLE_PHASE_WRITTEN=" + phase);
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

    private static String normalize(String value) {
        return value == null ? "N" : "V" + value.length() + ":" + value;
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
