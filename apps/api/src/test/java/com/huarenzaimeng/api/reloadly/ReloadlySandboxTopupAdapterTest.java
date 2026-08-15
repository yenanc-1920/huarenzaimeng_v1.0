package com.huarenzaimeng.api.reloadly;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReloadlySandboxTopupAdapterTest {
    @Test void createUsesOnlyFixedOfficialSandboxCommandAndCallsTransportOnce() {
        FakeTransport transport=new FakeTransport();
        ReloadlySandboxTopupAdapter adapter=new ReloadlySandboxTopupAdapter(transport);
        ReloadlySandboxTopupPort.CreateResult result=adapter.create("RLD-A-20260814-001");
        assertThat(result).isEqualTo(new ReloadlySandboxTopupPort.Created(7001,new BigDecimal("1.00"),"USD"));
        assertThat(transport.creates).containsExactly(new ReloadlySandboxTopupAdapter.CreateCommand(
                "RLD-A-20260814-001",1100,"AE","0503971821","CA","11231231231"));
        assertThat(transport.statusCalls).isZero();
    }

    @Test void invalidRequestNeverReachesTransport() {
        FakeTransport transport=new FakeTransport();
        ReloadlySandboxTopupPort.CreateResult result=new ReloadlySandboxTopupAdapter(transport).create("bad");
        assertThat(result).isEqualTo(new ReloadlySandboxTopupPort.Rejected("REQUEST_REF_INVALID"));
        assertThat(transport.creates).isEmpty();
    }

    @Test void statusIsSingleInjectedCallAndNullFailsClosedWithoutRetry() {
        FakeTransport transport=new FakeTransport();transport.statusResult=null;
        ReloadlySandboxTopupPort.StatusResult result=new ReloadlySandboxTopupAdapter(transport).status(7001);
        assertThat(result).isEqualTo(new ReloadlySandboxTopupPort.StatusUnknown("STATUS_RESULT_UNKNOWN_NO_RETRY"));
        assertThat(transport.statusCalls).isEqualTo(1);
        assertThat(transport.creates).isEmpty();
    }

    private static final class FakeTransport implements ReloadlySandboxTopupAdapter.Transport {
        final List<ReloadlySandboxTopupAdapter.CreateCommand> creates=new ArrayList<>();int statusCalls;
        ReloadlySandboxTopupPort.StatusResult statusResult=new ReloadlySandboxTopupPort.Observed("SUCCESSFUL");
        @Override public ReloadlySandboxTopupPort.CreateResult create(ReloadlySandboxTopupAdapter.CreateCommand command){creates.add(command);return new ReloadlySandboxTopupPort.Created(7001,new BigDecimal("1.00"),"USD");}
        @Override public ReloadlySandboxTopupPort.StatusResult status(long ignored){statusCalls++;return statusResult;}
    }
}

