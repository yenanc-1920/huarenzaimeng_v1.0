package com.huarenzaimeng.api.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public final class ReleaseFlywayMigrationRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ReleaseFlywayMigrationRunner.class);
    private final Flyway flyway;
    private final ReleaseMigrationState state;

    ReleaseFlywayMigrationRunner(Flyway flyway, ReleaseMigrationState state) {
        this.flyway = flyway;
        this.state = state;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            LOG.info("RELEASE_MIGRATE_STARTED");
            flyway.migrate();
            state.ready();
            LOG.info("RELEASE_MIGRATE_READY");
        } catch (Exception failure) {
            state.failed();
            LOG.error("RELEASE_MIGRATE_FAILED type={}", failure.getClass().getSimpleName());
            throw failure;
        }
    }
}
