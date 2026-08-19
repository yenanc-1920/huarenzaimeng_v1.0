package com.huarenzaimeng.api.buyerauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile("release-mysql")
@ConditionalOnProperty(name="hz.buyer-closure.cleanup.scheduler-enabled",havingValue="true")
class BuyerPiiCleanupSchedulingConfiguration {}
