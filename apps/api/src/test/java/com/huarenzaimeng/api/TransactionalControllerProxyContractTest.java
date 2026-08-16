package com.huarenzaimeng.api;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class TransactionalControllerProxyContractTest {
    @Test
    void releaseTransactionalControllersSupportClassBasedSpringProxies() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        assertClassProxyCanBeCreated(new V1AdminCommandController(jdbc));
        assertClassProxyCanBeCreated(new V1DirectoryController(
                new V1DevelopmentDataService(jdbc, Clock.systemUTC()), jdbc));
    }

    private static void assertClassProxyCanBeCreated(Object target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) invocation -> invocation.proceed());
        assertThatCode(factory::getProxy).doesNotThrowAnyException();
    }
}
