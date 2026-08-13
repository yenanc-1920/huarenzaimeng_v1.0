import com.huarenzaimeng.api.DataMigrationOracleVerifier;
import java.sql.DriverManager;

public final class OracleVerifierProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("ACTUAL_DATABASE_EXPECTED_DATABASE_UUID_STATE_REQUIRED");
        if (!args[0].matches("hz_oracle_(v11v12_01|case_[1-4])")) throw new IllegalArgumentException("DATABASE_NOT_ALLOWED");
        if (!args[1].matches("hz_oracle_(v11v12_01|case_[1-4])")) throw new IllegalArgumentException("EXPECTED_DATABASE_NOT_ALLOWED");
        if (!args[2].matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("EXPECTED_UUID_INVALID");
        try (var connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:33308/" + args[0] + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Dhaka",
                "root", "")) {
            var actual = DataMigrationOracleVerifier.verify(connection, args[1], args[2]);
            if (!actual.name().equals(args[3])) throw new IllegalStateException("STATE_MISMATCH_" + actual);
            System.out.println("ORACLE_STATE=" + actual);
        }
    }
}
