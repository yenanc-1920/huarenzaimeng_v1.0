package com.huarenzaimeng.api.buyerauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BuyerAccountLifecycleServiceTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void requestAlwaysStopsAllSessionsAndNeverClosesInline() throws Exception {
        FakeJdbc jdbc=new FakeJdbc();jdbc.identity=Map.of("buyer_id","BUYER-ID","status_code","ACTIVE");
        var service=new BuyerAccountLifecycleService(jdbc);
        var result=service.request("BUYER-SUBJECT","IDEMPOTENCY-0001",json.readTree("{\"requestRef\":\"CLOSURE-REQUEST-1\",\"reason\":\"user requested closure\",\"expectedVersion\":1}"));
        assertThat(result.state()).isEqualTo("REQUESTED");assertThat(result.version()).isEqualTo(1);assertThat(result.blockerCount()).isZero();
        assertThat(jdbc.updates).anySatisfy(sql->assertThat(sql).contains("UPDATE buyer_session SET status_code='REVOKED'"));
        assertThat(jdbc.updates).anySatisfy(sql->assertThat(sql).contains("buyer_pii_cleanup_task","'READY'"));
        assertThat(jdbc.updates).allSatisfy(sql->assertThat(sql).doesNotContain("closure_state='CLOSED'"));
    }

    @Test void requestedAccountWithTransactionBlockersStillGetsDurableCleanupTask() throws Exception {
        FakeJdbc jdbc=new FakeJdbc();jdbc.identity=Map.of("buyer_id","BUYER-ID","status_code","ACTIVE");jdbc.blockers=1;
        var result=new BuyerAccountLifecycleService(jdbc).request("BUYER-SUBJECT","IDEMPOTENCY-0003",json.readTree("{\"requestRef\":\"CLOSURE-REQUEST-3\",\"reason\":\"wait for settlement\",\"expectedVersion\":1}"));
        assertThat(result.state()).isEqualTo("REQUESTED");assertThat(result.blockerCount()).isEqualTo(4);
        assertThat(jdbc.updates).anySatisfy(sql->assertThat(sql).contains("buyer_pii_cleanup_task","'READY'"));
    }

    @Test void cleanupSucceedsBeforeClosedCasAndRetainsTransactionSnapshots() throws Exception {
        FakeJdbc jdbc=new FakeJdbc();jdbc.closure=Map.of("buyer_id","BUYER-ID","request_ref","CLOSURE-REQUEST-1","closure_state","REQUESTED","aggregate_version",1L);
        var service=new BuyerAccountLifecycleService(jdbc);
        var result=service.complete("BUYER-SUBJECT","CLOSURE-001","IDEMPOTENCY-0002",json.readTree("{\"reason\":\"cleanup worker\",\"expectedVersion\":1}"));
        assertThat(result.state()).isEqualTo("CLOSED");assertThat(result.version()).isEqualTo(2);
        int cleanup=position(jdbc.updates,"task_state='SUCCEEDED'");int close=position(jdbc.updates,"closure_state='CLOSED'");
        assertThat(cleanup).isGreaterThanOrEqualTo(0).isLessThan(close);
        assertThat(jdbc.updates).anySatisfy(sql->assertThat(sql).contains("DELETE FROM buyer_session"));
        assertThat(jdbc.updates).anySatisfy(sql->assertThat(sql).contains("UPDATE buyer_identity SET subject_ref=?","provider_appid_digest=?","provider_subject_digest=?","status_code='CLOSED'"));
        assertThat(jdbc.updates).allSatisfy(sql->assertThat(sql).doesNotContain("DELETE FROM hz_order","DELETE FROM hz_payment","DELETE FROM hz_topup","DELETE FROM hz_release_order_snapshot"));
    }

    @Test void leasedWorkerCleanupMustOwnLeaseAndIsIdempotentAfterClose(){
        FakeJdbc jdbc=new FakeJdbc();jdbc.leaseValid=true;jdbc.closure=Map.of("buyer_id","BUYER-ID","request_ref","CLOSURE-REQUEST-1","closure_state","REQUESTED","aggregate_version",1L);
        var service=new BuyerAccountLifecycleService(jdbc);
        var result=service.completeClaimed(new BuyerPiiCleanupTaskStore.Claim("CLEANUP-1","CLOSURE-001","BUYER-SUBJECT",1,"worker-1",2));
        assertThat(result.state()).isEqualTo("CLOSED");
        assertThat(jdbc.updates).anySatisfy(sql->assertThat(sql).contains("task_state='LEASED'","lease_owner=?"));
        jdbc.closure=Map.of("buyer_id","BUYER-ID","request_ref","CLOSURE-REQUEST-1","closure_state","CLOSED","aggregate_version",2L);
        assertThat(service.completeClaimed(new BuyerPiiCleanupTaskStore.Claim("CLEANUP-1","CLOSURE-001","BUYER-SUBJECT",2,"worker-1",3)).state()).isEqualTo("CLOSED");
    }

    private static int position(List<String> values,String token){for(int i=0;i<values.size();i++)if(values.get(i).contains(token))return i;return -1;}

    private static final class FakeJdbc extends JdbcTemplate {
        final List<String> updates=new ArrayList<>();Map<String,Object> identity;Map<String,Object> closure;int blockers;boolean leaseValid;
        @Override public List<Map<String,Object>> queryForList(String sql,Object... args){
            if(sql.contains("WHERE idempotency_key="))return List.of();
            if(sql.contains("FROM buyer_identity"))return identity==null?List.of():List.of(identity);
            if(sql.contains("FROM buyer_account_closure_request WHERE closure_ref="))return closure==null?List.of():List.of(closure);
            return List.of();
        }
        @Override public <T>T queryForObject(String sql,Class<T> type,Object... args){return type.cast(Integer.valueOf(sql.contains("buyer_pii_cleanup_task WHERE closure_ref")?(leaseValid?1:0):blockers));}
        @Override public int update(String sql,Object... args){updates.add(sql);return 1;}
    }
}
