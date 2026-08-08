package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static com.huarenzaimeng.api.P014TopupDomain.*;
import static org.assertj.core.api.Assertions.*;

class P014TopupApiContractTest {
    private static final LocalSyntheticIdentity ID = LocalSyntheticIdentity.fromToken("p014-unit-token");
    private P014TopupSideEffectProbe probe;
    private P014TopupService service;
    private Fixture fixture;

    @BeforeEach void setUp() { probe = new P014TopupSideEffectProbe(); service = new P014TopupService(probe); install(new F().build()); }

    @Test @DisplayName("BE05-P014-001-APPROVED-CREATE") void BE05_P014_001_APPROVED_CREATE__P001_VALID() {
        var r=create("c1","i1"); assertThat(r.projectCode()).isEqualTo("TOPUP_INTENT_CREATED");
        assertThat(r.currentProjection().stateCode()).isEqualTo("TOPUP_PROCESSING"); assertCreateDelta(); assertStrict(r);
        assertThat(r.currentProjection().factTimeline()).extracting(P014TopupDomain.Fact::factCode)
                .containsExactly("PAYMENT","UPSTREAM_DEBIT","DELIVERY","ACCOUNTING_CLOSURE");
        assertThat(r.currentProjection().allowedActions()).extracting(P014TopupDomain.AllowedAction::actionCode)
                .containsExactly("QUERY_ORIGINAL_TOPUP","REFRESH_ORDER_PROJECTION","OPEN_SUPPORT","SAFE_LEAVE");
    }

    @ParameterizedTest @DisplayName("BE05-P014-002-EXACT-REPLAY") @ValueSource(strings={"P001-UNCHANGED","P002-DECISION-ROTATED","P003-CATALOG-DRIFTED"})
    void BE05_P014_002_EXACT_REPLAY(String parameter) {
        var first=create("c1","i1");
        if (parameter.contains("DECISION-ROTATED")) replace(new F().versions(2).decisionVersions("PAY-V2","MNP-V2").build());
        if (parameter.contains("CATALOG-DRIFTED")) replace(new F().versions(2).catalog("CAT-V2",false).build());
        var before=counts(); var replay=create("c1","i1");
        assertThat(replay.projectCode()).isEqualTo("TOPUP_INTENT_REPLAYED");
        assertThat(replay.resourceRef()).isEqualTo(first.resourceRef());
        assertThat(replay.currentProjection().priceSnapshotSummary()).isEqualTo(first.currentProjection().priceSnapshotSummary());
        assertThat(counts()).isEqualTo(before); assertStrict(replay);
    }

    @ParameterizedTest @DisplayName("BE05-P014-003-COMMAND-KEY-CONFLICT") @ValueSource(strings={"P001-SAME-COMMAND-NEW-IDEMPOTENCY","P002-SAME-COMMAND-DIFFERENT-REQUEST"})
    void BE05_P014_003_COMMAND_KEY_CONFLICT(String parameter) {
        create("c1","i1"); var before=counts();
        CreateRequest q=parameter.endsWith("REQUEST")?request("c1","i1",99,1):request("c1","i2",1,1);
        assertThat(call(q).projectCode()).isEqualTo("IDEMPOTENCY_CONFLICT"); assertThat(counts()).isEqualTo(before);
    }

    @ParameterizedTest @DisplayName("BE05-P014-004-IDEMPOTENCY-KEY-CONFLICT") @ValueSource(strings={"P001-SAME-IDEMPOTENCY-NEW-COMMAND","P002-SAME-IDEMPOTENCY-DIFFERENT-REQUEST"})
    void BE05_P014_004_IDEMPOTENCY_KEY_CONFLICT(String parameter) {
        create("c1","i1"); var before=counts();
        CreateRequest q=parameter.endsWith("REQUEST")?request("c1","i1",99,1):request("c2","i1",1,1);
        assertThat(call(q).projectCode()).isEqualTo("IDEMPOTENCY_CONFLICT"); assertThat(counts()).isEqualTo(before);
    }

    @ParameterizedTest @DisplayName("BE05-P014-005-SAME-SEMANTIC-KEY-SWITCH") @ValueSource(strings={"P001-NEW-DOUBLE-KEY-SAME-FINGERPRINT","P002-NEW-DOUBLE-KEY-DIFFERENT-FINGERPRINT"})
    void BE05_P014_005_SAME_SEMANTIC_KEY_SWITCH(String parameter) {
        create("c1","i1"); var before=counts();
        var q=request("c2","i2",parameter.contains("DIFFERENT")?99:1,1);
        assertThat(call(q).projectCode()).isEqualTo("IDEMPOTENCY_CONFLICT"); assertThat(counts()).isEqualTo(before);
    }

    @ParameterizedTest @DisplayName("BE05-P014-006-B1-MNP-UNKNOWN-ZERO") @ValueSource(strings={"P001-MNP-UNKNOWN","P002-MNP-UNAVAILABLE"})
    void BE05_P014_006_B1_MNP_UNKNOWN_ZERO(String mnp) {
        install(new F().mnp(mnp.endsWith("UNAVAILABLE")?"UNAVAILABLE":"UNKNOWN").build()); assertRejectedZero("TOPUP_QUALIFICATION_UNKNOWN");
        var p=create("x","y").currentProjection();
        assertThat(p.allowedActions()).extracting(P014TopupDomain.AllowedAction::actionCode)
                .containsExactly("REFRESH_ORDER_PROJECTION","OPEN_SUPPORT","SAFE_LEAVE");
        assertThat(p.progressSummary().supportRef()).isNull();
    }

    @ParameterizedTest @DisplayName("BE05-P014-007-PAYMENT-CONFIRMATION-UNKNOWN-ZERO") @ValueSource(strings={"P001-PAYMENT-UNKNOWN","P002-PAYMENT-CONFLICT"})
    void BE05_P014_007_PAYMENT_CONFIRMATION_UNKNOWN_ZERO(String payment) {
        install(new F().payment(payment.endsWith("CONFLICT")?"CONFLICT":"UNKNOWN").build()); var before=counts(); var r=create("c","i");
        assertThat(r.projectCode()).isEqualTo("TOPUP_QUALIFICATION_UNKNOWN");
        assertThat(r.currentProjection().progressSummary().userMessageCode()).isEqualTo("PAYMENT_CONFIRMATION_CHECKING_NO_AUTO_TOPUP");
        assertThat(r.currentProjection().allowedActions()).extracting(P014TopupDomain.AllowedAction::actionCode)
                .containsExactly("REFRESH_ORDER_PROJECTION","OPEN_SUPPORT","SAFE_LEAVE");
        assertThat(r.currentProjection().progressSummary().supportRef()).isNull();
        assertThat(counts()).isEqualTo(before);
    }

    @ParameterizedTest @DisplayName("BE05-P014-008-AUTH-OR-EXISTENCE-SAME-SHAPE") @ValueSource(strings={"P001-UNAUTHENTICATED","P002-REVOKED","P003-CROSS-SUBJECT","P004-NOT-FOUND"})
    void BE05_P014_008_AUTH_OR_EXISTENCE_SAME_SHAPE(String parameter) {
        if (parameter.endsWith("REVOKED")) service.revokeForTest(fixture.sessionRef());
        String env=parameter.endsWith("UNAUTHENTICATED")?null:ENVIRONMENT;
        String subject=parameter.endsWith("CROSS-SUBJECT")?"OTHER":fixture.projectSubjectRef();
        String order=parameter.endsWith("NOT-FOUND")?"MISSING":fixture.orderRef();
        var before=counts();
        var post=service.create(env,subject,fixture.sessionRef(),order,request("c","i",1,1));
        var get=service.projection(env,subject,fixture.sessionRef(),order);
        var result=service.result(env,subject,fixture.sessionRef(),order,"c","i",1L,fixture.authorizationSetRef(),false);
        assertThat(post.projectCode()).isEqualTo("TOPUP_NOT_AVAILABLE");
        assertThat(get.projectCode()).isEqualTo("TOPUP_PROGRESS_NOT_AVAILABLE");
        assertThat(result.projectCode()).isEqualTo("TOPUP_INTENT_QUERY_NOT_AVAILABLE");
        assertThat(post.resourceRef()).isNull(); assertThat(get.currentProjection()).isNull(); assertThat(result.currentProjection()).isNull();
        assertNonQueryUnchanged(before);
    }

    @ParameterizedTest @DisplayName("BE05-P014-009-PRICE-OR-VERSION-DRIFT-ZERO") @ValueSource(strings={"P001-PRICE-DIGEST","P002-ORDER-VERSION","P003-PROJECTION-VERSION","P004-AUTH-EVIDENCE-VERSION"})
    void BE05_P014_009_PRICE_OR_VERSION_DRIFT_ZERO(String parameter) {
        if(parameter.endsWith("PRICE-DIGEST")) install(new F().priceDigest("WRONG-DIGEST").build());
        if(parameter.endsWith("AUTH-EVIDENCE-VERSION")) service.alterAuthorizationEvidenceForTest(fixture.sessionRef(),"AUTH-EVIDENCE-V2");
        long pv=parameter.endsWith("PROJECTION-VERSION")?2:1, av=parameter.endsWith("ORDER-VERSION")?2:1;
        var before=counts(); var r=call(request("c","i",pv,av));
        assertThat(r.projectCode()).isIn("VERSION_CONFLICT","TOPUP_NOT_AVAILABLE","TOPUP_QUALIFICATION_UNKNOWN");
        assertThat(counts()).isEqualTo(before);
    }

    @ParameterizedTest @DisplayName("BE05-P014-010-CONCURRENT-UNIQUE-CREATE") @ValueSource(strings={"P001-TWO-DISTINCT-DOUBLE-KEY-COMPETITORS","P002-N-DISTINCT-DOUBLE-KEY-COMPETITORS"})
    void BE05_P014_010_CONCURRENT_UNIQUE_CREATE(String parameter) throws Exception {
        int workers=parameter.startsWith("P001")?2:8;
        ExecutorService pool=Executors.newFixedThreadPool(workers); CountDownLatch ready=new CountDownLatch(workers), start=new CountDownLatch(1);
        List<Future<Response>> futures=new ArrayList<>();
        for(int i=0;i<workers;i++){ final int n=i; futures.add(pool.submit(()->{ready.countDown();start.await();return create("c"+n,"i"+n);})); }
        ready.await(); start.countDown(); List<Response> responses=new ArrayList<>(); for(var f:futures) responses.add(f.get()); pool.shutdown();
        assertThat(responses).filteredOn(r->r.projectCode().equals("TOPUP_INTENT_CREATED")).hasSize(1);
        assertThat(responses).filteredOn(r->r.projectCode().equals("IDEMPOTENCY_CONFLICT")).hasSize(workers-1);
        assertThat(counts().get("TopupIntent")).isEqualTo(1); assertThat(counts().get("DispatchIntent")).isEqualTo(1);
    }

    @ParameterizedTest @DisplayName("BE05-P014-011-U-CONFIRMED-D-UNKNOWN") @ValueSource(strings={"P001-D-UNKNOWN-WITHIN-WINDOW","P002-D-UNKNOWN-LONG-RUNNING","P003-D-NOT-OBSERVED-AGE-UNKNOWN","P004-D-ABSENT-CONFIRMED"})
    void BE05_P014_011_U_CONFIRMED_D_UNKNOWN(String parameter) {
        create("c","i"); F f=new F().versions(2).u("CONFIRMED");
        if(parameter.contains("LONG")) f.age(UnknownAgeDecision.LONG_RUNNING);
        else if(parameter.contains("AGE-UNKNOWN")) f.d("NOT_OBSERVED").age(UnknownAgeDecision.UNKNOWN);
        else if(parameter.contains("ABSENT")) f.d("ABSENT_CONFIRMED"); else f.d("UNKNOWN");
        replace(f.build()); var r=projection();
        assertThat(r.currentProjection().stateCode()).isEqualTo(parameter.contains("LONG")||parameter.contains("AGE-UNKNOWN")?"TOPUP_RESULT_UNKNOWN":parameter.contains("ABSENT")?"CONFIRMED_NOT_DELIVERED":"TOPUP_PROCESSING");
        assertStrict(r);
    }

    @ParameterizedTest @DisplayName("BE05-P014-012-D-CONFIRMED-U-UNKNOWN") @ValueSource(strings={"P001-U-UNKNOWN","P002-ACCOUNTING-UNKNOWN","P003-U-AND-ACCOUNTING-UNKNOWN"})
    void BE05_P014_012_D_CONFIRMED_U_UNKNOWN(String parameter) {
        create("c","i"); F f=new F().versions(2).d("CONFIRMED").u(parameter.endsWith("ACCOUNTING-UNKNOWN")&&!parameter.contains("U-AND")?"CONFIRMED":"UNKNOWN")
                .l(parameter.endsWith("U-UNKNOWN")?"CONFIRMED":"UNKNOWN"); replace(f.build());
        assertThat(projection().currentProjection().progressSummary().userMessageCode()).isEqualTo("DELIVERY_EVIDENCE_UNDER_REVIEW");
    }

    @ParameterizedTest @DisplayName("BE05-P014-013-U-D-BOTH-UNKNOWN") @ValueSource(strings={"P001-WITHIN-WINDOW","P002-LONG-RUNNING","P003-AGE-UNKNOWN"})
    void BE05_P014_013_U_D_BOTH_UNKNOWN(String parameter) {
        create("c","i"); UnknownAgeDecision age=parameter.endsWith("WITHIN-WINDOW")?UnknownAgeDecision.WITHIN_LOCAL_WINDOW:parameter.endsWith("LONG-RUNNING")?UnknownAgeDecision.LONG_RUNNING:UnknownAgeDecision.UNKNOWN;
        replace(new F().versions(2).u("UNKNOWN").d("UNKNOWN").l("UNKNOWN").age(age).build()); var p=projection().currentProjection();
        if(age==UnknownAgeDecision.WITHIN_LOCAL_WINDOW) assertThat(p.stateCode()).isEqualTo("TOPUP_PROCESSING");
        else { assertThat(p.stateCode()).isEqualTo("TOPUP_RESULT_UNKNOWN"); assertThat(p.progressSummary().supportRef()).isNotBlank();
            assertThat(p.allowedActions()).extracting(P014TopupDomain.AllowedAction::actionCode).containsExactly("QUERY_ORIGINAL_TOPUP","OPEN_SUPPORT","SAFE_LEAVE"); }
    }

    @Test @DisplayName("BE05-P014-014-U-D-ACCOUNTING-CONFIRMED") void BE05_P014_014_U_D_ACCOUNTING_CONFIRMED() {
        create("c","i"); replace(new F().versions(2).u("CONFIRMED").d("CONFIRMED").l("CONFIRMED").build());
        var p=projection().currentProjection(); assertThat(p.stateCode()).isEqualTo("DELIVERED");
        assertThat(p.progressSummary().confirmedItems()).containsExactly("PAYMENT","UPSTREAM_DEBIT","DELIVERY","ACCOUNTING_CLOSURE");
    }

    @ParameterizedTest @DisplayName("BE05-P014-015-DUPLICATE-OBSERVATION-ZERO") @ValueSource(strings={"P001-U-DUPLICATE","P002-D-DUPLICATE","P003-ACCOUNTING-DUPLICATE"})
    void BE05_P014_015_DUPLICATE_OBSERVATION_ZERO(String parameter) {
        create("c","i"); replace(new F().versions(2).u("CONFIRMED").d("CONFIRMED").l("CONFIRMED").build()); var one=projection(); var before=counts();
        service.applyObservationForTest(fixture.orderRef(),"CONFIRMED","CONFIRMED","CONFIRMED",UnknownAgeDecision.WITHIN_LOCAL_WINDOW,false);
        var two=projection(); assertThat(two.currentProjection()).isEqualTo(one.currentProjection()); assertNonQueryUnchanged(before);
    }

    @ParameterizedTest @DisplayName("BE05-P014-016-FACT-CONFLICT-REVIEW") @ValueSource(strings={"P001-U-CONFLICT","P002-D-CONFLICT","P003-ACCOUNTING-CONFLICT","P004-DUPLICATE-CANONICAL-IDENTITY"})
    void BE05_P014_016_FACT_CONFLICT_REVIEW(String parameter) {
        create("c","i"); F f=new F().versions(2);
        if(parameter.contains("U-CONFLICT"))f.u("CONFLICT"); else if(parameter.contains("D-CONFLICT"))f.d("CONFLICT"); else if(parameter.contains("ACCOUNTING-CONFLICT"))f.l("CONFLICT"); else f.duplicate(true);
        replace(f.build()); var p=projection().currentProjection(); assertThat(p.stateCode()).isEqualTo("SUPPORT_REVIEW");
        assertThat(p.progressSummary().userMessageCode()).isEqualTo("TOPUP_FACT_CONFLICT_UNDER_REVIEW");
    }

    @ParameterizedTest @DisplayName("BE05-P014-017-OUT-OF-ORDER-MONOTONIC") @ValueSource(strings={"P001-CONFIRMED-THEN-UNKNOWN","P002-D-LATE-BEFORE-U","P003-CONFLICT-AFTER-CONFIRMED"})
    void BE05_P014_017_OUT_OF_ORDER_MONOTONIC(String parameter) {
        create("c","i");
        if(parameter.contains("D-LATE-BEFORE-U")) service.applyObservationForTest(fixture.orderRef(),"UNKNOWN","CONFIRMED","UNKNOWN",UnknownAgeDecision.WITHIN_LOCAL_WINDOW,false);
        else service.applyObservationForTest(fixture.orderRef(),"CONFIRMED","UNKNOWN","CONFIRMED",UnknownAgeDecision.WITHIN_LOCAL_WINDOW,false);
        projection();
        if(parameter.contains("CONFLICT-AFTER-CONFIRMED")) service.applyObservationForTest(fixture.orderRef(),"CONFLICT","UNKNOWN","CONFIRMED",UnknownAgeDecision.WITHIN_LOCAL_WINDOW,false);
        else service.applyObservationForTest(fixture.orderRef(),"UNKNOWN","UNKNOWN","UNKNOWN",UnknownAgeDecision.WITHIN_LOCAL_WINDOW,false);
        var p=projection().currentProjection();
        if(parameter.contains("D-LATE-BEFORE-U")) assertThat(p.stateCode()).isEqualTo("SUPPORT_REVIEW");
        else assertThat(p.upstreamDebitState()).isNotEqualTo("UNKNOWN");
        assertThat(counts().get("DispatchIntent")).isEqualTo(1);
    }

    @ParameterizedTest @DisplayName("BE05-P014-018-ORIGINAL-KEY-QUERY-UNKNOWN") @ValueSource(strings={"P001-NEXTPOLL-NULL","P002-NEXTPOLL-RFC3339"})
    void BE05_P014_018_ORIGINAL_KEY_QUERY_UNKNOWN(String parameter) {
        boolean nextPoll=parameter.endsWith("RFC3339");
        install(new F().result(ResultState.UNKNOWN).nextPoll(nextPoll?Instant.parse("2026-08-03T00:05:00Z"):null).build());
        assertThat(create("c","i").projectCode()).isEqualTo("TOPUP_INTENT_CREATE_UNKNOWN"); var before=counts(); var r=result("c","i");
        assertThat(r.projectCode()).isEqualTo("TOPUP_INTENT_RESULT_UNKNOWN"); assertThat(r.nextPollAt()).isEqualTo(nextPoll?Instant.parse("2026-08-03T00:05:00Z"):null); assertNonQueryUnchanged(before);
    }

    @ParameterizedTest @DisplayName("BE05-P014-019-LATE-RESULT-SAME-KEY-CONVERGENCE") @ValueSource(strings={"P001-UNKNOWN-TO-FOUND","P002-UNKNOWN-TO-REJECTED","P003-FOUND-NO-REGRESSION"})
    void BE05_P014_019_LATE_RESULT_SAME_KEY_CONVERGENCE(String parameter) {
        install(new F().result(ResultState.UNKNOWN).build()); create("c","i");
        assertThat(service.submissionStateForTest(fixture.orderRef())).isEqualTo("UNKNOWN");
        assertThat(service.canonicalMapEntryCountForTest()).isEqualTo(3);
        assertThat(result("c","i").outcome()).isEqualTo("UNKNOWN");
        var beforeConvergence=counts();
        ResultState terminal=parameter.endsWith("UNKNOWN-TO-REJECTED")?ResultState.REJECTED:ResultState.FOUND; service.convergeResultForTest(fixture.orderRef(),terminal);
        var r=result("c","i"); assertThat(r.projectCode()).isEqualTo(terminal==ResultState.FOUND?"TOPUP_INTENT_RESULT_FOUND":"TOPUP_INTENT_RESULT_REJECTED");
        assertThat(service.submissionStateForTest(fixture.orderRef())).isEqualTo(terminal==ResultState.FOUND?"COMMITTED":"NOT_COMMITTED");
        for(String name:List.of("Command","CommandAlias","TopupBusinessKey","TopupSemanticAction","TopupIntent",
                "DispatchSemanticAction","DispatchIntent","OrderVersion","ProjectionVersion")) {
            assertThat(counts().get(name)-beforeConvergence.get(name)).as(name)
                    .isEqualTo(terminal==ResultState.FOUND?1:0);
        }
        var afterTerminal=counts(); service.convergeResultForTest(fixture.orderRef(),terminal);
        assertThat(counts()).isEqualTo(afterTerminal);
        if(parameter.endsWith("FOUND-NO-REGRESSION")){
            service.convergeResultForTest(fixture.orderRef(),ResultState.UNKNOWN);
            service.convergeResultForTest(fixture.orderRef(),ResultState.REJECTED);
            service.convergeResultForTest(fixture.orderRef(),ResultState.UNKNOWN);
            assertThat(result("c","i").projectCode()).isEqualTo("TOPUP_INTENT_RESULT_FOUND");
            assertThat(service.resultReviewSignalCountForTest()).isEqualTo(1);
            assertNonQueryUnchanged(afterTerminal);
        }
    }

    @ParameterizedTest @DisplayName("BE05-P014-020-STRICT-DTO-ZERO-REAL-SIDE-EFFECT") @ValueSource(strings={"P001-MISSING","P002-ADDITIONAL","P003-UNKNOWN-ENUM","P004-ILLEGAL-NULL","P005-WRONG-TYPE","P006-CROSS-FIELD-MISMATCH"})
    void BE05_P014_020_STRICT_DTO_ZERO_REAL_SIDE_EFFECT(String parameter) throws Exception {
        var r=create("c","i"); var before=counts(); ObjectMapper mapper=new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertThat(service.responseProjectCodesForTest()).containsExactlyInAnyOrder("TOPUP_NOT_AVAILABLE",
                "IDEMPOTENCY_CONFLICT","VERSION_CONFLICT","TOPUP_QUALIFICATION_UNKNOWN","TOPUP_INTENT_CREATE_UNKNOWN",
                "TOPUP_INTENT_CREATED","TOPUP_INTENT_REPLAYED","TOPUP_INTENT_RESULT_FOUND",
                "TOPUP_INTENT_RESULT_REJECTED","TOPUP_INTENT_RESULT_UNKNOWN","TOPUP_INTENT_QUERY_NOT_AVAILABLE",
                "TOPUP_PROGRESS_NOT_AVAILABLE","TOPUP_PROGRESS_READ");
        ObjectNode tree=(ObjectNode)mapper.readTree(mapper.writeValueAsString(r)); assertThat(tree.size()).isEqualTo(8);
        boolean rejected=false;
        try {
            if(parameter.endsWith("MISSING"))tree.remove("projectCode");
            else if(parameter.endsWith("ADDITIONAL"))tree.put("extra","forbidden");
            else if(parameter.endsWith("UNKNOWN-ENUM"))tree.put("outcome","SURPRISE");
            else if(parameter.endsWith("ILLEGAL-NULL"))((ObjectNode)tree.get("currentProjection")).putNull("priceSnapshotSummary");
            else if(parameter.endsWith("WRONG-TYPE"))tree.set("aggregateVersion",mapper.createObjectNode().put("bad",1));
            else if(parameter.endsWith("CROSS-FIELD-MISMATCH"))tree.put("resourceRef","different");
            else throw new AssertionError(parameter);
            Response bad=mapper.treeToValue(tree,Response.class); rejected=!service.isStrictResponseForTest(bad);
        } catch(Exception expected){ rejected=true; }
        assertThat(rejected).isTrue();
        assertThat(counts()).isEqualTo(before);
    }

    @Test void all_twenty_three_observation_boundaries_are_sensitive_zero_to_one() {
        install(new F().result(ResultState.REJECTED).build()); var rejectedBefore=counts();
        assertThat(create("rejected-command","rejected-idempotency").projectCode()).isEqualTo("TOPUP_NOT_AVAILABLE");
        assertThat(service.canonicalMapEntryCountForTest()).isZero(); assertThat(counts()).isEqualTo(rejectedBefore);

        install(new F().build()); var beforeCreate=counts(); create("created-command","created-idempotency"); var afterCreate=counts();
        for(String name:List.of("Command","CommandAlias","TopupBusinessKey","TopupSemanticAction","TopupIntent",
                "DispatchSemanticAction","DispatchIntent","OrderVersion","ProjectionVersion")) {
            assertThat(afterCreate.get(name)-beforeCreate.get(name)).as(name).isEqualTo(1);
        }
        service.applyObservationForTest(fixture.orderRef(),"CONFIRMED","UNKNOWN","NOT_OBSERVED",
                UnknownAgeDecision.WITHIN_LOCAL_WINDOW,false);
        assertThat(counts().get("SyntheticObservation")-afterCreate.get("SyntheticObservation")).isEqualTo(1);
        assertThat(service.hasNoForbiddenSideEffectDeltaForTest(beforeCreate,counts())).isTrue();

        for(var counter:EnumSet.range(P014TopupSideEffectProbe.Counter.PaymentAttempt,
                P014TopupSideEffectProbe.Counter.ExternalCall)) {
            var mutated=new HashMap<>(afterCreate); mutated.put(counter.name(),mutated.get(counter.name())+1);
            assertThat(service.hasNoForbiddenSideEffectDeltaForTest(afterCreate,mutated)).as(counter.name()).isFalse();
        }
    }

    @ParameterizedTest @ValueSource(strings={"SESSION_VERSION","AUTHORIZATION_SET","AUTHORIZED_ORDER_MEMBERSHIP"})
    void buyer_authorization_is_exact_and_checked_before_canonical_lookup(String parameter) {
        create("c","i"); var before=counts();
        if(parameter.equals("SESSION_VERSION")) service.alterAuthorizationForTest(fixture.sessionRef(),2,fixture.authorizationSetRef(),fixture.authorizationEvidenceVersion(),Set.of(fixture.orderRef()));
        else if(parameter.equals("AUTHORIZATION_SET")) service.alterAuthorizationForTest(fixture.sessionRef(),1,"OTHER-SET",fixture.authorizationEvidenceVersion(),Set.of(fixture.orderRef()));
        else service.alterAuthorizationForTest(fixture.sessionRef(),1,fixture.authorizationSetRef(),fixture.authorizationEvidenceVersion(),Set.of(fixture.orderRef(),"EXTRA-ORDER"));
        var replay=create("c","i"); assertThat(replay.projectCode()).isEqualTo("TOPUP_NOT_AVAILABLE"); assertNonQueryUnchanged(before);
    }

    @Test void allowed_action_binding_is_server_bound_to_action_order_authorization_and_projection_version() {
        var before=projection().currentProjection();
        assertThat(before.allowedActions()).extracting(P014TopupDomain.AllowedAction::actionCode)
                .containsExactly("CREATE_LOCAL_SYNTHETIC_TOPUP","REFRESH_ORDER_PROJECTION","OPEN_SUPPORT","SAFE_LEAVE");
        assertThat(before.progressSummary().supportRef()).isNull();
        String oldBinding=before.allowedActions().stream().filter(a->a.actionCode().equals("REFRESH_ORDER_PROJECTION")).findFirst().orElseThrow().actionBindingVersion();
        var after=create("c","i").currentProjection();
        String newBinding=after.allowedActions().stream().filter(a->a.actionCode().equals("REFRESH_ORDER_PROJECTION")).findFirst().orElseThrow().actionBindingVersion();
        assertThat(newBinding).isNotEqualTo(oldBinding);
        assertThat(after.allowedActions()).extracting(P014TopupDomain.AllowedAction::actionBindingVersion).doesNotHaveDuplicates();
    }

    private Response create(String c,String i){return call(request(c,i,1,1));}
    private Response call(CreateRequest q){return service.create(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef(),q);}
    private Response projection(){return service.projection(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef());}
    private Response result(String c,String i){return service.result(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef(),c,i,1L,fixture.authorizationSetRef(),false);}
    private CreateRequest request(String c,String i,long pv,long av){return new CreateRequest(c,i,PRECONDITION,1,fixture.authorizationSetRef(),pv,av);}
    private void install(Fixture f){fixture=f;service.resetForTest();service.installFixtureForTest(f);}
    private void replace(Fixture f){fixture=f;service.replaceMutableFixtureForTest(f);}
    private Map<String,Long> counts(){return service.countsForTest();}
    private void assertRejectedZero(String code){var before=counts();var r=create("c","i");assertThat(r.projectCode()).isEqualTo(code);assertThat(counts()).isEqualTo(before);}
    private void assertCreateDelta(){var c=counts();for(String n:List.of("Command","CommandAlias","TopupBusinessKey","TopupSemanticAction","TopupIntent","DispatchSemanticAction","DispatchIntent","OrderVersion","ProjectionVersion"))assertThat(c.get(n)).as(n).isEqualTo(1);for(String n:List.of("PaymentAttempt","SendAttempt","RemoteAcceptance","WechatPrepay","RequestPayment","Notification","ExternalFact","W","U","D","L","LedgerEntry","ExternalCall"))assertThat(c.get(n)).as(n).isZero();}
    private void assertNonQueryUnchanged(Map<String,Long> before){var after=counts();before.forEach((k,v)->{if(!k.equals("QueryCall"))assertThat(after.get(k)).as(k).isEqualTo(v);});}
    private void assertStrict(Response r){assertThat(service.isStrictResponseForTest(r)).isTrue();assertThat(r.currentProjection().priceSnapshotSummary().getClass().getRecordComponents()).hasSize(10);assertThat(r.currentProjection().getClass().getRecordComponents()).hasSize(15);}

    private static final class F {
        String payment="CONFIRMED",mnp="ELIGIBLE",u="NOT_OBSERVED",d="UNKNOWN",l="NOT_OBSERVED";
        String payVer="PAY-V1",mnpVer="MNP-V1",catalog="CAT-V1",supportSet="SET-V1",support="SUPPORT-P014-1";
        String priceDigest, fixtureEvidence="AUTH-EVIDENCE-V1", authStateEvidence="AUTH-EVIDENCE-V1";
        boolean catalogCurrent=true,supportCurrent=true,allowed=true,duplicate=false; long pv=1,av=1;
        UnknownAgeDecision age=UnknownAgeDecision.WITHIN_LOCAL_WINDOW; ResultState result=ResultState.FOUND; Instant nextPoll;
        F payment(String v){payment=v;return this;} F mnp(String v){mnp=v;return this;} F u(String v){u=v;return this;} F d(String v){d=v;return this;} F l(String v){l=v;return this;}
        F versions(long v){pv=v;av=v;return this;} F age(UnknownAgeDecision v){age=v;return this;} F result(ResultState v){result=v;return this;} F nextPoll(Instant v){nextPoll=v;return this;}
        F decisionVersions(String p,String m){payVer=p;mnpVer=m;return this;} F catalog(String v,boolean current){catalog=v;catalogCurrent=current;return this;}
        F support(String v){support=v;return this;} F duplicate(boolean v){duplicate=v;return this;} F priceDigest(String v){priceDigest=v;return this;}
        F authEvidence(String v){fixtureEvidence=v;return this;} F authStateEvidence(String v){authStateEvidence=v;return this;}
        Fixture build(){Instant now=Instant.parse("2026-08-03T00:00:00Z");var price=new PriceSnapshotSummary("PS-1",125000,"BDT","DISPLAY-V1","******1234","SYN Operator","SYN Package",100000,"BDT",now.plusSeconds(3600));
            Fixture f=new Fixture(ENVIRONMENT,REALITY_LEVEL,"ORDER-P014-1",ID.projectSubjectRef(),ID.sessionRef(),"BUYER",1,"AUTHSET-P014-1",fixtureEvidence,List.of("ORDER-P014-1"),ORDER_STATE,pv,av,price,priceDigest==null?P014TopupService.snapshotDigest(price):priceDigest,"PAY-DEC-1",payVer,payment,"MNP-DEC-1",mnpVer,mnp,catalog,supportSet,catalogCurrent,supportCurrent,allowed,u,d,l,age,support,duplicate,result,nextPoll,now);
            if(!authStateEvidence.equals(fixtureEvidence)) return new Fixture(f.environment(),f.realityEvidenceLevel(),f.orderRef(),f.projectSubjectRef(),f.sessionRef(),f.sessionRole(),f.sessionVersion(),f.authorizationSetRef(),authStateEvidence,f.authorizedOrderRefs(),f.orderState(),f.projectionVersion(),f.aggregateVersion(),f.priceSnapshot(),f.priceSnapshotDigest(),f.paymentDecisionRef(),f.paymentDecisionVersion(),f.paymentState(),f.mnpDecisionRef(),f.mnpDecisionVersion(),f.mnpState(),f.catalogVersion(),f.supportedOperatorSetVersion(),f.catalogCurrent(),f.supportSetCurrent(),f.allowed(),f.upstreamDebitState(),f.deliveryState(),f.accountingClosureState(),f.unknownAgeDecision(),f.supportRef(),f.duplicateCanonicalFactConflict(),f.createResultState(),f.nextPollAt(),f.now());
            return f;}
    }
}
