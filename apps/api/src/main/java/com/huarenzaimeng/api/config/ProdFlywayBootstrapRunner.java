package com.huarenzaimeng.api.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** One migration/bootstrap path shared by DEV, TEST, STAGE and PROD. */
@Component
@Profile("release-mysql & (local-mysql | test-mysql | stage-mysql | prod-mysql)")
final class ProdFlywayBootstrapRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ProdFlywayBootstrapRunner.class);
    static final long EXPECTED_VERSIONED_MIGRATION_COUNT = 25L;
    static final long EXPECTED_TERMINAL_MIGRATION_VERSION = 25L;
    private final DataSource dataSource;
    private final Flyway flyway;
    private final ReleaseMigrationState state;
    private final String expectedDatabase;
    private final boolean migrationEnabled;
    private final boolean developmentDataExpected;
    private final Executor executor;
    private final AtomicBoolean started = new AtomicBoolean();

    @Autowired
    ProdFlywayBootstrapRunner(DataSource dataSource,
            @Qualifier("releaseFlyway") Flyway flyway,
            ReleaseMigrationState state,
            @Value("${hz.environment.database-name}") String expectedDatabase,
            @Value("${hz.environment.migration-enabled:true}") boolean migrationEnabled,
            @Value("${hz.v1-dev-data.enabled:false}") boolean developmentDataExpected) {
        this(dataSource, flyway, state, expectedDatabase, migrationEnabled, developmentDataExpected,
                ProdFlywayBootstrapRunner::startDaemonWorker);
    }

    ProdFlywayBootstrapRunner(DataSource dataSource, Flyway flyway,
            ReleaseMigrationState state, String expectedDatabase,
            boolean migrationEnabled, boolean developmentDataExpected, Executor executor) {
        this.dataSource = dataSource;
        this.flyway = flyway;
        this.state = state;
        this.expectedDatabase = expectedDatabase;
        this.migrationEnabled = migrationEnabled;
        this.developmentDataExpected = developmentDataExpected;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!started.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                migrateAndVerify();
                LOG.info("ENVIRONMENT_FLYWAY_BOOTSTRAP_READY");
            } catch (Exception failure) {
                LOG.error("ENVIRONMENT_FLYWAY_BOOTSTRAP_FAILED", failure);
            }
        });
    }

    void migrateAndVerify() throws Exception {
        try {
            requireDatabaseIdentity();
            if (migrationEnabled) {
                flyway.migrate();
            }
            requireExpectedMigrationAndDevelopmentData();
            state.ready();
        } catch (Exception failure) {
            state.failed();
            throw failure;
        }
    }

    private static void startDaemonWorker(Runnable command) {
        Thread worker = new Thread(command, "prod-flyway-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }

    private void requireDatabaseIdentity() throws Exception {
        if (expectedDatabase == null || expectedDatabase.isBlank()) {
            throw new IllegalStateException("ENVIRONMENT_DATABASE_NAME_REQUIRED");
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next() || !expectedDatabase.equals(result.getString(1)) || result.next()) {
                throw new IllegalStateException("ENVIRONMENT_DATABASE_IDENTITY_MISMATCH");
            }
        }
    }

    private void requireExpectedMigrationAndDevelopmentData() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet history = statement.executeQuery(
                     "SELECT COUNT(*), COUNT(DISTINCT version), MAX(CAST(version AS UNSIGNED)), "
                             + "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), "
                             + "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) "
                             + "FROM flyway_schema_history WHERE version IS NOT NULL")) {
            if (!history.next()
                    || history.getLong(1) != EXPECTED_VERSIONED_MIGRATION_COUNT
                    || history.getLong(2) != EXPECTED_VERSIONED_MIGRATION_COUNT
                    || history.getLong(3) != EXPECTED_TERMINAL_MIGRATION_VERSION
                    || history.getLong(4) != EXPECTED_VERSIONED_MIGRATION_COUNT
                    || history.getLong(5) != 0L
                    || history.next()) {
                throw new IllegalStateException("ENVIRONMENT_FLYWAY_TERMINAL_STATE_INVALID");
            }
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet registry = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables "
                             + "WHERE table_schema = DATABASE() AND table_name = 'hz_v1_dev_seed_registry'")) {
            if (!registry.next()) {
                throw new IllegalStateException("ENVIRONMENT_DEVELOPMENT_SEED_STATE_INVALID");
            }
            boolean registryPresent = registry.getLong(1) == 1L;
            if (registry.next()) {
                throw new IllegalStateException("ENVIRONMENT_DEVELOPMENT_SEED_STATE_INVALID");
            }
            if (developmentDataExpected && !registryPresent) {
                throw new IllegalStateException("ENVIRONMENT_DEVELOPMENT_SEED_STATE_INVALID");
            }
            if (!registryPresent) return;
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet seeds = statement.executeQuery("SELECT COUNT(*) FROM hz_v1_dev_seed_registry")) {
            if (!seeds.next()) {
                throw new IllegalStateException("ENVIRONMENT_DEVELOPMENT_SEED_STATE_INVALID");
            }
            long seedCount = seeds.getLong(1);
            if (seeds.next()
                    || (developmentDataExpected && seedCount == 0L)
                    || (!developmentDataExpected && seedCount != 0L)) {
                throw new IllegalStateException("ENVIRONMENT_DEVELOPMENT_SEED_STATE_INVALID");
            }
        }
    }
}
