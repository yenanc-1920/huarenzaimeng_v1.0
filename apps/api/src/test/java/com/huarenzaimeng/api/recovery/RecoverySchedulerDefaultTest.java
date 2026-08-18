package com.huarenzaimeng.api.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RecoverySchedulerDefaultTest {
    @Test void schedulerIsAbsentUnlessExplicitlyEnabled(){
        new ApplicationContextRunner()
                .withUserConfiguration(RecoveryTaskScheduler.class,RecoverySchedulingConfiguration.class)
                .run(context->{
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RecoveryTaskScheduler.class);
                    assertThat(context).doesNotHaveBean(RecoverySchedulingConfiguration.class);
                });
    }
}
