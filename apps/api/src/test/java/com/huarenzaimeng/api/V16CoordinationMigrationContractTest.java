package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import java.io.StringReader;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class V16CoordinationMigrationContractTest {
    @Test void migrationAddsFourteenReentrantCoordinationTablesAndNoSecretColumns() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V16__add_payment_topup_coordination.sql"));
        assertThat(count(sql,"CREATE TABLE IF NOT EXISTS")).isEqualTo(14);
        assertThat(sql).contains("hz_payment_coordination","hz_payment_notification","hz_topup_coordination",
                "hz_topup_callback","hz_provider_balance_reservation","hz_business_event_link",
                "hz_payment_refund","hz_order_fulfillment_snapshot","hz_provider_balance_ledger",
                "hz_provider_reconciliation_task","hz_provider_balance_position","buyer_login_rate_window","hz_customer_case_subject_binding",
                "hz_content_report_window","submit_latched","unknown_queries_remaining","request_digest","event_digest",
                "notification_digest","certificate_serial","entitlement_digest","query_deadline","original_payment_state","safety_buffer_minor","expires_at",
                "refund_query_budget_remaining","refund_query_deadline");
        assertThat(sql.toLowerCase()).doesNotContain("api_key","private_key","merchant_secret","raw_code","bearer_token");
    }
    @Test void noEarlierMigrationWasCopiedIntoV16(){assertThat(Path.of("src/main/resources/db/migration/V15__add_v1_admin_workflow_history.sql")).exists();assertThat(Path.of("src/main/resources/db/migration/V16__add_payment_topup_coordination.sql")).exists();}
    @Test void normalizedCandidateIsReentrantAndCreatesAllFourteenTables() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V16__add_payment_topup_coordination.sql"))
                .replaceAll(" CHARACTER SET ascii COLLATE ascii_bin","")
                .replaceAll(" DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci","");
        JdbcDataSource source=new JdbcDataSource();source.setURL("jdbc:h2:mem:v16gate;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try(var connection=source.getConnection();var statement=connection.createStatement()){
            RunScript.execute(connection,new StringReader(sql));RunScript.execute(connection,new StringReader(sql));
            try(var result=statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('hz_payment_coordination','hz_payment_notification','hz_payment_refund','hz_topup_coordination','hz_topup_callback','hz_order_fulfillment_snapshot','hz_provider_balance_reservation','hz_provider_balance_ledger','hz_provider_balance_position','hz_provider_reconciliation_task','hz_business_event_link','buyer_login_rate_window','hz_customer_case_subject_binding','hz_content_report_window')")){assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isEqualTo(14);}
        }
    }
    private static int count(String text,String needle){int n=0,p=0;while((p=text.indexOf(needle,p))>=0){n++;p+=needle.length();}return n;}
}
