package com.huarenzaimeng.api.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

final class ReleaseMigrationRuntimeIdentityProvider {
    private static final Path ROOT = Path.of(System.getProperty("os.name", "").startsWith("Windows")
            ? "E:\\huarenzaimeng-controlled\\data-migration" : "/var/lib/huarenzaimeng-controlled/data-migration");
    private final DataSource applicationDataSource;
    private final Flyway flyway;

    ReleaseMigrationRuntimeIdentityProvider(DataSource applicationDataSource, Flyway flyway) {
        this.applicationDataSource = applicationDataSource;
        this.flyway = flyway;
    }

    public ReleaseMigrationAuthorization.ExecutionIdentity current() throws Exception {
        Path artifact = Path.of(ReleaseMigrationLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!artifact.isAbsolute() || !Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)) fail();
        String artifactSha = sha(Files.readAllBytes(artifact));
        String candidateManifest = fixedEvidenceSha("candidate-manifest.txt");
        String oracleManifest = fixedEvidenceSha("oracle-manifest.txt");
        String backup = fixedEvidenceSha("backup.evidence");
        String restore = fixedEvidenceSha("restore.evidence");

        String database;
        String uuid;
        String flywayGrants;
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT DATABASE(), @@server_uuid")) {
                if (!result.next()) fail();
                database = result.getString(1); uuid = result.getString(2);
            }
            flywayGrants = grants(connection);
        }
        String applicationGrants;
        try (Connection connection = applicationDataSource.getConnection()) { applicationGrants = grants(connection); }
        String grantSnapshot = sha(("APPLICATION|" + applicationGrants + "\nFLYWAY|" + flywayGrants)
                .getBytes(StandardCharsets.UTF_8));
        return new ReleaseMigrationAuthorization.ExecutionIdentity(artifactSha, candidateManifest, database, uuid, grantSnapshot,
                backup, restore, oracleManifest, migrationInventory());
    }

    private static String grants(Connection connection) throws Exception {
        List<String> rows = new ArrayList<>();
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SHOW GRANTS")) {
            while (result.next()) rows.add(result.getString(1));
        }
        Collections.sort(rows);
        return sha(String.join("\n", rows).getBytes(StandardCharsets.UTF_8));
    }

    private static String migrationInventory() throws Exception {
        List<String> lines = new ArrayList<>();
        for (int version = 1; version <= 12; version++) {
            String prefix = "db/migration/V" + version + "__";
            var resources = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                    .getResources("classpath*:" + prefix + "*");
            if (resources.length != 1) fail();
            try (InputStream input = resources[0].getInputStream()) {
                lines.add("V" + version + "|" + sha(input.readAllBytes()));
            }
        }
        return sha(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private static String fixedEvidenceSha(String name) throws Exception {
        Path path = ROOT.resolve(name).normalize();
        if (!path.getParent().equals(ROOT) || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) fail();
        return sha(Files.readAllBytes(path));
    }

    private static String sha(byte[] value) throws Exception { return ReleaseMigrationAuthorizationStore.sha256(value); }
    private static void fail() { throw new IllegalStateException("MIGRATION_RUNTIME_IDENTITY_INVALID"); }
}
