package com.huarenzaimeng.api.payment;

import com.huarenzaimeng.api.recovery.RecoveryTaskStore;
import com.huarenzaimeng.api.recovery.RecoveryTaskWorker;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("release-mysql")
final class WeChatPaymentRecoveryHandler implements RecoveryTaskWorker.Handler {
    private final WeChatPayCoordinator coordinator;
    WeChatPaymentRecoveryHandler(WeChatPayCoordinator coordinator){this.coordinator=coordinator;}
    @Override public String operationCode(){return "WECHAT_PAYMENT_QUERY";}
    @Override public RecoveryTaskWorker.Outcome execute(RecoveryTaskStore.Task task){
        WeChatPayCoordinator.View before=coordinator.statusForRecovery(task.aggregateRef());
        if(before.version()!=task.expectedAggregateVersion())return RecoveryTaskWorker.Outcome.SUCCEEDED;
        try{
            WeChatPayCoordinator.View after=coordinator.query(task.aggregateRef());
            return switch(after.state()){
                case PAID,CLOSED,REFUNDED,REJECTED->RecoveryTaskWorker.Outcome.SUCCEEDED;
                default->RecoveryTaskWorker.Outcome.RETRY;
            };
        }catch(WeChatPayCoordinator.Conflict unavailable){return RecoveryTaskWorker.Outcome.RETRY;}
    }
}
