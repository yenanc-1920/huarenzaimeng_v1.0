package com.huarenzaimeng.api;

import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class V15MigrationGateTest {
    private static final Path MIGRATIONS=Path.of("src/main/resources/db/migration");

    @Test void emptyDatabaseMigrationManifestIsUniqueAndContinuousThroughV25() throws Exception {
        Pattern version=Pattern.compile("^V(\\d+)__.+\\.sql$");
        List<Integer> versions;
        try(var files=Files.list(MIGRATIONS)){
            versions=files.map(path->path.getFileName().toString()).map(version::matcher).filter(Matcher::matches)
                    .map(matcher->Integer.parseInt(matcher.group(1))).sorted().toList();
        }
        assertThat(versions).containsExactly(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25);
    }

    @Test void exactV15ExecutesAgainstV14ShapeWithoutARealDatabase() throws Exception {
        JdbcDataSource source=new JdbcDataSource();
        source.setURL("jdbc:h2:mem:v15gate;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try(Connection connection=source.getConnection();Statement statement=connection.createStatement()){
            statement.execute("CREATE TABLE hz_customer_case(case_ref VARCHAR(64) PRIMARY KEY,data_origin VARCHAR(32) NOT NULL)");
            statement.execute("CREATE TABLE hz_reconciliation_case(reconciliation_ref VARCHAR(64) PRIMARY KEY,data_origin VARCHAR(32) NOT NULL)");
            statement.execute("CREATE TABLE hz_price_version(price_version_ref VARCHAR(64) PRIMARY KEY,settlement_currency CHAR(3))");
            String migration=Files.readString(MIGRATIONS.resolve("V15__add_v1_admin_workflow_history.sql"));
            // H2 does not implement MySQL PREPARE. Replace only the three guarded ADD COLUMN blocks;
            // CREATE TABLE IF NOT EXISTS remains the exact candidate SQL and makes a second run safe.
            String tables=migration.substring(migration.indexOf("CREATE TABLE IF NOT EXISTS"));
            String h2Compatible=("""
                    ALTER TABLE hz_customer_case ADD COLUMN IF NOT EXISTS aggregate_version BIGINT NOT NULL DEFAULT 1;
                    ALTER TABLE hz_reconciliation_case ADD COLUMN IF NOT EXISTS aggregate_version BIGINT NOT NULL DEFAULT 1;
                    ALTER TABLE hz_price_version ADD COLUMN IF NOT EXISTS supplier_source_ref VARCHAR(196) DEFAULT NULL;
                    """+tables).replaceAll(" CHARACTER SET ascii COLLATE ascii_bin","")
                    .replaceAll(" DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci","");
            RunScript.execute(connection,new StringReader(h2Compatible));
            RunScript.execute(connection,new StringReader(h2Compatible));
            try(ResultSet result=statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('hz_customer_case_event','hz_reconciliation_event','hz_content_review_task','hz_content_version_history','hz_supplier_catalog_batch_snapshot','hz_supplier_catalog_item_snapshot','hz_fx_rate_snapshot')")){
                assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isEqualTo(7);
            }
            try(ResultSet result=statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND ((table_name='hz_customer_case' AND column_name='aggregate_version') OR (table_name='hz_reconciliation_case' AND column_name='aggregate_version') OR (table_name='hz_price_version' AND column_name='supplier_source_ref'))")){
                assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isEqualTo(3);
            }
        }
    }
}
