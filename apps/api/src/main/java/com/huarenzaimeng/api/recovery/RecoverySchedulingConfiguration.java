package com.huarenzaimeng.api.recovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="hz.recovery.scheduler.enabled",havingValue="true")
class RecoverySchedulingConfiguration {}
