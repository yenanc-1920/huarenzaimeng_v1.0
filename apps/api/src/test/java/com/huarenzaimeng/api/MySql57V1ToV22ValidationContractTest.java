package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MySql57V1ToV22ValidationContractTest {
    private static final Path SCRIPT = Path.of("scripts/mysql57-validation/Invoke-MySql57V1ToV22Validation.ps1");
    private static final Path RUNBOOK = Path.of("../../docs/operations/mysql57-v1-v22-validation.md");

    @Test void scriptIsDryRunByDefaultAndGeneratesOnlyIsolatedDatabaseNames() throws Exception {
        String script = Files.readString(SCRIPT);
        assertThat(script)
                .contains("[string]$Action = 'DryRun'")
                .contains("$databaseName = \"hz_verify_$RunId\"")
                .contains("^[a-z0-9]{8,32}$")
                .contains("huarenzaimeng|prod|stage|test|dev|it_vnext")
                .contains("zeroConnection = ($Action -eq 'DryRun')")
                .contains("EXECUTE_MYSQL57_VERIFY:${databaseName}:$Scenario")
                .doesNotContain("CREATE DATABASE", "DROP DATABASE", "TRUNCATE TABLE", "flyway repair", "flyway clean");
    }

    @Test void scriptRequiresContinuousManifestAndBindsDatabaseServerAndVersionIdentity() throws Exception {
        String script = Files.readString(SCRIPT);
        assertThat(script)
                .contains("MIGRATION_MANIFEST_MUST_BE_CONTINUOUS_V1_TO_V22")
                .contains("migrationManifestSha256")
                .contains("DATABASE(), COALESCE(@@server_uuid,''), VERSION()")
                .contains("MYSQL_5_7_REQUIRED")
                .contains("DATABASE_SERVER_VERSION_IDENTITY_BINDING_FAILED")
                .contains("22`t22`t22`t22`t0")
                .contains("V22_TERMINAL_OBJECT_MISMATCH")
                .contains("flywayHistoryTableCount", "failedMigrations", "columnCount")
                .contains("TEMPORARY_MYSQL57_VALIDATION_ONLY_NOT_PRODUCTION_EVIDENCE");
    }

    @Test void executeValidatesEveryLocalInputBeforeTheFirstConnection() throws Exception {
        String script = Files.readString(SCRIPT);
        int executeBranch = script.indexOf("if ($Action -eq 'Execute')");
        int firstConnection = script.indexOf("$serverIdentity = @(Invoke-MysqlRead");
        assertThat(executeBranch).isPositive();
        assertThat(firstConnection).isGreaterThan(executeBranch);
        String preConnection = script.substring(executeBranch, firstConnection);
        assertThat(preConnection)
                .contains("$mysqlCredentialUser = Assert-SecureCredentialFile $MysqlDefaultsFile 'MYSQL'")
                .contains("$flywayCredentialUser = Assert-SecureCredentialFile $FlywayConfigFile 'FLYWAY'")
                .contains("MYSQL_AND_FLYWAY_MIGRATION_USER_MUST_MATCH")
                .contains("Assert-CommandAvailable $MysqlCommand")
                .contains("Assert-CommandAvailable $FlywayCommand")
                .contains("JDBC_BASE_URL_MUST_NOT_CONTAIN_DATABASE_QUERY_OR_CREDENTIALS")
                .contains("JDBC_AND_MYSQL_ENDPOINT_MUST_MATCH")
                .contains("EXECUTE_CONFIRMATION_TOKEN_MISMATCH");
        assertThat(script)
                .contains("CREDENTIAL_FILE_PLACEHOLDER_REJECTED")
                .contains("CREDENTIAL_FILE_PERMISSIONS_TOO_BROAD");
    }

    @Test void differentMysqlAndFlywayUsersFailBeforeConnectionAndAnyFlywayWrite() throws Exception {
        String script = Files.readString(SCRIPT);
        int mismatchRejection = script.indexOf("MYSQL_AND_FLYWAY_MIGRATION_USER_MUST_MATCH");
        int firstConnection = script.indexOf("$serverIdentity = @(Invoke-MysqlRead");
        int firstFlywayExecution = script.indexOf("[void](Invoke-Flyway");
        assertThat(mismatchRejection).isPositive();
        assertThat(firstConnection).isGreaterThan(mismatchRejection);
        assertThat(firstFlywayExecution).isGreaterThan(firstConnection);
        assertThat(script)
                .contains("$userMatches.Count -ne 1")
                .contains("$normalizedUser = $userMatches[0].Groups[1].Value.Trim()")
                .contains("SELECT CURRENT_USER()")
                .contains("MYSQL_AUTHENTICATED_USER_MUST_MATCH_MIGRATION_USER")
                .contains("return $normalizedUser");
    }

    @Test void executionRejectsGlobalHighPrivilegeAndNonTargetDatabaseGrants() throws Exception {
        String script = Files.readString(SCRIPT);
        assertThat(script)
                .contains("SHOW GRANTS FOR CURRENT_USER()")
                .contains("MIGRATION_ACCOUNT_HIGH_PRIVILEGE_REJECTED")
                .contains("MIGRATION_ACCOUNT_NON_TARGET_GRANT_REJECTED")
                .contains("MIGRATION_ACCOUNT_EXACT_PRIVILEGES_REQUIRED")
                .contains("'ALTER', 'CREATE', 'DELETE', 'INDEX', 'INSERT', 'REFERENCES', 'SELECT', 'UPDATE'")
                .contains("PRECREATED_VERIFY_DATABASE_REQUIRED");
    }

    @Test void credentialsAreExternalAndFailuresStopWithoutRetryOrCleanup() throws Exception {
        String script = Files.readString(SCRIPT);
        assertThat(script)
                .contains("--defaults-extra-file=$MysqlDefaultsFile")
                .contains("-configFiles=$FlywayConfigFile")
                .contains("-cleanDisabled=true")
                .contains("-baselineOnMigrate=false")
                .contains("FAILED_STOP_NO_RETRY")
                .contains("PRESERVE_TEMP_DATABASE_RUN_DIAGNOSE_DO_NOT_MUTATE_OR_RETRY")
                .doesNotContain("password=", "-password=", "Remove-Item", "DROP SCHEMA");
    }

    @Test void runbookCoversAllRequiredStartStatesIdempotencyAndInterruptedDdl() throws Exception {
        String runbook = Files.readString(RUNBOOK);
        assertThat(runbook)
                .contains("EMPTY", "V14", "V21")
                .contains("V1→V22", "V15→V22", "V22")
                .contains("幂等检查", "非事务 DDL 中断诊断与受控恢复")
                .contains("失败临时库原样保留")
                .contains("不得再次执行 `Execute`")
                .contains("临时库 PASS 外推为生产 GO");
    }
}
