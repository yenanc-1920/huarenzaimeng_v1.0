package com.huarenzaimeng.api.buyerauth;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BuyerPiiCleanupSchedulerAssemblyTest {
    @Test void schedulerIsAbsentByDefaultAndReachableOnlyWhenExplicitlyEnabled(){
        try(var disabled=context(false)){assertThat(disabled.getBeansOfType(BuyerPiiCleanupScheduler.class)).isEmpty();}
        try(var enabled=context(true)){assertThat(enabled.getBeansOfType(BuyerPiiCleanupScheduler.class)).hasSize(1);}
    }
    private static AnnotationConfigApplicationContext context(boolean enabled){
        var context=new AnnotationConfigApplicationContext();context.getEnvironment().setActiveProfiles("release-mysql");
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",
                java.util.Map.of("hz.buyer-closure.cleanup.scheduler-enabled",String.valueOf(enabled))));
        context.registerBean(BuyerPiiCleanupWorker.class,()->mock(BuyerPiiCleanupWorker.class));
        context.register(BuyerPiiCleanupSchedulingConfiguration.class,BuyerPiiCleanupScheduler.class);context.refresh();return context;
    }
}
