package com.huarenzaimeng.api.topup;

import com.huarenzaimeng.api.BusinessEventStore;
import com.huarenzaimeng.core.BusinessEventLinker;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcProviderExposureStoreTest {
    private JdbcTemplate jdbc;
    private JdbcDataSource dataSource;
    private JdbcProviderExposureStore store;
    private TransactionTemplate tx;

    @BeforeEach void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:provider-exposure-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.execute("CREATE TABLE hz_provider_limit(limit_ref VARCHAR(64) PRIMARY KEY,provider_code VARCHAR(32),channel_ref VARCHAR(64),operator_code VARCHAR(64),currency VARCHAR(3),max_inflight_count DECIMAL(20),max_inflight_minor DECIMAL(20),max_single_minor DECIMAL(20),enabled BOOLEAN,aggregate_version DECIMAL(20),reserved_count DECIMAL(20),reserved_minor DECIMAL(20),updated_at TIMESTAMP,UNIQUE(provider_code,channel_ref,operator_code,currency))");
        jdbc.execute("CREATE TABLE hz_provider_exposure(exposure_ref VARCHAR(80) PRIMARY KEY,merchant_order_ref VARCHAR(64) UNIQUE,provider_code VARCHAR(32),channel_ref VARCHAR(64),operator_code VARCHAR(64),currency VARCHAR(3),amount_minor BIGINT,exposure_state VARCHAR(24),limit_version BIGINT,aggregate_version BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP)");
        store = new JdbcProviderExposureStore(jdbc);
    }

    @Test void twoInstancesCannotRaceBeyondCountLimit() throws Exception {
        limit("L1", "WINLA", "CH-1", "GP", "BDT", 1, 10_000, 10_000, true);
        JdbcProviderExposureStore other = new JdbcProviderExposureStore(new JdbcTemplate(dataSource));
        AtomicInteger accepted = new AtomicInteger(), rejected = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> one = pool.submit(() -> reserveInTransaction(store, "O-1", ready, go, accepted, rejected));
            Future<?> two = pool.submit(() -> reserveInTransaction(other, "O-2", ready, go, accepted, rejected));
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            go.countDown(); one.get(5, TimeUnit.SECONDS); two.get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(accepted).hasValue(1);
        assertThat(rejected).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_provider_exposure", Integer.class)).isEqualTo(1);
    }

    @Test void replayIsIdempotentButChangedScopeOrAmountConflicts() {
        limit("L1", "WINLA", "CH-1", "GP", "BDT", 2, 10_000, 10_000, true);
        store.reserve("O-1", "WINLA", "CH-1", "GP", "BDT", 1_000);
        store.reserve("O-1", "WINLA", "CH-1", "GP", "BDT", 1_000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_provider_exposure", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> store.reserve("O-1", "WINLA", "CH-1", "GP", "BDT", 1_001))
                .hasMessage("TOPUP_EXPOSURE_IDEMPOTENCY_CONFLICT");
    }

    @Test void scopesAreIsolatedAndUnknownKeepsExposureUntilRejectOrDeliverySettlement() {
        limit("L-GP", "WINLA", "CH-1", "GP", "BDT", 1, 1_000, 1_000, true);
        limit("L-ROBI", "WINLA", "CH-1", "ROBI", "BDT", 1, 1_000, 1_000, true);
        store.reserve("O-GP-1", "WINLA", "CH-1", "GP", "BDT", 1_000);
        store.reserve("O-ROBI", "WINLA", "CH-1", "ROBI", "BDT", 1_000);
        assertThatThrownBy(() -> store.reserve("O-GP-2", "WINLA", "CH-1", "GP", "BDT", 1_000))
                .hasMessage("TOPUP_EXPOSURE_COUNT_LIMIT");

        store.release("O-GP-1");
        store.reserve("O-GP-2", "WINLA", "CH-1", "GP", "BDT", 1_000);
        store.settle("O-GP-2");
        store.reserve("O-GP-3", "WINLA", "CH-1", "GP", "BDT", 1_000);
        assertThat(jdbc.queryForObject("SELECT exposure_state FROM hz_provider_exposure WHERE merchant_order_ref='O-GP-1'", String.class)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT exposure_state FROM hz_provider_exposure WHERE merchant_order_ref='O-GP-2'", String.class)).isEqualTo("SETTLED");
    }

    @Test void missingDisabledNegativeSingleAmountAndOverflowFailClosed() {
        assertThatThrownBy(() -> store.reserve("O-MISSING", "WINLA", "CH-1", "GP", "BDT", 1)).hasMessage("TOPUP_EXPOSURE_LIMIT_MISSING");
        limit("L-OFF", "WINLA", "CH-1", "GP", "BDT", 2, 10, 10, false);
        assertThatThrownBy(() -> store.reserve("O-OFF", "WINLA", "CH-1", "GP", "BDT", 1)).hasMessage("TOPUP_EXPOSURE_LIMIT_DISABLED");
        jdbc.update("UPDATE hz_provider_limit SET enabled=TRUE");
        assertThatThrownBy(() -> store.reserve("O-NEG", "WINLA", "CH-1", "GP", "BDT", -1)).hasMessage("TOPUP_EXPOSURE_AMOUNT_INVALID");
        assertThatThrownBy(() -> store.reserve("O-SINGLE", "WINLA", "CH-1", "GP", "BDT", 11)).hasMessage("TOPUP_EXPOSURE_SINGLE_LIMIT");

        jdbc.update("UPDATE hz_provider_limit SET max_inflight_count=?,max_inflight_minor=?,max_single_minor=?", Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        jdbc.update("UPDATE hz_provider_limit SET reserved_count=1,reserved_minor=?",Long.MAX_VALUE);
        jdbc.update("INSERT INTO hz_provider_exposure VALUES('EXP-OLD','O-OLD','WINLA','CH-1','GP','BDT',?,'RESERVED',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", Long.MAX_VALUE);
        assertThatThrownBy(() -> store.reserve("O-OVERFLOW", "WINLA", "CH-1", "GP", "BDT", 1)).hasMessage("TOPUP_EXPOSURE_OVERFLOW");
    }

    @Test void exposureBalanceReservationLatchLedgerAndEventRollBackTogether() {
        createTopupTables();
        limit("L1", "WINLA", "CH-1", "GP", "BDT", 2, 10_000, 10_000, true);
        jdbc.update("INSERT INTO hz_provider_balance_position VALUES('WINLA','BDT',10000,0,0,CURRENT_TIMESTAMP,DATEADD('MINUTE',15,CURRENT_TIMESTAMP),'E',CURRENT_TIMESTAMP)");
        JdbcTopupStore topups = new JdbcTopupStore(jdbc, new FailingEvents(), null, store);
        TopupProviderPort.Command command = new TopupProviderPort.Command("O-TX", "REQ-TX", "SKU", "8801712345678", 1000, "BDT", "a".repeat(64));
        TopupEligibilityPort.Snapshot snapshot = new TopupEligibilityPort.Snapshot("O-TX", "BUYER", "SKU", "8801712345678", 1000, "BDT", "b".repeat(64), "CH-1", "GP");

        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> topups.beginAndReserve(command, snapshot, 2)))
                .hasMessage("INJECTED_EVENT_FAILURE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_provider_exposure", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_provider_balance_reservation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_provider_balance_ledger", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hz_topup_coordination", Integer.class)).isZero();
    }

    @Test void confirmedZeroAndNearLongMaxCountersFailClosedWithoutOverflow() {
        createTopupTables();
        limit("L1", "WINLA", "CH-1", "GP", "BDT", 3, Long.MAX_VALUE, Long.MAX_VALUE, true);
        JdbcTopupStore topups = new JdbcTopupStore(jdbc, null, null, store);
        TopupProviderPort.Command zero = command("O-ZERO", "REQ-ZERO", 1);
        TopupEligibilityPort.Snapshot snapshot = snapshot("O-ZERO", BigDecimal.ONE);
        jdbc.update("INSERT INTO hz_provider_balance_position VALUES('WINLA','BDT',0,0,0,CURRENT_TIMESTAMP,DATEADD('MINUTE',15,CURRENT_TIMESTAMP),'E',CURRENT_TIMESTAMP)");
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> topups.beginAndReserve(zero, snapshot, 2)))
                .hasMessage("TOPUP_BALANCE_INSUFFICIENT");
        assertThat(jdbc.queryForObject("SELECT reserved_count FROM hz_provider_limit WHERE limit_ref='L1'",Long.class)).isZero();

        jdbc.update("UPDATE hz_provider_balance_position SET confirmed_balance_minor=?,active_reserved_minor=?,safety_buffer_minor=?",Long.MAX_VALUE,Long.MAX_VALUE-2,2);
        TopupProviderPort.Command near = command("O-NEAR", "REQ-NEAR", 1);
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> topups.beginAndReserve(near, snapshot("O-NEAR",BigDecimal.ONE),2)))
                .hasMessage("TOPUP_BALANCE_INSUFFICIENT");
        assertThat(jdbc.queryForObject("SELECT active_reserved_minor FROM hz_provider_balance_position",Long.class)).isEqualTo(Long.MAX_VALUE-2);
    }

    @Test void coordinationLatchReplaysOnlyTheCompleteImmutableScope() {
        createTopupTables();
        limit("L1", "WINLA", "CH-1", "GP", "BDT", 3, 10_000, 10_000, true);
        jdbc.update("INSERT INTO hz_provider_balance_position VALUES('WINLA','BDT',10000,0,0,CURRENT_TIMESTAMP,DATEADD('MINUTE',15,CURRENT_TIMESTAMP),'E',CURRENT_TIMESTAMP)");
        JdbcTopupStore topups = new JdbcTopupStore(jdbc, null, null, store);
        TopupProviderPort.Command command=command("O-SCOPE","REQ-SCOPE",1000);
        TopupEligibilityPort.Snapshot snapshot=snapshot("O-SCOPE",new BigDecimal("9.2500"));
        TopupCoordinator.Begin created=tx.execute(ignored->topups.beginAndReserve(command,snapshot,2));
        TopupCoordinator.Begin replay=tx.execute(ignored->topups.beginAndReserve(command,snapshot,2));
        assertThat(created).isEqualTo(TopupCoordinator.Begin.CREATED);
        assertThat(replay).isEqualTo(TopupCoordinator.Begin.REPLAY);
        TopupEligibilityPort.Snapshot drift=snapshot("O-SCOPE",new BigDecimal("9.2501"));
        TopupCoordinator.Begin conflict=tx.execute(ignored->topups.beginAndReserve(command,drift,2));
        assertThat(conflict).isEqualTo(TopupCoordinator.Begin.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT reserved_count FROM hz_provider_limit WHERE limit_ref='L1'",Long.class)).isEqualTo(1);
    }

    @Test void concurrentSameOrderUniqueCollisionConvergesToCreatedAndReplay() throws Exception {
        createTopupTables();
        limit("L1", "WINLA", "CH-1", "GP", "BDT", 3, 10_000, 10_000, true);
        jdbc.update("INSERT INTO hz_provider_balance_position VALUES('WINLA','BDT',10000,0,0,CURRENT_TIMESTAMP,DATEADD('MINUTE',15,CURRENT_TIMESTAMP),'E',CURRENT_TIMESTAMP)");
        TopupProviderPort.Command command=command("O-RACE","REQ-RACE",1000);
        TopupEligibilityPort.Snapshot snapshot=snapshot("O-RACE",new BigDecimal("9.2500"));
        List<TopupCoordinator.Begin> outcomes=new CopyOnWriteArrayList<>();
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<?> one=pool.submit(()->beginInTransaction(command,snapshot,ready,go,outcomes));
            Future<?> two=pool.submit(()->beginInTransaction(command,snapshot,ready,go,outcomes));
            assertThat(ready.await(2,TimeUnit.SECONDS)).isTrue();go.countDown();one.get(5,TimeUnit.SECONDS);two.get(5,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        assertThat(outcomes).containsExactlyInAnyOrder(TopupCoordinator.Begin.CREATED,TopupCoordinator.Begin.REPLAY);
        assertThat(jdbc.queryForObject("SELECT reserved_count FROM hz_provider_limit WHERE limit_ref='L1'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT active_reserved_minor FROM hz_provider_balance_position",Long.class)).isEqualTo(1000);
    }

    private void beginInTransaction(TopupProviderPort.Command command,TopupEligibilityPort.Snapshot snapshot,CountDownLatch ready,CountDownLatch go,List<TopupCoordinator.Begin> outcomes){ready.countDown();await(go);JdbcTemplate otherJdbc=new JdbcTemplate(dataSource);JdbcProviderExposureStore otherExposure=new JdbcProviderExposureStore(otherJdbc);JdbcTopupStore other=new JdbcTopupStore(otherJdbc,null,null,otherExposure);outcomes.add(new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(ignored->other.beginAndReserve(command,snapshot,2)));}
    private static TopupProviderPort.Command command(String order,String request,long amount){return new TopupProviderPort.Command(order,request,"SKU","8801712345678",amount,"BDT","a".repeat(64));}
    private static TopupEligibilityPort.Snapshot snapshot(String order,BigDecimal cost){return new TopupEligibilityPort.Snapshot(order,"BUYER","SKU","8801712345678",1000,"BDT","b".repeat(64),"CH-1","GP",cost);}

    private void reserveInTransaction(JdbcProviderExposureStore target,String order,CountDownLatch ready,CountDownLatch go,AtomicInteger accepted,AtomicInteger rejected) {
        ready.countDown(); await(go);
        try { new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(ignored -> target.reserve(order,"WINLA","CH-1","GP","BDT",1000)); accepted.incrementAndGet(); }
        catch (TopupCoordinator.Conflict conflict) { rejected.incrementAndGet(); }
    }

    private void limit(String ref,String provider,String channel,String operator,String currency,long count,long total,long single,boolean enabled) {
        jdbc.update("INSERT INTO hz_provider_limit VALUES(?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", ref,provider,channel,operator,currency,count,total,single,enabled,1,0,0);
    }

    private void createTopupTables() {
        jdbc.execute("CREATE TABLE hz_provider_balance_position(provider_code VARCHAR(32),currency VARCHAR(3),confirmed_balance_minor DECIMAL(20),safety_buffer_minor DECIMAL(20),active_reserved_minor DECIMAL(20),observed_at TIMESTAMP,expires_at TIMESTAMP,evidence_ref VARCHAR(128),updated_at TIMESTAMP,PRIMARY KEY(provider_code,currency))");
        jdbc.execute("CREATE TABLE hz_provider_balance_reservation(reservation_ref VARCHAR(64) PRIMARY KEY,merchant_order_ref VARCHAR(64) UNIQUE,provider_code VARCHAR(32),amount_minor BIGINT,currency VARCHAR(3),reservation_state VARCHAR(24),aggregate_version BIGINT DEFAULT 1,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_provider_balance_ledger(entry_ref VARCHAR(64) PRIMARY KEY,merchant_order_ref VARCHAR(64),entry_type VARCHAR(32),amount_minor BIGINT,currency VARCHAR(3),occurred_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE hz_topup_coordination(merchant_order_ref VARCHAR(64) PRIMARY KEY,request_ref VARCHAR(64) UNIQUE,request_digest VARCHAR(64) UNIQUE,buyer_subject_ref VARCHAR(128),provider_sku VARCHAR(128),recipient VARCHAR(32),entitlement_digest VARCHAR(64),provider_code VARCHAR(32),provider_ref VARCHAR(128),submit_latched BOOLEAN,unknown_queries_remaining INT,state_code VARCHAR(32),evidence_ref VARCHAR(128),reserved_minor BIGINT,reserve_currency VARCHAR(3),aggregate_version BIGINT,updated_at TIMESTAMP,scope_provider_code VARCHAR(32),scope_channel_ref VARCHAR(64),scope_operator_code VARCHAR(64),supplier_cost DECIMAL(19,4))");
    }

    private static void await(CountDownLatch latch) { try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }

    private static final class FailingEvents implements BusinessEventStore {
        public void append(String orderRef, BusinessEventLinker.Type type, String eventRef, String digest, Instant occurredAt) { throw new IllegalStateException("INJECTED_EVENT_FAILURE"); }
        public void bindSupportCase(String caseRef,String orderRef) {}
        public int dispatchBatch(String owner,int limit,Instant now,Duration lease) { return 0; }
    }
}
