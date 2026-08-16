package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class V1PersistentDevelopmentDataContractTest {
    private static final Path MAIN=Path.of("src/main");
    private static final Path LOCAL=Path.of("src/local");

    @Test void v14AddsFormalReadModelsWithoutChangingEarlierMigrations() throws Exception {
        String sql=Files.readString(MAIN.resolve("resources/db/migration/V14__add_v1_business_read_models.sql"));
        for(String table:new String[]{"hz_city","hz_directory_entry","hz_holiday_rule","hz_news_article",
                "hz_platform_product","hz_price_version","hz_customer_case","hz_reconciliation_case",
                "hz_provider_channel","hz_directory_report","hz_v1_admin_command","hz_v1_admin_audit",
                "hz_v1_dev_seed_registry"}) assertTrue(sql.contains("CREATE TABLE "+table),table);
        assertTrue(sql.contains("aggregate_version"));
        assertTrue(sql.contains("uk_hz_v1_admin_idempotency"));
    }

    @Test void developmentSeedsArePersistentDeterministicAndNotInReleaseLocation() throws Exception {
        Path seed=LOCAL.resolve("resources/db/devdata/R__v1_manual_dev_sample.sql");
        String sql=Files.readString(seed);
        assertTrue(Files.exists(seed));
        assertTrue(sql.contains("MANUAL_DEV_SAMPLE"));
        for(String operator:new String[]{"GRAMEENPHONE","ROBI","BANGLALINK","AIRTEL","TELETALK"})
            assertTrue(sql.contains("'"+operator+"'"),operator);
        for(String type:new String[]{"'BALANCE'","'DATA'","'BUNDLE'"}) assertTrue(sql.contains(type),type);
        assertFalse(sql.contains("RAND(")); assertFalse(sql.contains("UUID("));
        sql.lines().filter(line -> line.startsWith("INSERT ")).forEach(line ->
                assertTrue(line.startsWith("INSERT IGNORE INTO ")||line.startsWith("INSERT INTO hz_v1_dev_seed_registry"),
                        "repeatable seed must be scoped and non-overwriting: "+line));
        String[] statements=sql.split(";\\s*");
        for(String statement:statements) if(statement.strip().startsWith("INSERT IGNORE INTO"))
            assertTrue(statement.substring(0,statement.indexOf("VALUES")).contains("("),"seed INSERT must name columns");
        assertFalse(sql.contains("REPLACE INTO"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE seed_version=VALUES(seed_version)"));
        assertTrue(sql.contains("source_mode='MANUAL_DEV_SAMPLE' WHERE p.aggregate_version=1"));
        assertTrue(sql.contains("WHERE e.aggregate_version=1"));
        assertTrue(sql.contains("('CUSTOMER_CASE','CASE-DEV-001'"));
        assertTrue(sql.contains("('RECONCILIATION','DIFF-DEV-001'"));
        assertTrue(sql.contains("o.environment='MANUAL_DEV_SAMPLE' AND o.aggregate_version=r.seed_version"));
        assertTrue(sql.contains("JSON_UNQUOTE(JSON_EXTRACT(q.price_snapshot,'$.source'))='MANUAL_DEV_SAMPLE'"));
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM hz_order o WHERE o.quote_ref=q.quote_ref)"));
        assertFalse(sql.contains("REPLACE INTO"));
        String profile=Files.readString(MAIN.resolve("resources/application-local-mysql.yml"));
        assertTrue(profile.contains("classpath:db/migration,classpath:db/devdata"));
        assertTrue(profile.contains("wechat-payment-enabled: false"));
        assertTrue(profile.contains("topup-provider-enabled: false"));
        String release=Files.readString(MAIN.resolve("resources/application-release-mysql.yml"));
        assertFalse(release.contains("classpath:db/devdata"));
        String pom=Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<id>local-devdata</id>"));
        assertTrue(pom.contains("<directory>src/local/resources</directory>"));
        assertFalse(Files.exists(MAIN.resolve("resources/db/devdata")));
    }

    @Test void releaseRoutesUseDatabaseServiceAndAdminWritesAreAudited() throws Exception {
        String publicController=Files.readString(MAIN.resolve("java/com/huarenzaimeng/api/ReleasePublicController.java"));
        String directory=Files.readString(MAIN.resolve("java/com/huarenzaimeng/api/V1DirectoryController.java"));
        String command=Files.readString(MAIN.resolve("java/com/huarenzaimeng/api/V1AdminCommandController.java"));
        assertTrue(publicController.contains("developmentData.catalog"));
        assertTrue(publicController.contains("developmentData.temporalOverview"));
        assertTrue(publicController.contains("developmentData.news"));
        assertTrue(directory.contains("/api/v1/directory"));
        assertTrue(directory.contains("/entries/{entryRef}/reports"));
        assertTrue(directory.contains("hz_directory_report"));
        assertTrue(directory.contains("request.description()==null?\"\""));
        assertTrue(command.contains("Idempotency-Key"));
        assertTrue(command.contains("expectedVersion"));
        assertTrue(command.contains("hz_v1_admin_audit"));
        assertTrue(command.contains("command_status='CLAIMED'"));
        assertTrue(command.contains("IDEMPOTENCY_CONFLICT"));
        assertTrue(command.contains("@ConditionalOnProperty(name=\"hz.v1-dev-data.enabled\",havingValue=\"true\")"));
        assertFalse(command.contains("@Profile({\"mock\""));
        String data=Files.readString(MAIN.resolve("java/com/huarenzaimeng/api/V1DevelopmentDataService.java"));
        assertFalse(data.contains("AS sourceLabel"));
        String buyer=Files.readString(MAIN.resolve("java/com/huarenzaimeng/api/ReleaseBuyerFlowController.java"));
        assertFalse(buyer.contains("MockFlowService"));
        assertFalse(buyer.contains("createLocalSynthetic"));
    }

    @Test void adminReadModelsReturnEveryFieldRequiredToEditDirectoryAndHolidayDrafts() throws Exception {
        String mapper=Files.readString(MAIN.resolve("java/com/huarenzaimeng/api/AdminReadMapper.java"));
        assertTrue(mapper.contains("e.summary"));
        assertTrue(mapper.contains("start_date AS startDate"));
        assertTrue(mapper.contains("end_date AS endDate"));
        assertTrue(mapper.contains("weekend_days AS weekendDays"));
        assertTrue(mapper.contains("effective_from AS effectiveFrom"));
        assertTrue(mapper.contains("effective_until AS effectiveUntil"));
        assertTrue(mapper.contains("p.country_code AS countryCode"));
        assertTrue(mapper.contains("p.data_allowance_mb AS dataAllowanceMb"));
        assertTrue(mapper.contains("p.channel_priority AS channelPriority"));
        assertTrue(mapper.contains("report_ref AS reportRef"));
        String service=Files.readString(MAIN.resolve("java/com/huarenzaimeng/api/AdminReadService.java"));
        assertFalse(service.contains("if (rows == null || rows.isEmpty()) return catalogProjection()"));
    }
}
