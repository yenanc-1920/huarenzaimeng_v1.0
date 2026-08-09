package com.huarenzaimeng.api.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public final class ReleaseFlywayMigrationRunner implements ApplicationRunner {
    private final Flyway flyway;
    private final ReleaseMigrationState state;

    ReleaseFlywayMigrationRunner(Flyway flyway, ReleaseMigrationState state) {
        this.flyway = flyway;
        this.state = state;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            flyway.validate();
            flyway.migrate();
            state.ready();
        } catch (Exception failure) {
            state.failed();
            throw failure;
        }
    }
}
