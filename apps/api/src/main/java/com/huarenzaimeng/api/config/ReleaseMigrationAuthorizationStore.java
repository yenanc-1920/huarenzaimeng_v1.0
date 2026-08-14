package com.huarenzaimeng.api.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class ReleaseMigrationAuthorizationStore {
    private static final Set<String> KEYS = Set.of("authorizationRef", "runId", "artifactSha256", "candidateManifestSha256", "databaseName",
            "expectedServerUuid", "grantSnapshotIdentity", "backupEvidenceSha256", "restoreEvidenceSha256",
            "oracleManifestSha256", "migrationInventorySha256", "startState", "allowedTarget", "validFrom", "validUntil");
    private static final Path FIXED_ROOT = Path.of(System.getProperty("os.name", "").startsWith("Windows")
            ? "E:\\huarenzaimeng-controlled\\data-migration" : "/var/lib/huarenzaimeng-controlled/data-migration");
    private final Path root;
    private final Clock clock;
    private final boolean enforceAcl;

    private ReleaseMigrationAuthorizationStore(Path root, Clock clock, boolean enforceAcl) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
        this.enforceAcl = enforceAcl;
    }

    static ReleaseMigrationAuthorizationStore fixed(Clock clock) {
        return new ReleaseMigrationAuthorizationStore(FIXED_ROOT, clock, true);
    }

    static ReleaseMigrationAuthorizationStore forTest(Path root, Clock clock) {
        return new ReleaseMigrationAuthorizationStore(root, clock, false);
    }

    ReleaseMigrationAuthorization preview() throws IOException {
        Path authorization = fixedFile("authorization.properties");
        byte[] payload = readStable(authorization);
        try {
            return parse(payload);
        } finally {
            java.util.Arrays.fill(payload, (byte) 0);
        }
    }

    ConsumedAuthorization consume(ReleaseMigrationAuthorization authorization,
                                  ReleaseMigrationAuthorization.ExecutionIdentity identity) throws IOException {
        validate(authorization, identity);
        Path directory = root.resolve("consumed");
        Files.createDirectories(directory);
        if (enforceAcl) requireOwnerOnly(directory);
        Path marker = directory.resolve(authorization.runId() + ".consumed").normalize();
        if (!marker.getParent().equals(directory)) invalid();
        writeCreateNewAndForce(marker, "CONSUMED\n");
        return new ConsumedAuthorization(root, marker, authorization);
    }

    Path readyFile() { return root.resolve("READY.properties"); }
    byte[] readReady() throws IOException { return readStable(readyFile()); }
    byte[] readConsumption(String runId) throws IOException {
        if (!runId.matches("[A-Z0-9][A-Z0-9_-]{7,95}")) invalid();
        Path path = root.resolve("consumed").resolve(runId + ".consumed").normalize();
        if (!path.getParent().equals(root.resolve("consumed"))) invalid();
        return readStable(path);
    }

    void validate(ReleaseMigrationAuthorization authorization,
                  ReleaseMigrationAuthorization.ExecutionIdentity identity) {
        authorization.requireMatches(identity, clock.instant());
    }

    void validateConsumedIdentity(ReleaseMigrationAuthorization authorization,
                                  ReleaseMigrationAuthorization.ExecutionIdentity identity) {
        authorization.requireIdentityMatches(identity);
    }

    private ReleaseMigrationAuthorization parse(byte[] payload) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : new String(payload, StandardCharsets.UTF_8).split("\\n", -1)) {
            if (line.isEmpty()) continue;
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) invalid();
            String key = line.substring(0, separator);
            if (!KEYS.contains(key) || values.putIfAbsent(key, line.substring(separator + 1)) != null) invalid();
        }
        if (!values.keySet().equals(KEYS)) invalid();
        try {
            return new ReleaseMigrationAuthorization(values.get("authorizationRef"), values.get("runId"),
                    values.get("artifactSha256"), values.get("candidateManifestSha256"), values.get("databaseName"), values.get("expectedServerUuid"),
                    values.get("grantSnapshotIdentity"), values.get("backupEvidenceSha256"),
                    values.get("restoreEvidenceSha256"), values.get("oracleManifestSha256"),
                    values.get("migrationInventorySha256"), ReleaseMigrationAuthorization.StartState.valueOf(values.get("startState")),
                    ReleaseMigrationAuthorization.AllowedTarget.valueOf(values.get("allowedTarget")),
                    Instant.parse(values.get("validFrom")), Instant.parse(values.get("validUntil")));
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("MIGRATION_AUTHORIZATION_INVALID"); }
    }

    private Path fixedFile(String name) throws IOException {
        if (!root.isAbsolute() || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) invalid();
        if (enforceAcl) requireOwnerOnly(root);
        Path path = root.resolve(name).normalize();
        if (!path.getParent().equals(root)) invalid();
        return path;
    }

    private byte[] readStable(Path path) throws IOException {
        BasicFileAttributes before = secureAttributes(path);
        byte[] bytes = Files.readAllBytes(path);
        BasicFileAttributes after = secureAttributes(path);
        if (before.size() != after.size() || before.lastModifiedTime().compareTo(after.lastModifiedTime()) != 0
                || !java.util.Objects.equals(before.fileKey(), after.fileKey()) || bytes.length != after.size()) invalid();
        return bytes;
    }

    private BasicFileAttributes secureAttributes(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) invalid();
        if (enforceAcl) requireOwnerOnly(path);
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static void requireOwnerOnly(Path path) throws IOException {
        var posix = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            Set<PosixFilePermission> permissions = posix.readAttributes().permissions();
            if (permissions.stream().anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_"))) invalid();
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null || acl.getAcl().isEmpty()) invalid();
        String owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName();
        if (acl.getAcl().stream().anyMatch(entry -> !entry.principal().getName().equalsIgnoreCase(owner))) invalid();
    }

    private static void invalid() { throw new IllegalArgumentException("MIGRATION_AUTHORIZATION_INVALID"); }

    private static void writeCreateNewAndForce(Path path, String value) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8))); channel.force(true);
        }
    }

    static final class ConsumedAuthorization {
        private final Path root;
        private final Path marker;
        private final ReleaseMigrationAuthorization authorization;
        private ConsumedAuthorization(Path root, Path marker, ReleaseMigrationAuthorization authorization) {
            this.root = root; this.marker = marker; this.authorization = authorization;
        }
        void seal(String outcome) throws IOException {
            try (FileChannel channel = FileChannel.open(marker, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(ByteBuffer.wrap((outcome + "\n").getBytes(StandardCharsets.UTF_8))); channel.force(true);
            }
        }
        void publishReady(ReleaseMigrationAuthorization.ExecutionIdentity identity) throws Exception {
            String markerSha = sha256(Files.readAllBytes(marker));
            String body = String.join("\n", "status=READY", "runId=" + authorization.runId(),
                    "authorizationRef=" + authorization.authorizationRef(), "artifactSha256=" + identity.artifactSha256(),
                    "candidateManifestSha256=" + identity.candidateManifestSha256(),
                    "databaseName=" + identity.databaseName(), "expectedServerUuid=" + identity.expectedServerUuid(),
                    "oracleManifestSha256=" + identity.oracleManifestSha256(),
                    "migrationInventorySha256=" + identity.migrationInventorySha256(),
                    "consumptionSha256=" + markerSha) + "\n";
            Path temp = root.resolve("READY.properties.tmp");
            Path ready = root.resolve("READY.properties");
            Files.deleteIfExists(temp);
            writeCreateNewAndForce(temp, body);
            Files.move(temp, ready, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
