package com.huarenzaimeng.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "hz.data-integration.readiness-enabled", havingValue = "true")
final class JdbcDataIntegrationReadinessProbe implements DataIntegrationReadinessProbe {
    private static final String EXPECTED_DATABASE = "huarenzaimeng_it_vnext";
    private static final Pattern SHA256 = Pattern.compile("[A-F0-9]{64}");
    private static final Pattern MIGRATION = Pattern.compile("(?:^|/)V(\\d+)__[^/]+\\.(?:sql|class)$");
    private static final Pattern GRANT = Pattern.compile(
            "^GRANT (.+) ON (.+) TO (?:`(?:``|[^`])*`|'(?:''|[^'])*')@(?:`(?:``|[^`])*`|'(?:''|[^'])*')$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GRANT_OPTION = Pattern.compile("\\s+WITH\\s+GRANT\\s+OPTION\\s*$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_GRANT_ROWS = 16;
    private static final Set<String> APP_REQUIRED = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");
    private static final Set<String> FLYWAY_REQUIRED = Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "INDEX", "REFERENCES");

    private final DataSource applicationDataSource;
    private final Environment environment;

    JdbcDataIntegrationReadinessProbe(DataSource applicationDataSource, Environment environment) {
        this.applicationDataSource = applicationDataSource;
        this.environment = environment;
    }

    @Override public Snapshot inspect() {
        Connection application = at(DataIntegrationReadinessStageException.Stage.APP_CONNECT,
                applicationDataSource::getConnection);
        try (application) {
            Connection flyway = at(DataIntegrationReadinessStageException.Stage.FLYWAY_CONNECT,
                    () -> flywayDataSource().getConnection());
            try (flyway) {
            runAt(DataIntegrationReadinessStageException.Stage.READ_ONLY_SETUP_APP,
                    () -> prepareReadOnly(application));
            runAt(DataIntegrationReadinessStageException.Stage.READ_ONLY_SETUP_FLYWAY,
                    () -> prepareReadOnly(flyway));
            try {
                String[] identity = at(DataIntegrationReadinessStageException.Stage.DB_IDENTITY, () -> new String[] {
                        scalar(application, "SELECT CONCAT(DATABASE(),'|',@@hostname,'|*|',@@server_uuid)"),
                        scalar(application, "SELECT @@version")
                });
                String databaseIdentity = identity[0];
                String version = identity[1];
                boolean identityMatched = databaseIdentity != null
                        && databaseIdentity.startsWith(EXPECTED_DATABASE + "|")
                        && databaseIdentity.split("\\|", -1).length == 4;
                String engineStatus = identityMatched && version != null && version.startsWith("5.7.")
                        ? "MYSQL_5_7_MATCHED" : "DATABASE_IDENTITY_OR_VERSION_MISMATCH";

                GrantClassification applicationClassification = at(DataIntegrationReadinessStageException.Stage.APP_GRANTS,
                        () -> grants(application, "APPLICATION", APP_REQUIRED));
                GrantClassification flywayClassification = at(DataIntegrationReadinessStageException.Stage.FLYWAY_GRANTS,
                        () -> grants(flyway, "FLYWAY", FLYWAY_REQUIRED));
                GrantSummary applicationGrant = applicationClassification.summary();
                GrantSummary flywayGrant = flywayClassification.summary();
                FlywaySummary flywaySummary = at(DataIntegrationReadinessStageException.Stage.FLYWAY_HISTORY,
                        () -> flyway(application));
                SchemaSummary schemaSummary = at(DataIntegrationReadinessStageException.Stage.SCHEMA,
                        () -> schema(application));
                MigrationDiscovery discovery = at(DataIntegrationReadinessStageException.Stage.MIGRATION_DISCOVERY,
                        this::discoverMigrations);
                DataMigrationOracleVerifier.State oracleState = at(DataIntegrationReadinessStageException.Stage.SCHEMA,
                        () -> DataMigrationOracleVerifier.verify(application, EXPECTED_DATABASE,
                                required("hz.data-integration.expected-server-uuid")));
                MigrationOracleSummary oracle = new MigrationOracleSummary(oracleState.name(),
                        oracleState == DataMigrationOracleVerifier.State.POST_V12);
                BackupSummary backup = at(DataIntegrationReadinessStageException.Stage.BACKUP_STATUS, this::backup);
                boolean ready = "MYSQL_5_7_MATCHED".equals(engineStatus)
                        && applicationGrant.grantBoundarySatisfied()
                        && flywayGrant.grantBoundarySatisfied()
                        && "MATCHED".equals(flywaySummary.status())
                        && "UNIQUE_V1_TO_V12".equals(discovery.status())
                        && oracle.terminalMatched()
                        && "PRESENT".equals(backup.referenceStatus())
                        && "PRESENT".equals(backup.restoreEvidenceStatus());
                return new Snapshot(runtimeIdentity(), databaseIdentity, engineStatus,
                        applicationGrant, flywayGrant, applicationClassification.breakdown(),
                        flywayClassification.breakdown(), flywaySummary, schemaSummary, discovery, oracle, backup, ready);
            } finally {
                safeRollback(application);
                safeRollback(flyway);
            }
            }
        } catch (DataIntegrationReadinessStageException safe) {
            throw safe;
        } catch (RuntimeException | SQLException unavailable) {
            throw new DataIntegrationReadinessStageException(
                    DataIntegrationReadinessStageException.Stage.INTERNAL_SAFE);
        }
    }

    private static <T> T at(DataIntegrationReadinessStageException.Stage stage,
                            CheckedSupplier<T> operation) {
        try { return operation.get(); }
        catch (DataIntegrationReadinessStageException safe) { throw safe; }
        catch (Exception unavailable) { throw new DataIntegrationReadinessStageException(stage); }
    }

    private static void runAt(DataIntegrationReadinessStageException.Stage stage,
                              CheckedRunnable operation) {
        at(stage, () -> { operation.run(); return null; });
    }

    @FunctionalInterface private interface CheckedSupplier<T> { T get() throws Exception; }
    @FunctionalInterface private interface CheckedRunnable { void run() throws Exception; }

    private DataSource flywayDataSource() {
        String url = required("spring.flyway.url");
        String user = required("spring.flyway.user");
        String password = required("spring.flyway.password");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        return dataSource;
    }

    private String required(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("DATA_INTEGRATION_CONFIGURATION_MISSING");
        return value;
    }

    private static void prepareReadOnly(Connection connection) throws SQLException {
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TRANSACTION READ ONLY");
        }
    }

    private static GrantClassification grants(Connection connection, String principalClass,
                                        Set<String> required) throws SQLException {
        List<String> raw = rows(connection, "SHOW GRANTS FOR CURRENT_USER()");
        if (raw.size() > MAX_GRANT_ROWS) {
            return new GrantClassification(
                    new GrantSummary(0, false, 0, false, false, 0, true,
                            1, false, false, false, false),
                    new GrantUnknownBreakdown(0, 0, 0, 0, 1, 0));
        }
        int usage = 0;
        int unknown = 0;
        boolean parserCompatible = true;
        boolean confirmedUnapproved = false;
        boolean expectedRequiredDuplicate = false;
        int unexpectedPrivilege = 0;
        int unexpectedScope = 0;
        int duplicateRequired = 0;
        int duplicateUsage = 0;
        int malformed = 0;
        int otherUnknown = 0;
        Set<String> expectedFound = new HashSet<>();
        Set<String> seenAtoms = new HashSet<>();
        for (String value : raw) {
            if (GRANT_OPTION.matcher(value).find()) {
                unknown++; malformed++; parserCompatible = false; continue;
            }
            Matcher matcher = GRANT.matcher(value.trim());
            if (!matcher.matches()) { unknown++; malformed++; parserCompatible = false; continue; }
            String scope = matcher.group(2).replace("`", "").toUpperCase(Locale.ROOT);
            TreeSet<String> privileges = new TreeSet<>();
            for (String privilege : matcher.group(1).split(",")) privileges.add(privilege.trim().toUpperCase(Locale.ROOT));
            if (privileges.isEmpty()) { unknown++; malformed++; parserCompatible = false; continue; }
            for (String privilege : privileges) {
                String atom = scope + "|" + privilege;
                boolean usageAtom = "*.*".equals(scope) && "USAGE".equals(privilege);
                if (usageAtom) usage++;
                if (!seenAtoms.add(atom)) {
                    if ((EXPECTED_DATABASE.toUpperCase(Locale.ROOT) + ".*").equals(scope)
                            && required.contains(privilege)) {
                        expectedRequiredDuplicate = true;
                        duplicateRequired++;
                    } else if (usageAtom) {
                        duplicateUsage++;
                    } else {
                        otherUnknown++;
                    }
                    unknown++;
                    continue;
                }
                if (!usageAtom && (EXPECTED_DATABASE.toUpperCase(Locale.ROOT) + ".*").equals(scope)
                        && required.contains(privilege)) {
                    expectedFound.add(privilege);
                } else if (!usageAtom) {
                    unknown++;
                    confirmedUnapproved = true;
                    if (!(EXPECTED_DATABASE.toUpperCase(Locale.ROOT) + ".*").equals(scope)) {
                        unexpectedScope++;
                    } else {
                        unexpectedPrivilege++;
                    }
                }
            }
        }
        boolean usageExact = usage == 1;
        boolean requiredComplete = expectedFound.size() == required.size();
        boolean requiredExact = requiredComplete && !confirmedUnapproved && !expectedRequiredDuplicate;
        int platformAdditionalCount = 0;
        boolean platformApprovedOnly = true;
        boolean unknownAbsent = unknown == 0;
        GrantUnknownBreakdown breakdown = new GrantUnknownBreakdown(unexpectedPrivilege, unexpectedScope,
                duplicateRequired, duplicateUsage, malformed, otherUnknown);
        boolean classificationMatched = breakdown.total() == unknown;
        boolean adjustment = parserCompatible && classificationMatched
                && (usage == 0 || !requiredComplete || confirmedUnapproved
                || duplicateRequired > 0 || duplicateUsage > 0 || unexpectedScope > 0 || unexpectedPrivilege > 0);
        boolean boundary = usageExact && requiredComplete && requiredExact && platformApprovedOnly
                && unknownAbsent && parserCompatible && classificationMatched;
        GrantSummary summary = new GrantSummary(usage, usageExact, expectedFound.size(), requiredComplete, requiredExact,
                platformAdditionalCount, platformApprovedOnly, unknown, unknownAbsent, parserCompatible,
                adjustment, boundary);
        if (!classificationMatched) {
            summary = new GrantSummary(usage, usageExact, expectedFound.size(), requiredComplete, false,
                    platformAdditionalCount, platformApprovedOnly, unknown, unknownAbsent, false, false, false);
        }
        return new GrantClassification(summary, breakdown);
    }

    private record GrantClassification(GrantSummary summary, GrantUnknownBreakdown breakdown) {}

    private static FlywaySummary flyway(Connection connection) throws SQLException {
        List<String> lines = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT installed_rank,version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")) {
            while (result.next()) lines.add(result.getInt(1) + "|" + result.getString(2) + "|"
                    + result.getString(3) + "|" + result.getString(4) + "|" + result.getString(5)
                    + "|" + result.getString(6) + "|" + result.getBoolean(7));
        }
        String current = lines.isEmpty() ? null : lines.get(lines.size() - 1).split("\\|", -1)[1];
        boolean success = !lines.isEmpty() && lines.stream().allMatch(line -> line.endsWith("|true"));
        String status = success && "12".equals(current) && lines.size() == 12 ? "MATCHED" : "MISMATCH";
        return new FlywaySummary(status, lines.size(), current, success, sha256(String.join("\n", lines)));
    }

    private static SchemaSummary schema(Connection connection) throws SQLException {
        List<String> lines = new ArrayList<>();
        lines.addAll(rows(connection, "SELECT CONCAT('T|',table_name,'|',CASE WHEN engine IS NULL THEN 'N' ELSE CONCAT('V',CHAR_LENGTH(engine),':',engine) END,'|',CASE WHEN table_collation IS NULL THEN 'N' ELSE CONCAT('V',CHAR_LENGTH(table_collation),':',table_collation) END) FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name"));
        int tables = lines.size();
        List<String> columns = rows(connection, "SELECT CONCAT('C|',table_name,'|',ordinal_position,'|',column_name,'|',column_type,'|',is_nullable,'|',CASE WHEN column_default IS NULL THEN 'N' ELSE CONCAT('V',CHAR_LENGTH(column_default),':',column_default) END,'|',extra,'|',CASE WHEN collation_name IS NULL THEN 'N' ELSE CONCAT('V',CHAR_LENGTH(collation_name),':',collation_name) END) FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position");
        List<String> indexes = rows(connection, "SELECT CONCAT('I|',table_name,'|',index_name,'|',non_unique,'|',seq_in_index,'|',column_name) FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index");
        List<String> foreignKeys = rows(connection, "SELECT CONCAT('F|',k.table_name,'|',k.constraint_name,'|',k.ordinal_position,'|',k.column_name,'|',k.referenced_table_name,'|',k.referenced_column_name,'|',r.update_rule,'|',r.delete_rule) FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema AND r.constraint_name=k.constraint_name AND r.table_name=k.table_name WHERE k.table_schema=DATABASE() AND k.referenced_table_name IS NOT NULL ORDER BY k.table_name,k.constraint_name,k.ordinal_position");
        lines.addAll(columns); lines.addAll(indexes); lines.addAll(foreignKeys);
        return new SchemaSummary(tables, columns.size(), indexes.size(), foreignKeys.size(),
                sha256(String.join("\n", lines)));
    }

    private MigrationDiscovery discoverMigrations() {
        TreeSet<String> entries = new TreeSet<>();
        Map<Integer,Integer> versions = new HashMap<>();
        try {
            URI location = JdbcDataIntegrationReadinessProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location);
            if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                try (JarFile jar = new JarFile(path.toFile())) {
                    var enumeration = jar.entries();
                    while (enumeration.hasMoreElements()) addMigration(enumeration.nextElement().getName(), entries, versions);
                }
            } else {
                try (var stream = Files.walk(path)) {
                    stream.filter(Files::isRegularFile).forEach(file -> addMigration(path.relativize(file).toString().replace('\\','/'), entries, versions));
                }
            }
        } catch (Exception exception) {
            return new MigrationDiscovery("DISCOVERY_UNAVAILABLE", 0, null, sha256("DISCOVERY_UNAVAILABLE"));
        }
        boolean exact = versions.size() == 12;
        for (int version = 1; version <= 12; version++) exact &= versions.getOrDefault(version, 0) == 1;
        return new MigrationDiscovery(exact ? "UNIQUE_V1_TO_V12" : "MIGRATION_DISCOVERY_MISMATCH",
                versions.size(), versions.isEmpty() ? null : Integer.toString(versions.keySet().stream().max(Integer::compareTo).orElseThrow()),
                sha256(String.join("\n", entries)));
    }

    private static void addMigration(String name, Set<String> entries, Map<Integer,Integer> versions) {
        if (name.contains("$")) return;
        Matcher matcher = MIGRATION.matcher(name);
        if (matcher.find()) {
            int version = Integer.parseInt(matcher.group(1));
            entries.add(name.substring(name.lastIndexOf('/') + 1));
            versions.merge(version, 1, Integer::sum);
        }
    }

    private BackupSummary backup() {
        String reference = environment.getProperty("hz.data-integration.backup-reference-sha256", "").trim();
        String restore = environment.getProperty("hz.data-integration.restore-evidence-sha256", "").trim();
        return new BackupSummary(SHA256.matcher(reference).matches() ? "PRESENT" : "ABSENT",
                SHA256.matcher(restore).matches() ? "PRESENT" : "ABSENT");
    }

    private static String runtimeIdentity() {
        try { return P021TestReadonlyDiagnosticController.runtimeIdentity(); }
        catch (RuntimeException unavailable) { return "RUNTIME_IDENTITY_UNAVAILABLE"; }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) throw new SQLException("required scalar missing");
            return result.getString(1);
        }
    }

    private static List<String> rows(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                String value = result.getString(1);
                if (value == null) throw new SQLException("DATA_INTEGRATION_SCHEMA_NULL_ROW");
                values.add(value);
            }
        }
        values.sort(Comparator.naturalOrder());
        return values;
    }

    private static void safeRollback(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
