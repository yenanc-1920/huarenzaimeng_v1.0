package com.huarenzaimeng.api.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ReleaseFlywayMigrationRunnerTest {
    @Test void migratesBeforeOpeningTheGate() throws Exception {
        Flyway flyway = mock(Flyway.class);
        ReleaseMigrationState state = new ReleaseMigrationState();

        new ReleaseFlywayMigrationRunner(flyway, state).run(new DefaultApplicationArguments());

        var order = inOrder(flyway);
        order.verify(flyway).migrate();
        assertThat(state.phase()).isEqualTo(ReleaseMigrationState.Phase.READY);
    }

    @Test void leavesGateClosedAndPropagatesMigrationFailure() {
        Flyway flyway = mock(Flyway.class);
        IllegalStateException failure = new IllegalStateException("synthetic migration failure");
        doThrow(failure).when(flyway).migrate();
        ReleaseMigrationState state = new ReleaseMigrationState();

        assertThatThrownBy(() -> new ReleaseFlywayMigrationRunner(flyway, state)
                .run(new DefaultApplicationArguments()))
                .isSameAs(failure);
        assertThat(state.phase()).isEqualTo(ReleaseMigrationState.Phase.FAILED);
    }
}
