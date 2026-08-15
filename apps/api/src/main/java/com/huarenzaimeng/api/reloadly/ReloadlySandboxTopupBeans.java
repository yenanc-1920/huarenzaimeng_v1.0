package com.huarenzaimeng.api.reloadly;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods=false)
@Profile("release-mysql")
@ConditionalOnProperty(name="hz.reloadly.sandbox-topup.enabled",havingValue="true")
class ReloadlySandboxTopupBeans {
    @Bean ReloadlySandboxTopupStore reloadlySandboxTopupStore(JdbcTemplate jdbc,TransactionTemplate transactions){
        return new JdbcReloadlySandboxTopupStore(jdbc,transactions,Clock.systemUTC());
    }
    @Bean @ConditionalOnBean(ReloadlySandboxTopupPort.class)
    ReloadlySandboxTopupService reloadlySandboxTopupService(ReloadlySandboxTopupPort provider,ReloadlySandboxTopupStore store){
        return new ReloadlySandboxTopupService(provider,store);
    }
}

