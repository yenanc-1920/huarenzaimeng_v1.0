package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;
import java.util.HexFormat;
import java.util.Arrays;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataIntegrationReadinessContractTest {
    @Test void every_fixed_stage_has_one_safe_503_json_mapping_and_no_diagnostic_header() {
        var expected = Set.of("APP_CONNECT", "FLYWAY_CONNECT", "READ_ONLY_SETUP_APP",
                "READ_ONLY_SETUP_FLYWAY", "DB_IDENTITY", "APP_GRANTS", "FLYWAY_GRANTS",
                "FLYWAY_HISTORY", "SCHEMA", "MIGRATION_DISCOVERY", "BACKUP_STATUS", "INTERNAL_SAFE");
        assertThat(Arrays.stream(DataIntegrationReadinessStageException.Stage.values())
                .map(Enum::name).collect(java.util.stream.Collectors.toSet())).isEqualTo(expected);
        AtomicInteger probeCalls = new AtomicInteger();
        AtomicInteger writeSideEffects = new AtomicInteger();

        for (var stage : DataIntegrationReadinessStageException.Stage.values()) {
            var controller = new DataIntegrationReadinessController(new DataIntegrationReadinessService(() -> {
                probeCalls.incrementAndGet();
                throw new DataIntegrationReadinessStageException(stage);
            }));
            var response = controller.read(trusted());

            assertThat(response.getStatusCode().value()).isEqualTo(503);
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(response.getHeaders().keySet())
                    .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("readiness-stage"));
            assertThat(response.getBody()).isEqualTo(new DataIntegrationReadinessController.Failure(
                    "UNAVAILABLE", "DATA_INTEGRATION_READINESS_UNAVAILABLE", "SAFE_RETRY_MANUAL", stage.name()));
            assertThat(response.getBody().getClass().getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .containsExactly("status", "projectCode", "retryClass", "stageCode");
        }
        assertThat(probeCalls).hasValue(12);
        assertThat(writeSideEffects).hasValue(0);
    }

    @Test void unknown_null_and_dynamic_diagnostics_collapse_to_internal_safe_without_leakage() {
        AtomicInteger probeCalls = new AtomicInteger();
        AtomicInteger writeSideEffects = new AtomicInteger();
        var failures = java.util.List.<DataIntegrationReadinessProbe>of(
                () -> { probeCalls.incrementAndGet(); throw new DataIntegrationReadinessStageException(null); },
                () -> { probeCalls.incrementAndGet(); throw new IllegalStateException(
                        "CANARY jdbc:mysql://user:password@host/db SQLState=999 stack"); });

        for (var probe : failures) {
            var response = new DataIntegrationReadinessController(
                    new DataIntegrationReadinessService(probe)).read(trusted());
            assertThat(response.getStatusCode().value()).isEqualTo(503);
            assertThat(response.getBody()).isEqualTo(new DataIntegrationReadinessController.Failure(
                    "UNAVAILABLE", "DATA_INTEGRATION_READINESS_UNAVAILABLE", "SAFE_RETRY_MANUAL", "INTERNAL_SAFE"));
            assertThat(response.getBody().toString()).doesNotContain(
                    "CANARY", "jdbc:mysql", "user", "password", "SQLState", "stack");
            assertThat(response.getHeaders().keySet())
                    .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("readiness-stage"));
        }
        var nullStage = new DataIntegrationReadinessStageException(null);
        assertThat(nullStage.getMessage()).isNull();
        assertThat(nullStage.getCause()).isNull();
        assertThat(nullStage.getStackTrace()).isEmpty();
        assertThat(probeCalls).hasValue(2);
        assertThat(writeSideEffects).hasValue(0);
    }

    @Test void successful_response_has_no_stage_code_field() {
        var response = new DataIntegrationReadinessController(
                new DataIntegrationReadinessService(() -> snapshot(true))).read(trusted());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(Arrays.stream(response.getBody().getClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)).doesNotContain("stageCode");
    }

    @Test void super_admin_receives_safe_summary_without_sensitive_details() {
        DataIntegrationReadinessProbe probe = () -> snapshot(true);
        var controller = new DataIntegrationReadinessController(new DataIntegrationReadinessService(probe));
        MockHttpServletRequest request = trusted();

        var response = controller.read(request);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isInstanceOf(DataIntegrationReadinessService.Response.class);
        String rendered = response.getBody().toString();
        assertThat(rendered).doesNotContain("jdbc:mysql", "password", "username", "C:\\", "/tmp/");
    }

    @Test void missing_role_query_body_and_probe_failure_fail_closed() {
        DataIntegrationReadinessService service = new DataIntegrationReadinessService(() -> snapshot(false));
        var controller = new DataIntegrationReadinessController(service);
        MockHttpServletRequest missingRole = new MockHttpServletRequest("GET", "/admin-read/v1/data-integration/readiness");
        assertThat(controller.read(missingRole).getStatusCode().value()).isEqualTo(403);

        MockHttpServletRequest query = trusted(); query.addParameter("unexpected", "1");
        assertThat(controller.read(query).getStatusCode().value()).isEqualTo(400);
        MockHttpServletRequest body = trusted(); body.setContent("{}".getBytes());
        assertThat(controller.read(body).getStatusCode().value()).isEqualTo(400);

        var failed = new DataIntegrationReadinessController(
                new DataIntegrationReadinessService(() -> { throw new IllegalStateException("secret/path/detail"); }));
        var unavailable = failed.read(trusted());
        assertThat(unavailable.getStatusCode().value()).isEqualTo(503);
        assertThat(unavailable.getBody().toString()).doesNotContain("secret", "path", "detail");
    }

    @Test void readiness_requires_identity_grants_flyway_unique_migrations_and_backup() {
        var ready = new DataIntegrationReadinessService(() -> snapshot(true)).read();
        var notReady = new DataIntegrationReadinessService(() -> snapshot(false)).read();
        assertThat(ready.status()).isEqualTo("READY");
        assertThat(notReady.status()).isEqualTo("NOT_READY");
    }

    @Test void chunked_get_is_rejected_before_probe_without_reading_body() {
        AtomicInteger probeCalls = new AtomicInteger();
        var controller = countingController(probeCalls);
        MockHttpServletRequest request = trusted();
        request.addHeader("Transfer-Encoding", "chunked");
        request.setContent("sensitive-body-must-not-be-read".getBytes());

        var response = controller.read(request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(probeCalls).hasValue(0);
    }

    @Test void unknown_length_readable_get_body_is_rejected_before_probe() {
        AtomicInteger probeCalls = new AtomicInteger();
        var controller = countingController(probeCalls);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-read/v1/data-integration/readiness") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setAttribute(AdminSessionFilter.TRUSTED_ROLE, "SUPER_ADMIN");
        request.setContent("sensitive-body-must-not-be-read".getBytes());

        var response = controller.read(request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(probeCalls).hasValue(0);
    }

    @Test void source_contract_uses_only_fixed_read_queries_and_never_returns_account_details() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/huarenzaimeng/api/JdbcDataIntegrationReadinessProbe.java"));
        assertThat(source).contains("connection.setReadOnly(true)", "SET TRANSACTION READ ONLY",
                "SHOW GRANTS FOR CURRENT_USER()", "flyway_schema_history", "information_schema.tables",
                "information_schema.columns", "information_schema.statistics", "information_schema.key_column_usage");
        assertThat(source).doesNotContain("executeUpdate(", "INSERT INTO", "UPDATE ", "DELETE FROM", "DROP ",
                "TRUNCATE ", "flyway repair", "flyway clean");
        Set<String> responseFields = Set.of(DataIntegrationReadinessService.Response.class.getRecordComponents())
                .stream().map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertThat(responseFields).doesNotContain("username", "jdbcUrl", "password", "path");
    }

    @Test void schema_queries_encode_nullable_mysql_metadata_without_collisions_and_are_read_only() throws Exception {
        List<String> queries = new ArrayList<>();
        AtomicInteger writes = new AtomicInteger();
        Connection connection = schemaConnection(queries, writes, false);

        var method = JdbcDataIntegrationReadinessProbe.class.getDeclaredMethod("schema", Connection.class);
        method.setAccessible(true);
        var summary = (DataIntegrationReadinessProbe.SchemaSummary) method.invoke(null, connection);

        assertThat(queries).hasSize(4);
        assertThat(queries.get(0)).contains(
                "CASE WHEN engine IS NULL THEN 'N' ELSE CONCAT('V',CHAR_LENGTH(engine),':',engine) END",
                "CASE WHEN table_collation IS NULL THEN 'N' ELSE CONCAT('V',CHAR_LENGTH(table_collation),':',table_collation) END");
        assertThat(queries.get(1)).contains(
                "CASE WHEN column_default IS NULL THEN 'N' ELSE CONCAT('V',CHAR_LENGTH(column_default),':',column_default) END",
                "CASE WHEN collation_name IS NULL THEN 'N' ELSE CONCAT('V',CHAR_LENGTH(collation_name),':',collation_name) END");
        assertThat(queries.get(1)).doesNotContain("COALESCE(column_default,'<NULL>')");
        assertThat(summary.tableCount()).isEqualTo(1);
        assertThat(summary.columnCount()).isEqualTo(2);
        assertThat(summary.indexCount()).isEqualTo(1);
        assertThat(summary.foreignKeyCount()).isEqualTo(1);
        assertThat(writes).hasValue(0);
    }

    @Test void unexpected_jdbc_null_from_any_schema_query_fails_with_fixed_safe_cause() throws Exception {
        List<String> queries = new ArrayList<>();
        AtomicInteger writes = new AtomicInteger();
        Connection connection = schemaConnection(queries, writes, true);
        var method = JdbcDataIntegrationReadinessProbe.class.getDeclaredMethod("schema", Connection.class);
        method.setAccessible(true);

        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> method.invoke(null, connection));

        assertThat(failure).isInstanceOf(java.lang.reflect.InvocationTargetException.class);
        assertThat(failure.getCause()).isInstanceOf(SQLException.class)
                .hasMessage("DATA_INTEGRATION_SCHEMA_NULL_ROW");
        assertThat(queries).hasSize(1);
        assertThat(writes).hasValue(0);
    }

    @Test void exact_application_and_flyway_grants_classify_with_conserved_safe_counts() throws Exception {
        var application = classifyGrants("APPLICATION", Set.of("SELECT", "INSERT", "UPDATE", "DELETE"), List.of(
                "GRANT USAGE ON *.* TO `CANARY_APP`@`CANARY_HOST`",
                "GRANT SELECT, INSERT ON `huarenzaimeng_it_vnext`.* TO `CANARY_APP`@`CANARY_HOST`",
                "GRANT UPDATE, DELETE ON `huarenzaimeng_it_vnext`.* TO `CANARY_APP`@`CANARY_HOST`"));
        var flyway = classifyGrants("FLYWAY", Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "INDEX", "REFERENCES"), List.of(
                "GRANT USAGE ON *.* TO `CANARY_FLYWAY`@`CANARY_HOST`",
                "GRANT SELECT, INSERT, UPDATE, DELETE ON `huarenzaimeng_it_vnext`.* TO `CANARY_FLYWAY`@`CANARY_HOST`",
                "GRANT CREATE, ALTER, INDEX, REFERENCES ON `huarenzaimeng_it_vnext`.* TO `CANARY_FLYWAY`@`CANARY_HOST`"));

        assertMatched(application, 4);
        assertMatched(flyway, 8);
        assertThat(application.toString() + flyway).doesNotContain("CANARY", "huarenzaimeng_it_vnext", "SELECT");
    }

    @Test void missing_usage_duplicate_extra_and_empty_allowlist_fail_closed_with_independent_semantics() throws Exception {
        Set<String> required = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");
        var missingUsage = classifyGrants("APPLICATION", required, List.of(
                "GRANT SELECT, INSERT, UPDATE, DELETE ON `huarenzaimeng_it_vnext`.* TO `u`@`h`"));
        assertThat(missingUsage.usageCount()).isZero();
        assertThat(missingUsage.usageExact()).isFalse();
        assertThat(missingUsage.parserCompatible()).isTrue();
        assertThat(missingUsage.permissionAdjustmentRequired()).isTrue();
        assertThat(missingUsage.grantBoundarySatisfied()).isFalse();

        var duplicateUsage = classifyGrants("APPLICATION", required, List.of(
                "GRANT USAGE ON *.* TO `u`@`h`", "GRANT USAGE ON *.* TO `u`@`h`",
                "GRANT SELECT, INSERT, UPDATE, DELETE ON `huarenzaimeng_it_vnext`.* TO `u`@`h`"));
        assertThat(duplicateUsage.usageCount()).isEqualTo(2);
        assertThat(duplicateUsage.usageExact()).isFalse();
        assertThat(duplicateUsage.unknownCount()).isEqualTo(1);
        assertThat(duplicateUsage.parserCompatible()).isTrue();
        assertThat(duplicateUsage.grantBoundarySatisfied()).isFalse();

        var duplicateRequired = classifyGrants("APPLICATION", required, List.of(
                "GRANT USAGE ON *.* TO `u`@`h`",
                "GRANT SELECT, INSERT, UPDATE, DELETE ON `huarenzaimeng_it_vnext`.* TO `u`@`h`",
                "GRANT SELECT ON `huarenzaimeng_it_vnext`.* TO `u`@`h`"));
        assertThat(duplicateRequired.expectedRequiredCount()).isEqualTo(4);
        assertThat(duplicateRequired.expectedRequiredComplete()).isTrue();
        assertThat(duplicateRequired.expectedRequiredExact()).isFalse();
        assertThat(duplicateRequired.unknownCount()).isEqualTo(1);
        assertThat(duplicateRequired.unknownAbsent()).isFalse();
        assertThat(duplicateRequired.grantBoundarySatisfied()).isFalse();

        var extra = classifyGrants("APPLICATION", required, List.of(
                "GRANT USAGE ON *.* TO `u`@`h`",
                "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON `huarenzaimeng_it_vnext`.* TO `u`@`h`"));
        assertThat(extra.platformAdditionalCount()).isZero();
        assertThat(extra.platformAdditionalApprovedOnly()).isTrue();
        assertThat(extra.unknownCount()).isEqualTo(1);
        assertThat(extra.permissionAdjustmentRequired()).isTrue();
        assertThat(extra.grantBoundarySatisfied()).isFalse();
        assertThat(extra.toString()).doesNotContain("CREATE", "huarenzaimeng_it_vnext");
    }

    @Test void parser_incompatibility_is_unknown_without_permission_adjustment_advice() throws Exception {
        Set<String> required = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");
        for (var grants : List.of(
                List.of("GRANT CANARY_ROLE TO `CANARY_USER`@`CANARY_HOST`"),
                List.of("GRANT USAGE ON *.* TO `u`@`h` WITH GRANT OPTION"),
                java.util.stream.IntStream.range(0, 17).mapToObj(i -> "GRANT CANARY_" + i + " TO `u`@`h`").toList())) {
            var result = classifyGrants("APPLICATION", required, grants);
            assertThat(result.unknownCount()).isPositive();
            assertThat(result.unknownAbsent()).isFalse();
            assertThat(result.parserCompatible()).isFalse();
            assertThat(result.permissionAdjustmentRequired()).isFalse();
            assertThat(result.grantBoundarySatisfied()).isFalse();
            assertThat(result.toString()).doesNotContain("CANARY", "GRANT OPTION");
        }
    }

    @Test void public_grant_response_is_isomorphic_fixed_counts_and_booleans_only() {
        var response = new DataIntegrationReadinessService(() -> snapshot(true)).read();
        var fields = Arrays.stream(response.applicationGrant().getClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(fields).containsExactly("usageCount", "usageExact", "expectedRequiredCount",
                "expectedRequiredComplete", "expectedRequiredExact", "platformAdditionalCount",
                "platformAdditionalApprovedOnly", "unknownCount", "unknownAbsent", "parserCompatible",
                "permissionAdjustmentRequired", "grantBoundarySatisfied");
        assertThat(response.flywayGrant().getClass()).isEqualTo(response.applicationGrant().getClass());
        assertThat(fields).noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("granttext")
                || name.toLowerCase(java.util.Locale.ROOT).contains("canonical")
                || name.toLowerCase(java.util.Locale.ROOT).contains("scope")
                || name.toLowerCase(java.util.Locale.ROOT).contains("account")
                || name.toLowerCase(java.util.Locale.ROOT).contains("host"));
    }

    @Test void fixed_manifest_is_ordinal_and_matches_every_source_file() throws Exception {
        var repoRoot = resolveRepositoryRoot(java.nio.file.Path.of("").toAbsolutePath());
        var moduleRoot = repoRoot.resolve("apps/api");
        assertThat(resolveRepositoryRoot(repoRoot)).isEqualTo(repoRoot);
        assertThat(resolveRepositoryRoot(moduleRoot)).isEqualTo(repoRoot);

        var manifest = moduleRoot.resolve("manifests/DATA-INTEGRATION-01-readonly-challenge.txt");
        var lines = java.nio.file.Files.readAllLines(manifest, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(lines).hasSize(6).isSorted();
        for (String line : lines) {
            String[] fields = line.split("\\|", -1);
            assertThat(fields).hasSize(2);
            byte[] bytes = java.nio.file.Files.readAllBytes(repoRoot.resolve(fields[0]).normalize());
            String actual = HexFormat.of().withUpperCase().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            assertThat(actual).isEqualTo(fields[1]);
        }
    }

    @Test void repository_root_resolution_rejects_zero_candidates() throws Exception {
        var fixture = java.nio.file.Files.createTempDirectory("data-root-zero-");
        try {
            assertThat(org.assertj.core.api.Assertions.catchThrowable(
                    () -> resolveRepositoryRoot(fixture)))
                    .isInstanceOf(IOException.class)
                    .hasMessage("PROJECT_ROOT_RESOLUTION_FAILED");
        } finally {
            deleteFixture(fixture);
        }
        assertThat(java.nio.file.Files.exists(fixture)).isFalse();
    }

    @Test void repository_root_resolution_rejects_nested_multiple_candidates() throws Exception {
        var fixture = java.nio.file.Files.createTempDirectory("data-root-multiple-");
        try {
            var outer = fixture.resolve("outer");
            var nested = outer.resolve("apps/api/nested");
            java.nio.file.Files.createDirectories(outer.resolve("apps/api"));
            java.nio.file.Files.createFile(outer.resolve("pom.xml"));
            java.nio.file.Files.createFile(outer.resolve("apps/api/pom.xml"));
            java.nio.file.Files.createDirectories(nested.resolve("apps/api"));
            java.nio.file.Files.createFile(nested.resolve("pom.xml"));
            java.nio.file.Files.createFile(nested.resolve("apps/api/pom.xml"));

            assertThat(org.assertj.core.api.Assertions.catchThrowable(
                    () -> resolveRepositoryRoot(nested.resolve("apps/api"))))
                    .isInstanceOf(IOException.class)
                    .hasMessage("PROJECT_ROOT_RESOLUTION_FAILED");
        } finally {
            deleteFixture(fixture);
        }
        assertThat(java.nio.file.Files.exists(fixture)).isFalse();
    }

    private static java.nio.file.Path resolveRepositoryRoot(java.nio.file.Path start) throws IOException {
        var candidates = new java.util.LinkedHashSet<java.nio.file.Path>();
        for (var current = start.toRealPath(); current != null; current = current.getParent()) {
            if (java.nio.file.Files.isRegularFile(current.resolve("pom.xml"))
                    && java.nio.file.Files.isRegularFile(current.resolve("apps/api/pom.xml"))) {
                candidates.add(current);
            }
            if (current.endsWith(java.nio.file.Path.of("apps", "api"))) {
                var parent = current.getParent();
                var repository = parent == null ? null : parent.getParent();
                if (repository != null
                        && java.nio.file.Files.isRegularFile(current.resolve("pom.xml"))
                        && java.nio.file.Files.isRegularFile(repository.resolve("pom.xml"))) {
                    candidates.add(repository);
                }
            }
        }
        if (candidates.size() != 1) {
            throw new IOException("PROJECT_ROOT_RESOLUTION_FAILED");
        }
        return candidates.iterator().next();
    }

    private static void deleteFixture(java.nio.file.Path root) throws IOException {
        try (var paths = java.nio.file.Files.walk(root)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                java.nio.file.Files.delete(path);
            }
        }
    }

    private static Connection schemaConnection(List<String> queries, AtomicInteger writes,
                                               boolean unexpectedNull) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("createStatement")) {
                        return schemaStatement(queries, writes, unexpectedNull);
                    }
                    if (method.getName().equals("close")) return null;
                    if (method.getName().equals("isClosed")) return false;
                    return defaultValue(method.getReturnType());
                });
    }

    private static DataIntegrationReadinessProbe.GrantSummary classifyGrants(
            String roleCode, Set<String> required, List<String> rows) throws Exception {
        AtomicInteger writes = new AtomicInteger();
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("createStatement")) return grantStatement(rows, writes);
                    return defaultValue(method.getReturnType());
                });
        var method = JdbcDataIntegrationReadinessProbe.class.getDeclaredMethod(
                "grants", Connection.class, String.class, Set.class);
        method.setAccessible(true);
        var result = (DataIntegrationReadinessProbe.GrantSummary) method.invoke(null, connection, roleCode, required);
        assertThat(writes).hasValue(0);
        return result;
    }

    private static Statement grantStatement(List<String> rows, AtomicInteger writes) {
        return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class<?>[] {Statement.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("executeQuery")) {
                        assertThat(arguments[0]).isEqualTo("SHOW GRANTS FOR CURRENT_USER()");
                        return schemaResult(rows);
                    }
                    if (method.getName().equals("execute") || method.getName().equals("executeUpdate")) {
                        writes.incrementAndGet();
                        throw new AssertionError("GRANT_CLASSIFICATION_WRITE_NOT_ALLOWED");
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static void assertMatched(DataIntegrationReadinessProbe.GrantSummary value, int requiredCount) {
        assertThat(value.usageCount()).isEqualTo(1);
        assertThat(value.usageExact()).isTrue();
        assertThat(value.expectedRequiredCount()).isEqualTo(requiredCount);
        assertThat(value.expectedRequiredComplete()).isTrue();
        assertThat(value.expectedRequiredExact()).isTrue();
        assertThat(value.platformAdditionalCount()).isZero();
        assertThat(value.platformAdditionalApprovedOnly()).isTrue();
        assertThat(value.unknownCount()).isZero();
        assertThat(value.unknownAbsent()).isTrue();
        assertThat(value.parserCompatible()).isTrue();
        assertThat(value.permissionAdjustmentRequired()).isFalse();
        assertThat(value.grantBoundarySatisfied()).isTrue();
    }

    private static Statement schemaStatement(List<String> queries, AtomicInteger writes,
                                             boolean unexpectedNull) {
        return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class<?>[] {Statement.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("executeQuery")) {
                        String sql = (String) arguments[0];
                        queries.add(sql);
                        if (sql.contains("information_schema.tables")) {
                            return schemaResult(unexpectedNull ? java.util.Collections.singletonList(null)
                                    : List.of("T|synthetic_view|N|N"));
                        }
                        if (sql.contains("information_schema.columns")) {
                            return schemaResult(List.of(
                                    "C|synthetic_table|1|amount|int|NO|N||N",
                                    "C|synthetic_table|2|created_at|timestamp|NO|V0:||N"));
                        }
                        if (sql.contains("information_schema.statistics")) {
                            return schemaResult(List.of("I|synthetic_table|PRIMARY|0|1|id"));
                        }
                        if (sql.contains("information_schema.key_column_usage")) {
                            return schemaResult(List.of("F|child|fk_parent|1|parent_id|parent|id|RESTRICT|RESTRICT"));
                        }
                        throw new AssertionError("UNEXPECTED_SCHEMA_QUERY");
                    }
                    if (method.getName().equals("execute") || method.getName().equals("executeUpdate")) {
                        writes.incrementAndGet();
                        throw new AssertionError("SCHEMA_WRITE_NOT_ALLOWED");
                    }
                    if (method.getName().equals("close")) return null;
                    return defaultValue(method.getReturnType());
                });
    }

    private static ResultSet schemaResult(List<String> rows) {
        AtomicInteger cursor = new AtomicInteger(-1);
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) return cursor.incrementAndGet() < rows.size();
                    if (method.getName().equals("getString")) return rows.get(cursor.get());
                    if (method.getName().equals("close")) return null;
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static MockHttpServletRequest trusted() {
        MockHttpServletRequest request = new BodylessMockRequest();
        request.setAttribute(AdminSessionFilter.TRUSTED_ROLE, "SUPER_ADMIN");
        return request;
    }

    private static final class BodylessMockRequest extends MockHttpServletRequest {
        private BodylessMockRequest() { super("GET", "/admin-read/v1/data-integration/readiness"); }
        @Override public ServletInputStream getInputStream() {
            return new ServletInputStream() {
                @Override public boolean isFinished() { return true; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
                @Override public int read() throws IOException { return -1; }
            };
        }
    }

    private static DataIntegrationReadinessController countingController(AtomicInteger probeCalls) {
        return new DataIntegrationReadinessController(new DataIntegrationReadinessService(() -> {
            probeCalls.incrementAndGet();
            return snapshot(true);
        }));
    }

    private static DataIntegrationReadinessProbe.Snapshot snapshot(boolean ready) {
        String status = ready ? "MATCHED" : "MISMATCH";
        var app = ready
                ? new DataIntegrationReadinessProbe.GrantSummary(1, true, 4, true, true, 0, true, 0, true, true, false, true)
                : new DataIntegrationReadinessProbe.GrantSummary(0, false, 0, false, false, 0, true, 0, true, true, true, false);
        var flywayGrant = ready
                ? new DataIntegrationReadinessProbe.GrantSummary(1, true, 8, true, true, 0, true, 0, true, true, false, true)
                : new DataIntegrationReadinessProbe.GrantSummary(0, false, 0, false, false, 0, true, 0, true, true, true, false);
        var flyway = new DataIntegrationReadinessProbe.FlywaySummary(status, 11, "11", ready, "C".repeat(64));
        var schema = new DataIntegrationReadinessProbe.SchemaSummary(20, 100, 40, 8, "D".repeat(64));
        var discovery = new DataIntegrationReadinessProbe.MigrationDiscovery(
                ready ? "UNIQUE_V1_TO_V11" : "MIGRATION_DISCOVERY_MISMATCH", 11, "11", "E".repeat(64));
        var backup = new DataIntegrationReadinessProbe.BackupSummary(ready ? "PRESENT" : "ABSENT",
                ready ? "PRESENT" : "ABSENT");
        return new DataIntegrationReadinessProbe.Snapshot("ARTIFACT_SHA256:" + "F".repeat(64),
                "huarenzaimeng_it_vnext|TENCENT64.site|*|server-uuid", ready ? "MYSQL_5_7_MATCHED" : "DATABASE_IDENTITY_OR_VERSION_MISMATCH",
                app, flywayGrant, flyway, schema, discovery, backup, ready);
    }
}
