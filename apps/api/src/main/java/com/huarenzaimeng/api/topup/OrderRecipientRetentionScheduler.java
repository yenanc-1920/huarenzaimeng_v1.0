package com.huarenzaimeng.api.topup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("release-mysql")
@ConditionalOnProperty(name="hz.recipient-retention.scheduler-enabled",havingValue="true")
class OrderRecipientRetentionScheduler {
    private final OrderRecipientRetentionStore store;
    OrderRecipientRetentionScheduler(OrderRecipientRetentionStore store){this.store=store;}
    @Scheduled(fixedDelayString="${hz.recipient-retention.scheduler-delay-ms:86400000}")
    void run(){store.runOnce();}
}
