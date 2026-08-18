package com.huarenzaimeng.api;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
@Component @Profile("release-mysql")
final class BusinessEventOutboxDispatcher {
    private final BusinessEventStore store;
    BusinessEventOutboxDispatcher(BusinessEventStore store){this.store=store;}
    int dispatch(String owner,int limit){return store.dispatchBatch(owner,limit,Instant.now(),Duration.ofSeconds(30));}
}
