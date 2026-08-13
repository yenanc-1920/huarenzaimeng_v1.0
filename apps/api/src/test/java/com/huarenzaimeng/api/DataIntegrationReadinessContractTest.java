package com.huarenzaimeng.api;

import com.huarenzaimeng.api.adminauth.AdminSessionFilter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;
import java.util.HexFormat;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DataIntegrationReadinessContractTest {
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

    @Test void fixed_manifest_is_ordinal_and_matches_every_source_file() throws Exception {
        var manifest = java.nio.file.Path.of("manifests/DATA-INTEGRATION-01-readonly-challenge.txt");
        var lines = java.nio.file.Files.readAllLines(manifest, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(lines).hasSize(5).isSorted();
        for (String line : lines) {
            String[] fields = line.split("\\|", -1);
            assertThat(fields).hasSize(2);
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../..", fields[0]).normalize());
            String actual = HexFormat.of().withUpperCase().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            assertThat(actual).isEqualTo(fields[1]);
        }
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
        var app = new DataIntegrationReadinessProbe.GrantSummary("APPLICATION", status, "A".repeat(64), 2, ready, ready);
        var flywayGrant = new DataIntegrationReadinessProbe.GrantSummary("FLYWAY", status, "B".repeat(64), 2, ready, ready);
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
