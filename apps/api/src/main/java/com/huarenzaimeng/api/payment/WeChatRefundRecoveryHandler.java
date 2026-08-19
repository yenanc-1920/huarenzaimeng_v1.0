package com.huarenzaimeng.api.payment;

import com.huarenzaimeng.api.recovery.RecoveryTaskStore;
import com.huarenzaimeng.api.recovery.RecoveryTaskWorker;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("release-mysql")
final class WeChatRefundRecoveryHandler implements RecoveryTaskWorker.Handler {
    private final WeChatPayCoordinator coordinator;
    WeChatRefundRecoveryHandler(WeChatPayCoordinator coordinator){this.coordinator=coordinator;}
    @Override public String operationCode(){return "WECHAT_REFUND_QUERY";}
    @Override public RecoveryTaskWorker.Outcome execute(RecoveryTaskStore.Task task){
        WeChatPayCoordinator.RefundView refund=coordinator.refundStatus(task.aggregateRef());
        WeChatPayCoordinator.View payment=coordinator.statusForRecovery(refund.merchantOrderRef());
        if(payment.version()!=task.expectedAggregateVersion())return RecoveryTaskWorker.Outcome.SUCCEEDED;
        try{
            WeChatPayCoordinator.RefundView after=coordinator.queryRefundOriginal(task.aggregateRef());
            return after.state()==WeChatPayCoordinator.RefundState.UNKNOWN||after.state()==WeChatPayCoordinator.RefundState.PENDING
                    ?RecoveryTaskWorker.Outcome.RETRY:RecoveryTaskWorker.Outcome.SUCCEEDED;
        }catch(WeChatPayCoordinator.Conflict unavailable){return RecoveryTaskWorker.Outcome.RETRY;}
    }
}
