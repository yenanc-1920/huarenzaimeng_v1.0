package com.huarenzaimeng.api.recovery;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class RecoveryTaskWorker {
    public enum Outcome { SUCCEEDED, RETRY, DEAD }
    public interface Handler { String operationCode(); Outcome execute(RecoveryTaskStore.Task task); }
    private final RecoveryTaskStore store;private final Map<String,Handler> handlers;private final Supplier<Instant> clock;
    public RecoveryTaskWorker(RecoveryTaskStore store,List<Handler> handlers,Supplier<Instant> clock){this.store=store;this.handlers=handlers.stream().collect(Collectors.toUnmodifiableMap(Handler::operationCode,x->x));this.clock=clock;}
    public int runOnce(String owner,int limit,Duration lease,Duration retryDelay){Instant now=clock.get();int completed=0;for(var claimed:store.claimBatch(owner,limit,now,lease)){var permitted=store.consumePermit(claimed.taskRef(),owner,claimed.taskVersion(),clock.get());if(permitted.isEmpty())continue;var task=permitted.get();Handler handler=handlers.get(task.operationCode());Outcome outcome=handler==null?Outcome.DEAD:handler.execute(task);RecoveryTaskStore.Completion result=switch(outcome){case SUCCEEDED->store.succeed(task.taskRef(),owner,task.taskVersion(),clock.get());case RETRY->store.retry(task.taskRef(),owner,task.taskVersion(),clock.get(),clock.get().plus(retryDelay),"PROVIDER_RESULT_UNKNOWN");case DEAD->store.dead(task.taskRef(),owner,task.taskVersion(),clock.get(),"RECOVERY_HANDLER_UNAVAILABLE");};if(result==RecoveryTaskStore.Completion.COMPLETED)completed++;}return completed;}
}
