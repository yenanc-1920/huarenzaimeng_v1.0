package com.huarenzaimeng.api.recovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name="hz.recovery.scheduler.enabled",havingValue="true")
final class RecoveryTaskScheduler {
    private final RecoveryTaskWorker worker;
    RecoveryTaskScheduler(RecoveryTaskStore store,List<RecoveryTaskWorker.Handler> handlers){this.worker=new RecoveryTaskWorker(store,handlers,Instant::now);}
    @Scheduled(fixedDelayString="${hz.recovery.scheduler.delay-ms:30000}") void run(){worker.runOnce("recovery-scheduler",20,Duration.ofSeconds(30),Duration.ofSeconds(15));}
}
