package com.huarenzaimeng.api.topup;

import com.huarenzaimeng.api.recovery.RecoveryTaskStore;
import com.huarenzaimeng.api.recovery.RecoveryTaskWorker;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("release-mysql")
final class WinlaTopupRecoveryHandler implements RecoveryTaskWorker.Handler {
    private final TopupCoordinator coordinator;
    WinlaTopupRecoveryHandler(TopupCoordinator coordinator){this.coordinator=coordinator;}
    @Override public String operationCode(){return "WINLA_TOPUP_QUERY";}
    @Override public RecoveryTaskWorker.Outcome execute(RecoveryTaskStore.Task task){
        TopupCoordinator.View before=coordinator.statusForRecovery(task.aggregateRef());
        if(before.version()!=task.expectedAggregateVersion())return RecoveryTaskWorker.Outcome.SUCCEEDED;
        try{
            TopupCoordinator.View after=coordinator.query(task.aggregateRef());
            return switch(after.state()){
                case DELIVERED,REJECTED,RECONCILIATION_REQUIRED->RecoveryTaskWorker.Outcome.SUCCEEDED;
                default->RecoveryTaskWorker.Outcome.RETRY;
            };
        }catch(TopupCoordinator.Conflict unavailable){return RecoveryTaskWorker.Outcome.RETRY;}
    }
}
