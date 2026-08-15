package com.huarenzaimeng.api.reloadly;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReloadlySandboxTopupServiceTest {
    @Test void createPersistsCommandAndAcceptedFactThenReplayCallsProviderZero() {
        FakePort provider=new FakePort();MemoryStore store=new MemoryStore();ReloadlySandboxTopupService service=new ReloadlySandboxTopupService(provider,store);
        var first=service.create("RLD-A-20260814-001");var replay=service.create("RLD-A-20260814-001");
        assertThat(first.outcome()).isEqualTo("CREATED");assertThat(first.state()).isEqualTo("ACCEPTED");
        assertThat(replay.outcome()).isEqualTo("REPLAYED");assertThat(provider.createCalls).isEqualTo(1);
        assertThat(store.commandWrites).isEqualTo(1);assertThat(store.factWrites).isEqualTo(1);
    }

    @Test void activeStatusQueryPersistsObservationAndReadDoesNotCallProvider() {
        FakePort provider=new FakePort();MemoryStore store=new MemoryStore();ReloadlySandboxTopupService service=new ReloadlySandboxTopupService(provider,store);
        service.create("RLD-A-20260814-002");var read=service.read("RLD-A-20260814-002");var queried=service.queryStatus("RLD-A-20260814-002");
        assertThat(read.outcome()).isEqualTo("READ_ONLY");assertThat(provider.statusCalls).isEqualTo(1);
        assertThat(queried.providerStatus()).isEqualTo("SUCCESSFUL");assertThat(store.statusWrites).isEqualTo(1);
        assertThat(queried.automaticRetry()).isFalse();
    }

    @Test void unknownCreateIsPermanentlyLatchedAndNeverRetried() {
        FakePort provider=new FakePort();provider.createResult=new ReloadlySandboxTopupPort.Unknown("UNKNOWN");
        MemoryStore store=new MemoryStore();ReloadlySandboxTopupService service=new ReloadlySandboxTopupService(provider,store);
        assertThat(service.create("RLD-A-20260814-003").state()).isEqualTo("UNKNOWN");
        assertThat(service.create("RLD-A-20260814-003").outcome()).isEqualTo("REPLAYED");
        assertThat(provider.createCalls).isEqualTo(1);assertThat(store.factWrites).isZero();
    }

    private static final class FakePort implements ReloadlySandboxTopupPort {
        int createCalls,statusCalls;CreateResult createResult=new Created(7001,new BigDecimal("1.00"),"USD");
        public CreateResult create(String ignored){createCalls++;return createResult;}
        public StatusResult status(long ignored){statusCalls++;return new Observed("SUCCESSFUL");}
    }

    static final class MemoryStore implements ReloadlySandboxTopupStore {
        final Map<String,Record> rows=new LinkedHashMap<>();int commandWrites,factWrites,statusWrites;
        public Begin begin(String ref,String fingerprint){if(rows.containsKey(ref))return Begin.EXISTING;rows.put(ref,new Record(ref,"SUBMITTED",null,null,null,null));commandWrites++;return Begin.NEW;}
        public void created(String ref,long id,BigDecimal amount,String currency){rows.put(ref,new Record(ref,"ACCEPTED",id,amount,currency,null));factWrites++;}
        public void rejected(String ref){Record r=require(ref);rows.put(ref,new Record(ref,"REJECTED",null,null,null,null));}
        public void unknown(String ref){Record r=require(ref);rows.put(ref,new Record(ref,"UNKNOWN",null,null,null,null));}
        public Record require(String ref){Record r=rows.get(ref);if(r==null)throw new IllegalStateException("NOT_FOUND");return r;}
        public void observed(String ref,long id,String status){Record r=require(ref);rows.put(ref,new Record(ref,r.commandState(),id,r.amount(),r.currency(),status));statusWrites++;}
    }
}

