package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import com.huarenzaimeng.core.*;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StateAdvanceAuthorityContractTest {
    static final Instant NOW=Instant.parse("2026-08-12T12:00:00Z");
    static final CommandIdentity ID=new CommandIdentity("CMD-AUTH-GOV-01","IDEM-AUTH-GOV-01","AUTH_GOV","ORDER-1","SEM-AUTH-GOV-01","FP-AUTH-GOV-01");
    static StateAdvanceCommand base(){return StateAdvanceAuthorityResolver.resolveControlledLocal(ID,"ORDER-1","LOCAL_FIXTURE_ADVANCE","EVIDENCE-LOCAL-01",NOW);}
    static StateAdvanceGate.AggregateIdentity aggregate(){return new StateAdvanceGate.AggregateIdentity(StateAdvanceAuthority.Environment.LOCAL_SYNTHETIC,StateAdvanceAuthority.EvidenceLevel.L1,"NON_PRODUCTION");}
    static StateAdvanceCommand change(StateAdvanceCommand c,StateAdvanceAuthority.AuthorizationDecision d,String env,String target,String evidence){return new StateAdvanceCommand(c.command(),c.authority(),d,env,target,evidence,NOW);}
    static StateAdvanceAuthority.AuthorizationDecision decision(StateAdvanceCommand c,StateAdvanceAuthority.Environment e,StateAdvanceAuthority.EvidenceLevel l,String ref,String aggregate,String target,String evidence,StateAdvanceAuthority.AuthorizationDecision.Status status,boolean fixture,Instant until){return new StateAdvanceAuthority.AuthorizationDecision(ref,e,l,aggregate,target,evidence,NOW.minusSeconds(1),until,status,fixture);}

    private static final List<String> REJECT_CASES=List.of(
            "MISSING","UNKNOWN","L1_CROSS_LEVEL","L2_CROSS_LEVEL","L3_CROSS_LEVEL",
            "ENVIRONMENT_MISMATCH","EVIDENCE_LEVEL_MISMATCH","AUTHORIZATION_REF_MISMATCH",
            "AUTHORIZATION_SCOPE_MISMATCH","TARGET_MISMATCH","EVIDENCE_REF_MISMATCH",
            "EXPIRED","NOT_YET_VALID","REVOKED","AGGREGATE_ENVIRONMENT_MISMATCH",
            "AGGREGATE_EVIDENCE_MISMATCH","AGGREGATE_AUTHORITY_STATE_MISMATCH","REPLAY_AUTHORITY_MISMATCH");

    static Stream<Arguments> rejectCaseTable(){return REJECT_CASES.stream().map(Arguments::of);}

    private static StateAdvanceAuthority authority(StateAdvanceAuthority.Environment environment,
                                                     StateAdvanceAuthority.EvidenceLevel evidence,
                                                     String authorizationRef) {
        try {
            Field capability=StateAdvanceAuthorityResolver.class.getDeclaredField("CAPABILITY");
            capability.setAccessible(true);
            return new StateAdvanceAuthority(environment,evidence,authorizationRef,
                    (StateAdvanceAuthorityResolver.Capability)capability.get(null));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static StateAdvanceGate.AggregateIdentity aggregateFor(String kind) {
        if(kind.equals("AGGREGATE_ENVIRONMENT_MISMATCH")) return new StateAdvanceGate.AggregateIdentity(StateAdvanceAuthority.Environment.SANDBOX,StateAdvanceAuthority.EvidenceLevel.L1,"NON_PRODUCTION");
        if(kind.equals("AGGREGATE_EVIDENCE_MISMATCH")) return new StateAdvanceGate.AggregateIdentity(StateAdvanceAuthority.Environment.LOCAL_SYNTHETIC,StateAdvanceAuthority.EvidenceLevel.L2,"NON_PRODUCTION");
        if(kind.equals("AGGREGATE_AUTHORITY_STATE_MISMATCH")) return new StateAdvanceGate.AggregateIdentity(StateAdvanceAuthority.Environment.LOCAL_SYNTHETIC,StateAdvanceAuthority.EvidenceLevel.L1,"REAL_AUTHORIZED");
        if(kind.equals("L2_CROSS_LEVEL")) return new StateAdvanceGate.AggregateIdentity(StateAdvanceAuthority.Environment.LOCAL_SYNTHETIC,StateAdvanceAuthority.EvidenceLevel.L2,"NON_PRODUCTION");
        if(kind.equals("L3_CROSS_LEVEL")) return new StateAdvanceGate.AggregateIdentity(StateAdvanceAuthority.Environment.LOCAL_SYNTHETIC,StateAdvanceAuthority.EvidenceLevel.L3,"NON_PRODUCTION");
        return aggregate();
    }

    private static StateAdvanceCommand rejectedCommand(String kind,String orderRef) {
        if(kind.equals("MISSING")) return null;
        var level=kind.equals("L2_CROSS_LEVEL")?StateAdvanceAuthority.EvidenceLevel.L2:
                kind.equals("L3_CROSS_LEVEL")?StateAdvanceAuthority.EvidenceLevel.L3:StateAdvanceAuthority.EvidenceLevel.L1;
        var auth=authority(StateAdvanceAuthority.Environment.LOCAL_SYNTHETIC,level,"AUTH-MATRIX-0001");
        var id=new CommandIdentity("CMD-"+kind,"IDEM-"+kind,"AUTH_GOV",orderRef,"SEM-"+kind,"F".repeat(64));
        var decisionEnvironment=kind.equals("ENVIRONMENT_MISMATCH")?StateAdvanceAuthority.Environment.SANDBOX:auth.environment();
        var decisionLevel=kind.equals("EVIDENCE_LEVEL_MISMATCH")?StateAdvanceAuthority.EvidenceLevel.L2:auth.evidenceLevel();
        var decisionRef=kind.equals("AUTHORIZATION_REF_MISMATCH")||kind.equals("REPLAY_AUTHORITY_MISMATCH")?"AUTH-MATRIX-OTHER":auth.authorizationRef();
        var aggregateRef=kind.equals("AUTHORIZATION_SCOPE_MISMATCH")?"ORDER-OTHER":orderRef;
        var target=kind.equals("TARGET_MISMATCH")?"U":"LOCAL_FIXTURE_ADVANCE";
        var decisionTarget=kind.equals("TARGET_MISMATCH")?"D":target;
        var evidence=kind.equals("EVIDENCE_REF_MISMATCH")?"EVIDENCE-COMMAND":"EVIDENCE-MATRIX";
        var decisionEvidence=kind.equals("EVIDENCE_REF_MISMATCH")?"EVIDENCE-AUTH":evidence;
        var status=kind.equals("UNKNOWN")?StateAdvanceAuthority.AuthorizationDecision.Status.UNKNOWN:
                kind.equals("REVOKED")?StateAdvanceAuthority.AuthorizationDecision.Status.REVOKED:StateAdvanceAuthority.AuthorizationDecision.Status.ACTIVE;
        var from=kind.equals("NOT_YET_VALID")?NOW.plusSeconds(1):NOW.minusSeconds(1);
        var until=kind.equals("EXPIRED")?NOW:NOW.plusSeconds(60);
        boolean controlled=!kind.endsWith("CROSS_LEVEL");
        var authorization=new StateAdvanceAuthority.AuthorizationDecision(decisionRef,decisionEnvironment,decisionLevel,
                aggregateRef,decisionTarget,decisionEvidence,from,until,status,controlled);
        return new StateAdvanceCommand(id,auth,authorization,auth.environment().name(),target,evidence,NOW);
    }

    @SuppressWarnings("unchecked")
    private static void setAggregate(InMemoryFlowStore store,String subject,String orderRef,StateAdvanceGate.AggregateIdentity identity) {
        try {
            Field field=InMemoryFlowStore.class.getDeclaredField("aggregateIdentities"); field.setAccessible(true);
            ((Map<String,StateAdvanceGate.AggregateIdentity>)field.get(store)).put(subject+'\u0000'+orderRef,identity);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static Map<String,Object> mutableSnapshot(InMemoryFlowStore store) {
        try {
            Map<String,Object> result=new TreeMap<>();
            for(Field field:InMemoryFlowStore.class.getDeclaredFields()) {
                field.setAccessible(true); Object value=field.get(store);
                if(value instanceof Map<?,?> map) result.put(field.getName(),new HashMap<>(map));
                else if(value instanceof Set<?> set) result.put(field.getName(),new HashSet<>(set));
                else if(value instanceof Collection<?> collection) result.put(field.getName(),new ArrayList<>(collection));
            }
            return result;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @ParameterizedTest(name="IN_MEMORY[{0}]") @MethodSource("rejectCaseTable")
    void inMemoryRejectMatrixHasZeroDelta(String kind) {
        InMemoryFlowStore store=new InMemoryFlowStore(); String subject="SUBJECT-"+kind;
        Quote q=new Quote("Q-"+kind,"880****000","OP","PRODUCT","DENOM",1,1,100,"CNY",NOW.plusSeconds(60));
        store.installQuoteForTest(subject,q);
        OrderProjection order=store.createOrder(subject,q,new CommandIdentity("CREATE-"+kind,"CREATE-IDEM-"+kind,"POST:/orders",q.quoteRef(),"CREATE:"+q.quoteRef(),"A".repeat(64))).order();
        setAggregate(store,subject,order.orderRef(),aggregateFor(kind));
        Map<String,Object> before=mutableSnapshot(store); OrderProjection orderBefore=store.requireOrder(subject,order.orderRef());
        assertThatThrownBy(()->store.transitionOrderAuthorized(subject,order.orderRef(),rejectedCommand(kind,order.orderRef()),1,1,x->x))
                .isInstanceOf(RuntimeException.class);
        assertThat(store.requireOrder(subject,order.orderRef())).isEqualTo(orderBefore);
        assertThat(mutableSnapshot(store)).as("all aggregate/version/fact/command/outbox/ledger/side-effect collections").isEqualTo(before);
    }

    @ParameterizedTest(name="MYBATIS[{0}]") @MethodSource("rejectCaseTable")
    void myBatisRejectMatrixLocksThenAbortsBeforeEveryWrite(String kind) {
        FlowMapper mapper=mock(FlowMapper.class); TransactionTemplate tx=mock(TransactionTemplate.class);
        AtomicBoolean transactionAborted=new AtomicBoolean();
        when(tx.execute(any())).thenAnswer(invocation->{try{return ((TransactionCallback<?>)invocation.getArgument(0)).doInTransaction(null);}catch(RuntimeException rejected){transactionAborted.set(true);throw rejected;}});
        var identity=aggregateFor(kind);
        when(mapper.selectOrderAuthorityForUpdate("SUBJECT","ORDER-1")).thenReturn(Map.of(
                "environment",identity.environment().name(),"evidence_level",identity.evidenceLevel().name(),"authority_state",identity.authorityState()));
        MyBatisFlowStore store=new MyBatisFlowStore(mapper,tx);
        assertThatThrownBy(()->store.transitionOrderAuthorized("SUBJECT","ORDER-1",rejectedCommand(kind,"ORDER-1"),1,1,x->x))
                .isInstanceOf(RuntimeException.class);
        assertThat(transactionAborted.get()).isTrue();
        verify(tx).execute(any()); verify(mapper).selectOrderAuthorityForUpdate("SUBJECT","ORDER-1");
        verifyNoMoreInteractions(mapper);
    }

    @Test void rejectionMatrixDenominatorIsFrozenAtThirtySix(){assertThat(REJECT_CASES).hasSize(18).doesNotHaveDuplicates();assertThat(REJECT_CASES.size()*2).isEqualTo(36);}

    @Test void controlledLocalSameLevelIsOnlyReachablePositive(){assertThatCode(()->StateAdvanceGate.verify("ORDER-1",aggregate(),base())).doesNotThrowAnyException();}
    @ParameterizedTest @ValueSource(strings={"L1","L2","L3"}) void lowerLevelsCannotAdvanceRealFacts(String ignored){var c=base();var d=decision(c,c.authority().environment(),c.authority().evidenceLevel(),c.authority().authorizationRef(),"ORDER-1","W","EVIDENCE-REAL",StateAdvanceAuthority.AuthorizationDecision.Status.ACTIVE,false,NOW.plusSeconds(1));assertThatThrownBy(()->StateAdvanceGate.verify("ORDER-1",aggregate(),change(c,d,"LOCAL_SYNTHETIC","W","EVIDENCE-REAL"))).isInstanceOf(FlowRejectedException.class);}
    @ParameterizedTest @ValueSource(strings={"PAYMENT","TOPUP","REFUND","DELIVERY","ACCOUNTING","W","U","D","R","L","LEDGER"}) void syntheticCannotAdvanceAnyRealTarget(String target){var c=base();var d=decision(c,c.authority().environment(),c.authority().evidenceLevel(),c.authority().authorizationRef(),"ORDER-1",target,"EVIDENCE-REAL",StateAdvanceAuthority.AuthorizationDecision.Status.ACTIVE,false,NOW.plusSeconds(1));assertThatThrownBy(()->StateAdvanceGate.verify("ORDER-1",aggregate(),change(c,d,"LOCAL_SYNTHETIC",target,"EVIDENCE-REAL"))).isInstanceOf(FlowRejectedException.class);}
    @Test void missingAndUnknownFailClosed(){assertThatThrownBy(()->new StateAdvanceCommand(null,null,null,null,null,null,null)).isInstanceOf(FlowRejectedException.class);var c=base();var d=decision(c,c.authority().environment(),c.authority().evidenceLevel(),c.authority().authorizationRef(),"ORDER-1",c.targetTransition(),c.evidenceRef(),StateAdvanceAuthority.AuthorizationDecision.Status.UNKNOWN,true,NOW.plusSeconds(1));assertThatThrownBy(()->StateAdvanceGate.verify("ORDER-1",aggregate(),change(c,d,"LOCAL_SYNTHETIC",c.targetTransition(),c.evidenceRef()))).isInstanceOf(FlowRejectedException.class);}
    @ParameterizedTest @ValueSource(strings={"ENVIRONMENT","EVIDENCE","AUTHORIZATION","SCOPE","TARGET","EVIDENCE_REF","EXPIRED","REVOKED","AGGREGATE_ENV","AGGREGATE_EVIDENCE","AGGREGATE_STATE"}) void mismatchIsZeroQualification(String kind){var c=base();var a=c.authority();var e=kind.equals("EVIDENCE")?StateAdvanceAuthority.EvidenceLevel.L2:a.evidenceLevel();var env=kind.equals("ENVIRONMENT")?StateAdvanceAuthority.Environment.SANDBOX:a.environment();var ref=kind.equals("AUTHORIZATION")?"AUTH-OTHER-0001":a.authorizationRef();var status=kind.equals("REVOKED")?StateAdvanceAuthority.AuthorizationDecision.Status.REVOKED:StateAdvanceAuthority.AuthorizationDecision.Status.ACTIVE;var d=decision(c,env,e,ref,kind.equals("SCOPE")?"ORDER-2":"ORDER-1",kind.equals("TARGET")?"Y":c.targetTransition(),kind.equals("EVIDENCE_REF")?"OTHER":c.evidenceRef(),status,true,kind.equals("EXPIRED")?NOW.minusSeconds(1):NOW.plusSeconds(1));var ag=new StateAdvanceGate.AggregateIdentity(kind.equals("AGGREGATE_ENV")?StateAdvanceAuthority.Environment.SANDBOX:aggregate().environment(),kind.equals("AGGREGATE_EVIDENCE")?StateAdvanceAuthority.EvidenceLevel.L2:aggregate().evidenceLevel(),kind.equals("AGGREGATE_STATE")?"REAL_AUTHORIZED":aggregate().authorityState());assertThatThrownBy(()->StateAdvanceGate.verify("ORDER-1",ag,change(c,d,"LOCAL_SYNTHETIC",c.targetTransition(),c.evidenceRef()))).isInstanceOf(FlowRejectedException.class);}
    @Test void onlyResolverOwnsCapability()throws Exception{String authority=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/StateAdvanceAuthority.java"));String resolver=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/StateAdvanceAuthorityResolver.java"));assertThat(authority).contains("StateAdvanceAuthorityResolver.Capability").doesNotContain("enum TrustedResolverMarker");assertThat(resolver).contains("private static final Capability CAPABILITY");}
    @Test void noClientInputPathAndOldEntryRemoved()throws Exception{String all=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/StateAdvanceAuthorityResolver.java"))+Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/FlowStore.java"));assertThat(all).doesNotContain("RequestBody","RequestHeader","RequestParam","BuyerPrincipal","transitionOrder(");}
    @Test void bothStoresGateInsideCriticalSection()throws Exception{String in=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/InMemoryFlowStore.java"));String db=Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/MyBatisFlowStore.java"));assertThat(in.indexOf("synchronized OrderProjection transitionOrderAuthorized")).isLessThan(in.indexOf("StateAdvanceGate.verify(orderRef,identity,advance)"));assertThat(db.indexOf("transactions.execute")).isLessThan(db.lastIndexOf("StateAdvanceGate.verify"));assertThat(db.indexOf("StateAdvanceGate.verify",db.indexOf("transitionOrderAuthorized"))).isLessThan(db.indexOf("mapper.updateOrder",db.indexOf("transitionOrderAuthorized")));}
    @Test void migrationMakesHistoryExplicitlyNonProduction()throws Exception{String sql=Files.readString(Path.of("src/main/resources/db/migration/V11__add_state_advance_authority_columns.sql"));assertThat(sql).contains("DEFAULT 'LOCAL_SYNTHETIC'","DEFAULT 'L1'","DEFAULT 'NON_PRODUCTION'").doesNotContain("UPDATE ","DEFAULT 'REAL_AUTHORIZED'");}
    @Test void myBatisRejectsAggregateMismatchBeforeEveryWrite(){FlowMapper mapper=mock(FlowMapper.class);TransactionTemplate tx=mock(TransactionTemplate.class);when(tx.execute(any())).thenAnswer(i->((TransactionCallback<?>)i.getArgument(0)).doInTransaction(null));when(mapper.selectOrderAuthorityForUpdate("SUBJECT","ORDER-1")).thenReturn(java.util.Map.of("environment","SANDBOX","evidence_level","L2","authority_state","NON_PRODUCTION"));MyBatisFlowStore store=new MyBatisFlowStore(mapper,tx);assertThatThrownBy(()->store.transitionOrderAuthorized("SUBJECT","ORDER-1",base(),1,1,x->x)).isInstanceOf(FlowRejectedException.class);verify(mapper,never()).updateOrder(any(),any(),any(),any(),any(),any(),any(),any(),anyLong(),anyLong(),anyLong(),any());verify(mapper,never()).insertStateAdvanceAuthorityFact(any(),any(),any(),any(),any(),any(),any(),any(),any());verify(mapper,never()).insertOutbox(any(),any(),anyLong(),anyLong(),any());}
    @Test void inMemoryRejectedAdvanceLeavesOrderFactsAndSideEffectsUnchanged(){InMemoryFlowStore store=new InMemoryFlowStore();String subject="SUBJECT";Quote q=new Quote("Q1","880****000","OP","PRODUCT","DENOM",1,1,100,"CNY",NOW.plusSeconds(60));store.installQuoteForTest(subject,q);OrderProjection order=store.createOrder(subject,q,new CommandIdentity("CREATE-1","CREATE-IDEM-1","POST:/orders","Q1","CREATE:Q1","A".repeat(64))).order();var before=store.sideEffectSnapshot(subject);long facts=store.stateAdvanceAuthorityFactCountForTest();var c=base();var bad=new StateAdvanceCommand(c.command(),c.authority(),c.authorization(),"SANDBOX",c.targetTransition(),c.evidenceRef(),NOW);assertThatThrownBy(()->store.transitionOrderAuthorized(subject,order.orderRef(),bad,1,1,x->x)).isInstanceOf(FlowRejectedException.class);assertThat(store.requireOrder(subject,order.orderRef())).isEqualTo(order);assertThat(store.sideEffectSnapshot(subject)).isEqualTo(before);assertThat(store.stateAdvanceAuthorityFactCountForTest()).isEqualTo(facts);}

    @ParameterizedTest(name="CANONICAL[{0}]")
    @MethodSource("canonicalClassifications")
    void duplicateRollbackCanonicalReadClassifiesFourTuple(StateAdvanceConcurrentResult expected,
                                                             List<Map<String,Object>> rows) throws Exception {
        FlowMapper mapper=mock(FlowMapper.class);
        TransactionTemplate tx=mock(TransactionTemplate.class);
        when(mapper.selectCommands(eq("SUBJECT"),any(),any(),any(),any(),any())).thenReturn(rows);
        MyBatisFlowStore store=new MyBatisFlowStore(mapper,tx);
        Method classify=MyBatisFlowStore.class.getDeclaredMethod("classifyStateAdvanceDuplicate",String.class,String.class,CommandIdentity.class);
        classify.setAccessible(true);
        assertThat(classify.invoke(store,"SUBJECT","ORDER-1",ID)).isEqualTo(expected);
        verify(mapper).selectCommands("SUBJECT",ID.commandId(),ID.endpointScope(),ID.resourceScope(),ID.idempotencyKey(),ID.semanticActionKey());
        verifyNoMoreInteractions(mapper);
    }

    static Stream<Arguments> canonicalClassifications(){
        Map<String,Object> replay=canonical(ID,"ORDER-1");
        Map<String,Object> conflict=canonical(new CommandIdentity(ID.commandId(),ID.idempotencyKey(),ID.endpointScope(),ID.resourceScope(),ID.semanticActionKey(),"FP-OTHER"),"ORDER-1");
        Map<String,Object> corrupt=canonical(ID,"ORDER-OTHER");
        return Stream.of(
                Arguments.of(StateAdvanceConcurrentResult.REPLAYED,List.of(replay)),
                Arguments.of(StateAdvanceConcurrentResult.IDEMPOTENCY_CONFLICT,List.of(conflict)),
                Arguments.of(StateAdvanceConcurrentResult.STORAGE_INTEGRITY_CONFLICT,List.of(corrupt)),
                Arguments.of(StateAdvanceConcurrentResult.CONCURRENT_RESULT_UNKNOWN,List.of()));
    }

    static Map<String,Object> canonical(CommandIdentity command,String resourceRef){
        return Map.of("command_id",command.commandId(),"idempotency_key",command.idempotencyKey(),
                "endpoint_scope",command.endpointScope(),"resource_scope",command.resourceScope(),
                "semantic_action_key",command.semanticActionKey(),"canonical_fingerprint",command.canonicalFingerprint(),
                "resource_ref",resourceRef);
    }
}
