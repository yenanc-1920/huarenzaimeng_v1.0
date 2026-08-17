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

/** Empty/PRE_V10 bootstrap and read-only terminal verifier for PROD. */
@Component
@Profile("release-mysql & prod-mysql")
final class ProdFlywayBootstrapRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ProdFlywayBootstrapRunner.class);
    private final DataSource dataSource;
    private final Flyway flyway;
    private final ReleaseMigrationState state;
    private final String expectedDatabase;
    private final boolean initializeEmptyDatabase;
    private final Executor executor;
    private final AtomicBoolean started = new AtomicBoolean();

    @Autowired
    ProdFlywayBootstrapRunner(DataSource dataSource,
            @Qualifier("releaseFlyway") Flyway flyway,
            ReleaseMigrationState state,
            @Value("${hz.environment.database-name}") String expectedDatabase,
            @Value("${hz.environment.initialize-empty-database:false}") boolean initializeEmptyDatabase) {
        this(dataSource, flyway, state, expectedDatabase, initializeEmptyDatabase,
                ProdFlywayBootstrapRunner::startDaemonWorker);
    }

    ProdFlywayBootstrapRunner(DataSource dataSource, Flyway flyway,
            ReleaseMigrationState state, String expectedDatabase,
            boolean initializeEmptyDatabase, Executor executor) {
        this.dataSource = dataSource;
        this.flyway = flyway;
        this.state = state;
        this.expectedDatabase = expectedDatabase;
        this.initializeEmptyDatabase = initializeEmptyDatabase;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!started.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                migrateAndVerify();
                LOG.info("PROD_FLYWAY_BOOTSTRAP_READY");
            } catch (Exception failure) {
                LOG.error("PROD_FLYWAY_BOOTSTRAP_FAILED", failure);
            }
        });
    }

    void migrateAndVerify() throws Exception {
        try {
            requireDatabaseIdentity();
            if (initializeEmptyDatabase) {
                requireApprovedInitializationSource();
                flyway.migrate();
            }
            requirePostV14WithoutDevelopmentSeeds();
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
            throw new IllegalStateException("PROD_DATABASE_NAME_REQUIRED");
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next() || !expectedDatabase.equals(result.getString(1)) || result.next()) {
                throw new IllegalStateException("PROD_DATABASE_IDENTITY_MISMATCH");
            }
        }
    }

    private void requireApprovedInitializationSource() throws Exception {
        long tableCount;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            if (!result.next()) throw new IllegalStateException("PROD_INITIALIZATION_SOURCE_INVALID");
            tableCount = result.getLong(1);
            if (result.next()) throw new IllegalStateException("PROD_INITIALIZATION_SOURCE_INVALID");
        }
        if (tableCount == 0L) return;
        if (tableCount != 20L) throw new IllegalStateException("PROD_INITIALIZATION_SOURCE_INVALID");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet history = statement.executeQuery(
                     "SELECT COUNT(*), COUNT(DISTINCT version), "
                             + "MIN(CAST(version AS UNSIGNED)), MAX(CAST(version AS UNSIGNED)), "
                             + "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), "
                             + "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), "
                             + "SUM(CASE WHEN installed_rank = CAST(version AS UNSIGNED) THEN 1 ELSE 0 END) "
                             + "FROM flyway_schema_history WHERE version IS NOT NULL")) {
            if (!history.next()
                    || history.getLong(1) != 10L
                    || history.getLong(2) != 10L
                    || history.getLong(3) != 1L
                    || history.getLong(4) != 10L
                    || history.getLong(5) != 10L
                    || history.getLong(6) != 0L
                    || history.getLong(7) != 10L
                    || history.next()) {
                throw new IllegalStateException("PROD_INITIALIZATION_SOURCE_INVALID");
            }
        }
        // This also verifies that the applied V1-V10 checksums match the fixed
        // migration scripts before the interrupted initialization is resumed.
        flyway.validate();
    }

    private void requirePostV14WithoutDevelopmentSeeds() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet history = statement.executeQuery(
                     "SELECT COUNT(*), COUNT(DISTINCT version), MAX(CAST(version AS UNSIGNED)), "
                             + "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), "
                             + "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) "
                             + "FROM flyway_schema_history WHERE version IS NOT NULL")) {
            if (!history.next()
                    || history.getLong(1) != 14L
                    || history.getLong(2) != 14L
                    || history.getLong(3) != 14L
                    || history.getLong(4) != 14L
                    || history.getLong(5) != 0L
                    || history.next()) {
                throw new IllegalStateException("PROD_FLYWAY_TERMINAL_STATE_INVALID");
            }
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet seeds = statement.executeQuery("SELECT COUNT(*) FROM hz_v1_dev_seed_registry")) {
            if (!seeds.next() || seeds.getLong(1) != 0L || seeds.next()) {
                throw new IllegalStateException("PROD_DEVELOPMENT_SEED_FORBIDDEN");
            }
        }
    }
}
