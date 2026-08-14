package com.huarenzaimeng.api.config;

import com.huarenzaimeng.api.ApiApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** The unique governed migration entry. It accepts no caller-provided paths or identities. */
public final class ReleaseMigrationLauncher {
    static final String LOADER_MAIN = "com.huarenzaimeng.api.config.ReleaseMigrationLauncher";
    static final String PROPERTIES_LAUNCHER = "org.springframework.boot.loader.launch.PropertiesLauncher";
    private static final Path ROOT = Path.of(System.getProperty("os.name", "").startsWith("Windows")
            ? "E:\\huarenzaimeng-controlled\\data-migration" : "/var/lib/huarenzaimeng-controlled/data-migration");
    private ReleaseMigrationLauncher() {}

    public static void main(String[] args) throws Exception {
        verifyActualProcess(args);
        try (var context = new SpringApplicationBuilder(ApiApplication.class)
                .profiles("release-mysql").web(WebApplicationType.NONE).run()) {
            Flyway flyway = context.getBean(Flyway.class);
            DataSource application = context.getBean(DataSource.class);
            ReleaseMigrationState state = context.getBean(ReleaseMigrationState.class);
            var store = ReleaseMigrationAuthorizationStore.fixed(Clock.systemUTC());
            var identities = new ReleaseMigrationRuntimeIdentityProvider(application, flyway);
            new ReleaseFlywayMigrationRunner(ReleaseFlywayMigrationRunner.flywayStages(flyway), state, store, identities)
                    .execute();
        }
    }

    static void verifyActualProcess(String[] args) throws Exception {
        if (args.length != 0) fail();
        if (!PROPERTIES_LAUNCHER.equals(System.getProperty("sun.java.command"))) fail();
        if (!LOADER_MAIN.equals(System.getProperty("loader.main"))) fail();
        String[] classpath = System.getProperty("java.class.path", "").split(java.io.File.pathSeparator, -1);
        if (classpath.length != 1) fail();
        Path jar = Path.of(classpath[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(jar)) fail();
        String command = ProcessHandle.current().info().command().orElseThrow(
                () -> new IllegalStateException("MIGRATION_LAUNCH_IDENTITY_INVALID"));
        Path java = Path.of(command).toAbsolutePath().normalize();
        if (!Files.isRegularFile(java, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(java)) fail();
        Path javaIdentity = ROOT.resolve("java.sha256");
        if (!Files.isRegularFile(javaIdentity, LinkOption.NOFOLLOW_LINKS)) fail();
        String expected = Files.readString(javaIdentity, StandardCharsets.US_ASCII).trim();
        if (!ReleaseMigrationAuthorizationStore.sha256(Files.readAllBytes(java)).equals(expected)) fail();
    }

    private static void fail() { throw new IllegalStateException("MIGRATION_LAUNCH_IDENTITY_INVALID"); }
}
