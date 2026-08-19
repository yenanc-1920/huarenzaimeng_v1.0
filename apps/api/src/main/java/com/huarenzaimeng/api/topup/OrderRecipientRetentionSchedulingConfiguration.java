package com.huarenzaimeng.api.topup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile("release-mysql")
@ConditionalOnProperty(name="hz.recipient-retention.scheduler-enabled",havingValue="true")
class OrderRecipientRetentionSchedulingConfiguration {}
