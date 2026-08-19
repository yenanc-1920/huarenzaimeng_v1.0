package com.huarenzaimeng.api.buyerauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("release-mysql")
@ConditionalOnProperty(name="hz.buyer-closure.cleanup.scheduler-enabled",havingValue="true")
class BuyerPiiCleanupScheduler {
    private final BuyerPiiCleanupWorker worker;
    BuyerPiiCleanupScheduler(BuyerPiiCleanupWorker worker){this.worker=worker;}
    @Scheduled(fixedDelayString="${hz.buyer-closure.cleanup.scheduler-delay-ms:30000}")
    void run(){worker.runOnce("buyer-pii-cleanup-scheduler");}
}
