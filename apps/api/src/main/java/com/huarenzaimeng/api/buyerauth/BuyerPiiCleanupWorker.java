package com.huarenzaimeng.api.buyerauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
@Profile("release-mysql")
class BuyerPiiCleanupWorker {
    private final BuyerPiiCleanupTaskStore tasks;
    private final BuyerAccountLifecycleService lifecycle;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration retryDelay;

    @Autowired BuyerPiiCleanupWorker(BuyerPiiCleanupTaskStore tasks,BuyerAccountLifecycleService lifecycle,
                          @Value("${hz.buyer-closure.cleanup.lease-seconds:30}") long leaseSeconds,
                          @Value("${hz.buyer-closure.cleanup.retry-seconds:30}") long retrySeconds){
        this(tasks,lifecycle,Clock.systemUTC(),Duration.ofSeconds(leaseSeconds),Duration.ofSeconds(retrySeconds));
    }
    BuyerPiiCleanupWorker(BuyerPiiCleanupTaskStore tasks,BuyerAccountLifecycleService lifecycle,Clock clock,
                          Duration leaseDuration,Duration retryDelay){
        this.tasks=tasks;this.lifecycle=lifecycle;this.clock=clock;this.leaseDuration=leaseDuration;this.retryDelay=retryDelay;
    }
    boolean runOnce(String owner){
        var claim=tasks.claim(owner,clock.instant(),leaseDuration);
        if(claim.isEmpty())return false;
        try{lifecycle.completeClaimed(claim.get());return true;}
        catch(RuntimeException failure){tasks.retry(claim.get(),failure.getMessage(),clock.instant(),retryDelay);return false;}
    }
}
