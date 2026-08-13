package com.huarenzaimeng.api;

final class DataIntegrationReadinessStageException extends RuntimeException {
    enum Stage {
        APP_CONNECT,
        FLYWAY_CONNECT,
        READ_ONLY_SETUP_APP,
        READ_ONLY_SETUP_FLYWAY,
        DB_IDENTITY,
        APP_GRANTS,
        FLYWAY_GRANTS,
        FLYWAY_HISTORY,
        SCHEMA,
        MIGRATION_DISCOVERY,
        BACKUP_STATUS,
        INTERNAL_SAFE
    }

    private final Stage stage;

    DataIntegrationReadinessStageException(Stage stage) {
        super(null, null, false, false);
        this.stage = stage;
    }

    Stage stage() { return stage; }
}
