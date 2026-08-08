package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class P021CloudBaseConsoleFixtureContractTest {
    private static final Path ROOT = Path.of("src/test/resources/db/fixture");
    private static final List<String> ORDER_REFS = List.of("IT-P021-AWAITING", "IT-P021-PAYMENT",
            "IT-P021-TOPUP", "IT-P021-UNKNOWN", "IT-P021-DELIVERED", "IT-P021-REFUNDED",
            "IT-P021-REVOKED");

    @Test void cloudBaseFixtureIsConnectionIndependentAndTestDatabaseScoped() throws Exception {
        String sql = read("VnextP021OrderDetailCloudBaseConsoleFixture.sql");
        String upper = executableSql(sql).toUpperCase();
        assertThat(upper).doesNotContain("USE ", "TEMPORARY", "@P021", "DROP TABLE", "TRUNCATE");
        assertThat(sql).doesNotContain("huarenzaimeng.hz_", "huaren-api", "prod-d3g9ntdmsdf9d7877");
        assertThat(sql).contains("huarenzaimeng_it_vnext.hz_quote",
                "huarenzaimeng_it_vnext.hz_order", "huarenzaimeng_it_vnext.hz_order_detail_projection");
        assertThat(upper).contains("WHERE ORDER_REF IN", "WHERE QUOTE_REF IN");
        for (String ref : ORDER_REFS) assertThat(sql).contains(ref, ref.replace("IT-P021", "IT-Q-P021"));
    }

    @Test void preAndPostChecksAreReadOnlyAndFullyQualified() throws Exception {
        for (String name : List.of("VnextP021OrderDetailCloudBaseConsolePrecheck.sql",
                "VnextP021OrderDetailCloudBaseConsolePostcheck.sql")) {
            String sql = read(name); String upper = sql.toUpperCase();
            assertThat(upper).doesNotContain("INSERT ", "UPDATE ", "DELETE ", "CREATE ", "DROP ", "TRUNCATE");
            assertThat(sql).contains("huarenzaimeng_it_vnext.");
            for (String ref : ORDER_REFS) assertThat(sql).contains(ref);
        }
    }

    private static String read(String name) throws Exception {
        return Files.readString(ROOT.resolve(name), StandardCharsets.UTF_8);
    }

    private static String executableSql(String sql) {
        return sql.replaceAll("(?m)^\\s*--.*$", "");
    }
}
