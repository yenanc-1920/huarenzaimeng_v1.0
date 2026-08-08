package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static com.huarenzaimeng.api.P014TopupDomain.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Executable formal runner. The outer single-use wrapper is the only authorized entry point. */
@EnabledIfSystemProperty(named = "hz.p014.evidence.finalRun", matches = "AUTHORIZED_ONCE")
class P014TopupEvidenceExecutableFinalRunTest {
    static final String D1 = "364F73B28E2BC1C4D3827D5B9332C1CEB6D93C928859A226CD8842FF283D9BD8";
    static final String D2 = "A87E8D7BFE6D8AF421BF87421849D6065DE96EB160A37F2DCCD2AF2EA59A8E82";
    static final String D303 = "875CCE8688EACAA0F803AA03E29B4E8C9846718F1D28DF4246A4DFAA99F53475";
    static final String D304 = "579A1A87A239A6690779991CD0BDF57F1C81E7A9DF34684179E98BFB7B7EFB0B";
    static final String D305 = "96C0914C2962EDC5D16BF16460A9DC194DA4E50792BB18FC12542D885749C29B";
    static final String D3REG = "A14544E586DF44D56F08E25948FE4F6D1569D2D646DC02992EB2AC9EF50CE3BD";
    static final String REVIEWED_IMPLEMENTATION = "46849B2856431B21FAE002B988822803606C4C60545434BD9ECE608CADEACF89";
    static final String MATRIX = "676A283CB81B0819522BB3FF198A90CF8A02EE41A45AB8C2EB45AEB29E4EE7E5";
    static final String IDENTITY_SET_SHA = "5591715C3EB8AE7263E9888E3563270BB0A8F0D3107F7FDA1C32E79E1C5A0A3E";
    static final String PACKAGE_ID = "D5-BE-05-P014-LOCAL-SYNTHETIC";
    static final LocalSyntheticIdentity ID = LocalSyntheticIdentity.fromToken("p014-unit-token");
    static final List<String> WRITES = List.of("Command", "CommandAlias", "TopupBusinessKey", "TopupSemanticAction",
            "TopupIntent", "DispatchSemanticAction", "DispatchIntent", "OrderVersion", "ProjectionVersion",
            "SyntheticObservation", "PaymentAttempt", "SendAttempt", "RemoteAcceptance", "WechatPrepay",
            "RequestPayment", "Notification", "ExternalFact", "W", "U", "D", "L", "LedgerEntry", "ExternalCall");
    static final Map<String, List<String>> FIXED_MATRIX = Collections.unmodifiableMap(new TreeMap<>(Map.ofEntries(
            e("BE05-P014-001-APPROVED-CREATE", "P001-VALID"),
            e("BE05-P014-002-EXACT-REPLAY", "P001-UNCHANGED", "P002-DECISION-ROTATED", "P003-CATALOG-DRIFTED"),
            e("BE05-P014-003-COMMAND-KEY-CONFLICT", "P001-SAME-COMMAND-NEW-IDEMPOTENCY", "P002-SAME-COMMAND-DIFFERENT-REQUEST"),
            e("BE05-P014-004-IDEMPOTENCY-KEY-CONFLICT", "P001-SAME-IDEMPOTENCY-NEW-COMMAND", "P002-SAME-IDEMPOTENCY-DIFFERENT-REQUEST"),
            e("BE05-P014-005-SAME-SEMANTIC-KEY-SWITCH", "P001-NEW-DOUBLE-KEY-SAME-FINGERPRINT", "P002-NEW-DOUBLE-KEY-DIFFERENT-FINGERPRINT"),
            e("BE05-P014-006-B1-MNP-UNKNOWN-ZERO", "P001-MNP-UNKNOWN", "P002-MNP-UNAVAILABLE"),
            e("BE05-P014-007-PAYMENT-CONFIRMATION-UNKNOWN-ZERO", "P001-PAYMENT-UNKNOWN", "P002-PAYMENT-CONFLICT"),
            e("BE05-P014-008-AUTH-OR-EXISTENCE-SAME-SHAPE", "P001-UNAUTHENTICATED", "P002-REVOKED", "P003-CROSS-SUBJECT", "P004-NOT-FOUND"),
            e("BE05-P014-009-PRICE-OR-VERSION-DRIFT-ZERO", "P001-PRICE-DIGEST", "P002-ORDER-VERSION", "P003-PROJECTION-VERSION", "P004-AUTH-EVIDENCE-VERSION"),
            e("BE05-P014-010-CONCURRENT-UNIQUE-CREATE", "P001-TWO-DISTINCT-DOUBLE-KEY-COMPETITORS", "P002-N-DISTINCT-DOUBLE-KEY-COMPETITORS"),
            e("BE05-P014-011-U-CONFIRMED-D-UNKNOWN", "P001-D-UNKNOWN-WITHIN-WINDOW", "P002-D-UNKNOWN-LONG-RUNNING", "P003-D-NOT-OBSERVED-AGE-UNKNOWN", "P004-D-ABSENT-CONFIRMED"),
            e("BE05-P014-012-D-CONFIRMED-U-UNKNOWN", "P001-U-UNKNOWN", "P002-ACCOUNTING-UNKNOWN", "P003-U-AND-ACCOUNTING-UNKNOWN"),
            e("BE05-P014-013-U-D-BOTH-UNKNOWN", "P001-WITHIN-WINDOW", "P002-LONG-RUNNING", "P003-AGE-UNKNOWN"),
            e("BE05-P014-014-U-D-ACCOUNTING-CONFIRMED", "P001-ALL-CONFIRMED"),
            e("BE05-P014-015-DUPLICATE-OBSERVATION-ZERO", "P001-U-DUPLICATE", "P002-D-DUPLICATE", "P003-ACCOUNTING-DUPLICATE"),
            e("BE05-P014-016-FACT-CONFLICT-REVIEW", "P001-U-CONFLICT", "P002-D-CONFLICT", "P003-ACCOUNTING-CONFLICT", "P004-DUPLICATE-CANONICAL-IDENTITY"),
            e("BE05-P014-017-OUT-OF-ORDER-MONOTONIC", "P001-CONFIRMED-THEN-UNKNOWN", "P002-D-LATE-BEFORE-U", "P003-CONFLICT-AFTER-CONFIRMED"),
            e("BE05-P014-018-ORIGINAL-KEY-QUERY-UNKNOWN", "P001-NEXTPOLL-NULL", "P002-NEXTPOLL-RFC3339"),
            e("BE05-P014-019-LATE-RESULT-SAME-KEY-CONVERGENCE", "P001-UNKNOWN-TO-FOUND", "P002-UNKNOWN-TO-REJECTED", "P003-FOUND-NO-REGRESSION"),
            e("BE05-P014-020-STRICT-DTO-ZERO-REAL-SIDE-EFFECT", "P001-MISSING", "P002-ADDITIONAL", "P003-UNKNOWN-ENUM", "P004-ILLEGAL-NULL", "P005-WRONG-TYPE", "P006-CROSS-FIELD-MISMATCH"))));

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test void execute_fixed_twenty_scenarios_and_fifty_six_parameters() throws Exception {
        String runId = exact("hz.p014.evidence.runId", "P014-BE-");
        Path repository = Path.of(required("hz.p014.evidence.repositoryRoot")).toRealPath();
        Path output = Path.of(required("hz.p014.evidence.outputDir")).toRealPath();
        verifyBinding("hz.p014.evidence.d1", D1); verifyBinding("hz.p014.evidence.d2", D2);
        verifyBinding("hz.p014.evidence.d303", D303); verifyBinding("hz.p014.evidence.d304", D304);
        verifyBinding("hz.p014.evidence.d305", D305); verifyBinding("hz.p014.evidence.d3reg", D3REG);
        verifyBinding("hz.p014.evidence.implementation", REVIEWED_IMPLEMENTATION);
        verifyBinding("hz.p014.evidence.matrix", MATRIX);
        String runnerSha = required("hz.p014.evidence.runnerSha").toUpperCase(Locale.ROOT);
        Path runner = repository.resolve("apps/api/src/test/java/com/huarenzaimeng/api/P014TopupEvidenceExecutableFinalRunTest.java");
        assertThat(sha(runner)).isEqualTo(runnerSha);
        Path marker = output.resolve("STAGING_AUTHORIZED.json");
        assertThat(Files.isRegularFile(marker)).isTrue();
        var markerJson = json.readTree(marker.toFile());
        assertThat(markerJson.path("RunId").asText()).isEqualTo(runId);
        assertThat(markerJson.path("RunnerSha256").asText()).isEqualTo(runnerSha);
        assertThat(FIXED_MATRIX).hasSize(20);
        assertThat(FIXED_MATRIX.values().stream().mapToInt(List::size).sum()).isEqualTo(56);
        assertThat(WRITES).hasSize(23).doesNotHaveDuplicates();

        Path cases = output.resolve("cases");
        Files.createDirectory(cases);
        int written = 0;
        for (var scenario : FIXED_MATRIX.entrySet()) for (String parameter : scenario.getValue()) {
            CaseExecution execution = execute(scenario.getKey(), parameter);
            Map<String,Object> expectedOracle=execution.expected();String expectedOracleSha=shaText(json.writeValueAsString(expectedOracle));
            Map<String,Object> actualWithBinding=new LinkedHashMap<>(execution.actual());actualWithBinding.put("MatchedExpectedOracleSha256",expectedOracleSha);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("EvidencePackageId", PACKAGE_ID); payload.put("EvidencePackageRef", PACKAGE_ID + "/" + scenario.getKey() + "/" + parameter);
            payload.put("ScenarioId", scenario.getKey()); payload.put("SubcaseId", scenario.getKey()); payload.put("ParameterId", parameter);
            payload.put("RunId", runId); payload.put("ExecutedAt", Instant.now().toString()); payload.put("ExecutionStatus", "PASS");
            payload.put("Consumable", false); payload.put("AutomaticRetryAllowed", false);payload.put("IdentitySetSha256",IDENTITY_SET_SHA);
            payload.put("FixedInputs", fixedInputs(runnerSha)); payload.put("Input", execution.input());
            payload.put("Expected", expectedOracle);payload.put("ExpectedOracleSha256",expectedOracleSha);payload.put("Actual", actualWithBinding);
            payload.put("CompleteResponse", execution.completeResponse());
            payload.put("Before", writes(execution.before())); payload.put("After", writes(execution.after()));
            payload.put("Delta", delta(writes(execution.before()), writes(execution.after())));
            payload.put("QueryCall", Map.of("before", execution.before().get("QueryCall"), "after", execution.after().get("QueryCall"),
                    "delta", execution.after().get("QueryCall") - execution.before().get("QueryCall")));
            payload.put("ProcessEvidenceRef", "process/process-evidence.json"); payload.put("DefectRef", "N/A");
            Path target = cases.resolve(scenario.getKey() + "__" + parameter + ".json");
            atomicJson(target, payload); written++;
        }
        assertThat(written).isEqualTo(56);
        assertThat(Files.list(cases).filter(Files::isRegularFile).count()).isEqualTo(56);
    }

    private CaseExecution execute(String scenario, String parameter) throws Exception {
        P014TopupSideEffectProbe probe = new P014TopupSideEffectProbe();
        P014TopupService service = new P014TopupService(probe);
        Fixture fixture = new F().build(); service.installFixtureForTest(fixture);
        Map<String, Long> before = service.countsForTest();
        Map<String, Object> input = map("scenario", scenario, "parameter", parameter, "fixture", "EXPLICIT_LOCAL_SYNTHETIC_NO_DEFAULT");
        Map<String, Object> actual = new LinkedHashMap<>(); Object complete;
        switch (scenario.substring(10, 13)) {
            case "001" -> { Response r=create(service,fixture,"c1","i1"); assertCode(r,"TOPUP_INTENT_CREATED"); complete=r; }
            case "002" -> {
                Response first=create(service,fixture,"c1","i1");
                if(parameter.contains("DECISION-ROTATED")) { fixture=new F().versions(2).decisionVersions("PAY-V2","MNP-V2").build(); service.replaceMutableFixtureForTest(fixture); }
                if(parameter.contains("CATALOG-DRIFTED")) { fixture=new F().versions(2).catalog("CAT-V2",false).build(); service.replaceMutableFixtureForTest(fixture); }
                Map<String,Long> phase=service.countsForTest(); Response replay=create(service,fixture,"c1","i1");
                assertCode(replay,"TOPUP_INTENT_REPLAYED"); assertThat(replay.resourceRef()).isEqualTo(first.resourceRef());
                assertThat(service.countsForTest()).isEqualTo(phase); actual.put("first",first); actual.put("replay",replay); complete=actual;
            }
            case "003" -> {
                Response first=create(service,fixture,"c1","i1"); Map<String,Long> phase=service.countsForTest();
                CreateRequest q=parameter.endsWith("REQUEST")?request(fixture,"c1","i1",99,1):request(fixture,"c1","i2",1,1);
                Response r=service.create(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef(),q);
                assertCode(r,"IDEMPOTENCY_CONFLICT"); assertThat(service.countsForTest()).isEqualTo(phase); complete=map("first",first,"conflict",r);
            }
            case "004" -> {
                Response first=create(service,fixture,"c1","i1"); Map<String,Long> phase=service.countsForTest();
                CreateRequest q=parameter.endsWith("REQUEST")?request(fixture,"c1","i1",99,1):request(fixture,"c2","i1",1,1);
                Response r=service.create(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef(),q);
                assertCode(r,"IDEMPOTENCY_CONFLICT"); assertThat(service.countsForTest()).isEqualTo(phase); complete=map("first",first,"conflict",r);
            }
            case "005" -> {
                Response first=create(service,fixture,"c1","i1"); Map<String,Long> phase=service.countsForTest();
                CreateRequest q=request(fixture,"c2","i2",parameter.contains("DIFFERENT")?99:1,1);
                Response r=service.create(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef(),q);
                assertCode(r,"IDEMPOTENCY_CONFLICT"); assertThat(service.countsForTest()).isEqualTo(phase); complete=map("first",first,"conflict",r);
            }
            case "006" -> {
                fixture=new F().mnp(parameter.endsWith("UNAVAILABLE")?"UNAVAILABLE":"UNKNOWN").build(); reset(service,fixture);
                before=service.countsForTest(); Response r=create(service,fixture,"c","i"); assertCode(r,"TOPUP_QUALIFICATION_UNKNOWN");
                assertThat(service.countsForTest()).isEqualTo(before); complete=r;
            }
            case "007" -> {
                fixture=new F().payment(parameter.endsWith("CONFLICT")?"CONFLICT":"UNKNOWN").build(); reset(service,fixture);
                before=service.countsForTest(); Response r=create(service,fixture,"c","i"); assertCode(r,"TOPUP_QUALIFICATION_UNKNOWN");
                assertThat(service.countsForTest()).isEqualTo(before); complete=r;
            }
            case "008" -> {
                if(parameter.endsWith("REVOKED")) service.revokeForTest(fixture.sessionRef());
                String env=parameter.endsWith("UNAUTHENTICATED")?null:ENVIRONMENT;
                String subject=parameter.endsWith("CROSS-SUBJECT")?"OTHER":fixture.projectSubjectRef();
                String order=parameter.endsWith("NOT-FOUND")?"MISSING":fixture.orderRef();
                Response post=service.create(env,subject,fixture.sessionRef(),order,request(fixture,"c","i",1,1));
                Response projection=service.projection(env,subject,fixture.sessionRef(),order);
                Response result=service.result(env,subject,fixture.sessionRef(),order,"c","i",1L,fixture.authorizationSetRef(),false);
                assertCode(post,"TOPUP_NOT_AVAILABLE"); assertCode(projection,"TOPUP_PROGRESS_NOT_AVAILABLE"); assertCode(result,"TOPUP_INTENT_QUERY_NOT_AVAILABLE");
                complete=map("post",post,"projection",projection,"result",result);
            }
            case "009" -> {
                if(parameter.endsWith("PRICE-DIGEST")) { fixture=new F().priceDigest("WRONG-DIGEST").build(); reset(service,fixture); before=service.countsForTest(); }
                if(parameter.endsWith("AUTH-EVIDENCE-VERSION")) service.alterAuthorizationEvidenceForTest(fixture.sessionRef(),"AUTH-EVIDENCE-V2");
                long pv=parameter.endsWith("PROJECTION-VERSION")?2:1, av=parameter.endsWith("ORDER-VERSION")?2:1;
                Response r=service.create(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef(),request(fixture,"c","i",pv,av));
                assertThat(r.projectCode()).isIn("VERSION_CONFLICT","TOPUP_NOT_AVAILABLE","TOPUP_QUALIFICATION_UNKNOWN"); complete=r;
            }
            case "010" -> {
                int workers=parameter.startsWith("P001")?2:8; ExecutorService pool=Executors.newFixedThreadPool(workers);
                CountDownLatch ready=new CountDownLatch(workers),start=new CountDownLatch(1); List<Future<Response>> futures=new ArrayList<>();
                Fixture fixed=fixture; for(int i=0;i<workers;i++){int n=i;futures.add(pool.submit(()->{ready.countDown();start.await();return create(service,fixed,"c"+n,"i"+n);}));}
                ready.await();start.countDown();List<Response> responses=new ArrayList<>();for(var f:futures)responses.add(f.get());pool.shutdown();
                assertThat(responses).filteredOn(r->r.projectCode().equals("TOPUP_INTENT_CREATED")).hasSize(1);
                assertThat(responses).filteredOn(r->r.projectCode().equals("IDEMPOTENCY_CONFLICT")).hasSize(workers-1);complete=responses;
            }
            case "011" -> {
                create(service,fixture,"c","i"); F f=new F().versions(2).u("CONFIRMED");
                if(parameter.contains("LONG"))f.age(UnknownAgeDecision.LONG_RUNNING);else if(parameter.contains("AGE-UNKNOWN"))f.d("NOT_OBSERVED").age(UnknownAgeDecision.UNKNOWN);else if(parameter.contains("ABSENT"))f.d("ABSENT_CONFIRMED");else f.d("UNKNOWN");
                fixture=f.build();service.replaceMutableFixtureForTest(fixture);Response r=service.projection(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef());complete=r;
            }
            case "012" -> {
                create(service,fixture,"c","i"); F f=new F().versions(2).d("CONFIRMED").u(parameter.endsWith("ACCOUNTING-UNKNOWN")&&!parameter.contains("U-AND")?"CONFIRMED":"UNKNOWN").l(parameter.endsWith("U-UNKNOWN")?"CONFIRMED":"UNKNOWN");
                fixture=f.build();service.replaceMutableFixtureForTest(fixture);Response r=service.projection(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef());assertCode(r,"TOPUP_PROGRESS_READ");complete=r;
            }
            case "013" -> {
                create(service,fixture,"c","i");UnknownAgeDecision age=parameter.endsWith("WITHIN-WINDOW")?UnknownAgeDecision.WITHIN_LOCAL_WINDOW:parameter.endsWith("LONG-RUNNING")?UnknownAgeDecision.LONG_RUNNING:UnknownAgeDecision.UNKNOWN;
                fixture=new F().versions(2).u("UNKNOWN").d("UNKNOWN").l("UNKNOWN").age(age).build();service.replaceMutableFixtureForTest(fixture);
                Response r=service.projection(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef());assertCode(r,"TOPUP_PROGRESS_READ");complete=r;
            }
            case "014" -> {
                create(service,fixture,"c","i");fixture=new F().versions(2).u("CONFIRMED").d("CONFIRMED").l("CONFIRMED").build();service.replaceMutableFixtureForTest(fixture);
                Response r=service.projection(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef());assertThat(r.currentProjection().stateCode()).isEqualTo("DELIVERED");complete=r;
            }
            case "015" -> {
                create(service,fixture,"c","i");fixture=new F().versions(2).u("CONFIRMED").d("CONFIRMED").l("CONFIRMED").build();service.replaceMutableFixtureForTest(fixture);
                Response first=service.projection(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef());Map<String,Long> phase=service.countsForTest();
                service.applyObservationForTest(fixture.orderRef(),"CONFIRMED","CONFIRMED","CONFIRMED",UnknownAgeDecision.WITHIN_LOCAL_WINDOW,false);
                Response second=service.projection(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef());assertThat(second.currentProjection()).isEqualTo(first.currentProjection());
                assertOnlyQueryChanged(phase,service.countsForTest());complete=map("first",first,"second",second);
            }
            case "016" -> {
                create(service,fixture,"c","i");F f=new F().versions(2);if(parameter.contains("U-CONFLICT"))f.u("CONFLICT");else if(parameter.contains("D-CONFLICT"))f.d("CONFLICT");else if(parameter.contains("ACCOUNTING-CONFLICT"))f.l("CONFLICT");else f.duplicate(true);
                fixture=f.build();service.replaceMutableFixtureForTest(fixture);Response r=service.projection(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef());assertThat(r.currentProjection().stateCode()).isEqualTo("SUPPORT_REVIEW");complete=r;
            }
            case "017" -> {
                create(service,fixture,"c","i");if(parameter.contains("D-LATE-BEFORE-U"))service.applyObservationForTest(fixture.orderRef(),"UNKNOWN","CONFIRMED","UNKNOWN",UnknownAgeDecision.WITHIN_LOCAL_WINDOW,false);else service.applyObservationForTest(fixture.orderRef(),"CONFIRMED","UNKNOWN","CONFIRMED",UnknownAgeDecision.WITHIN_LOCAL_WINDOW,false);
                Response prior=service.projection(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef());if(parameter.contains("CONFLICT-AFTER-CONFIRMED"))service.applyObservationForTest(fixture.orderRef(),"CONFLICT","UNKNOWN","CONFIRMED",UnknownAgeDecision.WITHIN_LOCAL_WINDOW,false);else service.applyObservationForTest(fixture.orderRef(),"UNKNOWN","UNKNOWN","UNKNOWN",UnknownAgeDecision.WITHIN_LOCAL_WINDOW,false);
                Response r=service.projection(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef());actual.put("priorStateCode",prior.currentProjection().stateCode());actual.put("finalStateCode",r.currentProjection().stateCode());
                complete=map("priorResponse",json.valueToTree(prior),"finalResponse",r);
            }
            case "018" -> {
                fixture=new F().result(ResultState.UNKNOWN).nextPoll(parameter.endsWith("RFC3339")?Instant.parse("2026-08-03T00:05:00Z"):null).build();reset(service,fixture);before=service.countsForTest();
                Response create=create(service,fixture,"c","i");Response result=result(service,fixture,"c","i");assertCode(create,"TOPUP_INTENT_CREATE_UNKNOWN");assertCode(result,"TOPUP_INTENT_RESULT_UNKNOWN");complete=map("create",create,"result",result);
            }
            case "019" -> {
                fixture=new F().result(ResultState.UNKNOWN).build();reset(service,fixture);before=service.countsForTest();Response createUnknown=create(service,fixture,"c","i");assertCode(createUnknown,"TOPUP_INTENT_CREATE_UNKNOWN");
                Response initialUnknownResult=result(service,fixture,"c","i");assertCode(initialUnknownResult,"TOPUP_INTENT_RESULT_UNKNOWN");
                ResultState terminal=parameter.endsWith("UNKNOWN-TO-REJECTED")?ResultState.REJECTED:ResultState.FOUND;service.convergeResultForTest(fixture.orderRef(),terminal);Response terminalResult=result(service,fixture,"c","i");assertCode(terminalResult,terminal==ResultState.REJECTED?"TOPUP_INTENT_RESULT_REJECTED":"TOPUP_INTENT_RESULT_FOUND");
                if(parameter.endsWith("FOUND-NO-REGRESSION")){service.convergeResultForTest(fixture.orderRef(),ResultState.UNKNOWN);service.convergeResultForTest(fixture.orderRef(),ResultState.REJECTED);Response postConflictResult=result(service,fixture,"c","i");assertCode(postConflictResult,"TOPUP_INTENT_RESULT_FOUND");complete=map("createUnknown",createUnknown,"initialUnknownResult",initialUnknownResult,"terminalResult",terminalResult,"postConflictResult",postConflictResult,"reviewSignalCount",service.resultReviewSignalCountForTest());}
                else complete=map("createUnknown",createUnknown,"initialUnknownResult",initialUnknownResult,"terminalResult",terminalResult,"reviewSignalCount",service.resultReviewSignalCountForTest());
                actual.put("submissionState",service.submissionStateForTest(fixture.orderRef()));actual.put("reviewSignalCount",service.resultReviewSignalCountForTest());
            }
            case "020" -> {
                Map<String,Long> createEndpointBefore=service.countsForTest();Response created=create(service,fixture,"c","i");Map<String,Long> createEndpointAfter=service.countsForTest();assertCode(created,"TOPUP_INTENT_CREATED");before=createEndpointAfter;
                Map<String,Object> components=new LinkedHashMap<>();components.put("create",component020(service,"CREATE_RESPONSE","TOPUP_INTENT_CREATED",created,parameter,createEndpointBefore,createEndpointAfter,0,expectedWrites(true,0)));
                Map<String,Long> resultEndpointBefore=service.countsForTest();Response result=result(service,fixture,"c","i");Map<String,Long> resultEndpointAfter=service.countsForTest();assertCode(result,"TOPUP_INTENT_RESULT_FOUND");
                components.put("result",component020(service,"RESULT_RESPONSE","TOPUP_INTENT_RESULT_FOUND",result,parameter,resultEndpointBefore,resultEndpointAfter,1,expectedWrites(false,0)));
                Map<String,Long> projectionEndpointBefore=service.countsForTest();Response projection=service.projection(ENVIRONMENT,fixture.projectSubjectRef(),fixture.sessionRef(),fixture.orderRef());Map<String,Long> projectionEndpointAfter=service.countsForTest();assertCode(projection,"TOPUP_PROGRESS_READ");
                components.put("projection",component020(service,"PROJECTION_RESPONSE","TOPUP_PROGRESS_READ",projection,parameter,projectionEndpointBefore,projectionEndpointAfter,1,expectedWrites(false,0)));
                actual.put("componentEvidence",components);complete=map("components",components);
            }
            default -> throw new IllegalArgumentException(scenario);
        }
        Map<String,Long> after=service.countsForTest();
        assertThat(service.hasNoForbiddenSideEffectDeltaForTest(before,after)).isTrue();
        Oracle oracle=oracle(scenario,parameter);Map<String,Object> normalized=normalizeActual(complete,actual,before,after);
        oracle.assertMatches(normalized);normalized.put("oracleAssertionsPassed",true);
        return new CaseExecution(input,oracle.expected(),Map.copyOf(normalized),complete,before,after);
    }

    private static Response create(P014TopupService s,Fixture f,String c,String i){return s.create(ENVIRONMENT,f.projectSubjectRef(),f.sessionRef(),f.orderRef(),request(f,c,i,1,1));}
    private static Response result(P014TopupService s,Fixture f,String c,String i){return s.result(ENVIRONMENT,f.projectSubjectRef(),f.sessionRef(),f.orderRef(),c,i,1L,f.authorizationSetRef(),false);}
    private static CreateRequest request(Fixture f,String c,String i,long pv,long av){return new CreateRequest(c,i,PRECONDITION,1,f.authorizationSetRef(),pv,av);}
    private static void reset(P014TopupService s,Fixture f){s.resetForTest();s.installFixtureForTest(f);}
    private static void assertCode(Response r,String code){assertThat(r.projectCode()).isEqualTo(code);}
    private static void assertOnlyQueryChanged(Map<String,Long> before,Map<String,Long> after){before.forEach((k,v)->{if(!k.equals("QueryCall"))assertThat(after.get(k)).as(k).isEqualTo(v);});}
    private ObjectNode mutate(Response response,String parameter)throws Exception{
        ObjectNode tree=(ObjectNode)json.readTree(json.writeValueAsString(response));
        if(parameter.endsWith("MISSING"))tree.remove("projectCode");
        else if(parameter.endsWith("ADDITIONAL"))tree.put("extra","forbidden");
        else if(parameter.endsWith("UNKNOWN-ENUM"))tree.put("outcome","SURPRISE");
        else if(parameter.endsWith("ILLEGAL-NULL")){if(tree.path("currentProjection").isObject())((ObjectNode)tree.get("currentProjection")).putNull("priceSnapshotSummary");else tree.putNull("projectCode");}
        else if(parameter.endsWith("WRONG-TYPE"))tree.set("aggregateVersion",json.createObjectNode().put("bad",1));
        else tree.put("resourceRef","different");
        return tree;
    }
    private Map<String,Object> component020(P014TopupService service,String responseType,String projectCode,Response response,String parameter,
                                             Map<String,Long> endpointBefore,Map<String,Long> endpointAfter,long endpointQueryDelta,Map<String,Long> endpointWriteDelta)throws Exception{
        Map<String,Long> actualEndpointDelta=delta(writes(endpointBefore),writes(endpointAfter));assertThat(actualEndpointDelta).isEqualTo(endpointWriteDelta);assertThat(endpointAfter.get("QueryCall")-endpointBefore.get("QueryCall")).isEqualTo(endpointQueryDelta);
        Map<String,Long> validationBefore=service.countsForTest();ObjectNode full=(ObjectNode)json.readTree(json.writeValueAsString(response));assertThat(full.size()).isEqualTo(8);assertThat(service.isStrictResponseForTest(response)).isTrue();
        ObjectNode mutation=mutate(response,parameter);boolean rejected;try{Response bad=json.treeToValue(mutation,Response.class);rejected=!service.isStrictResponseForTest(bad);}catch(Exception error){rejected=true;}
        Map<String,Long> validationAfter=service.countsForTest(),validationDelta=delta(writes(validationBefore),writes(validationAfter));assertThat(rejected).isTrue();assertThat(validationDelta).isEqualTo(expectedWrites(false,0));assertThat(validationAfter.get("QueryCall")-validationBefore.get("QueryCall")).isZero();
        Map<String,Object> expected=componentExpected020(responseType,projectCode,parameter,endpointQueryDelta,endpointWriteDelta);
        Map<String,Object> observed=map("responseType",responseType,"projectCode",response.projectCode(),"completeResponse",response,"responseFieldCount",full.size(),"endpointRead",map("queryDelta",endpointQueryDelta,"writeDelta",actualEndpointDelta),"validation",map("mutationParameter",parameter,"mutatedResponse",mutation,"mutationRejected",rejected,"queryDelta",0L,"writeDelta",validationDelta));
        return map("Expected",expected,"Actual",observed,
                "EndpointRead",map("Before",writes(endpointBefore),"After",writes(endpointAfter),"Delta",actualEndpointDelta,"QueryCall",map("before",endpointBefore.get("QueryCall"),"after",endpointAfter.get("QueryCall"),"delta",endpointQueryDelta)),
                "Validation",map("Before",writes(validationBefore),"After",writes(validationAfter),"Delta",validationDelta,"QueryCall",map("before",validationBefore.get("QueryCall"),"after",validationAfter.get("QueryCall"),"delta",0L)));
    }
    private static Map<String,Object> componentExpected020(String responseType,String projectCode,String parameter,long endpointQueryDelta,Map<String,Long> endpointWriteDelta){return map("responseType",responseType,"projectCode",projectCode,"strictEightFieldDto",true,"endpointRead",map("queryDelta",endpointQueryDelta,"writeDelta",endpointWriteDelta),"validation",map("mutationParameter",parameter,"mutationRejected",true,"queryDelta",0L,"writeDelta",expectedWrites(false,0)));}
    private static Map<String,Object> componentExpected020(String parameter){Map<String,Object> result=new LinkedHashMap<>();result.put("create",componentExpected020("CREATE_RESPONSE","TOPUP_INTENT_CREATED",parameter,0,expectedWrites(true,0)));result.put("result",componentExpected020("RESULT_RESPONSE","TOPUP_INTENT_RESULT_FOUND",parameter,1,expectedWrites(false,0)));result.put("projection",componentExpected020("PROJECTION_RESPONSE","TOPUP_PROGRESS_READ",parameter,1,expectedWrites(false,0)));return Map.copyOf(result);}
    private static Map<String,Object> normalizeActual(Object complete,Map<String,Object> scenarioActual,
                                                       Map<String,Long> before,Map<String,Long> after){
        List<Response> responses=new ArrayList<>();collectResponses(complete,responses);
        Map<String,Integer> codes=new TreeMap<>();for(Response response:responses)codes.merge(response.projectCode(),1,Integer::sum);
        List<Projection> projections=responses.stream().map(Response::currentProjection).filter(Objects::nonNull).toList();
        Map<String,Object> actual=new LinkedHashMap<>(scenarioActual);actual.put("responseCodeCounts",codes);
        actual.put("projectionStateCodes",projections.stream().map(Projection::stateCode).distinct().sorted().toList());
        actual.put("messageCodes",projections.stream().map(p->p.progressSummary().userMessageCode()).distinct().sorted().toList());
        actual.put("allowedActionSets",projections.stream().map(p->p.allowedActions().stream().map(AllowedAction::actionCode).toList()).distinct().toList());
        actual.put("projectionFactStates",projections.stream().map(p->{Map<String,String> states=new TreeMap<>();p.factTimeline().forEach(f->states.put(f.factCode(),f.state()));return states;}).distinct().toList());
        actual.put("responsibilityCodes",projections.stream().map(p->p.progressSummary().responsibilityCode()).distinct().sorted().toList());
        actual.put("confirmedItemSets",projections.stream().map(p->p.progressSummary().confirmedItems()).distinct().toList());
        actual.put("unknownItemSets",projections.stream().map(p->p.progressSummary().unknownItems()).distinct().toList());
        actual.put("unknownNextPollValues",responses.stream().filter(r->"UNKNOWN".equals(r.outcome())).map(Response::nextPollAt).toList());
        actual.put("responseSequence",responses.stream().map(Response::projectCode).toList());
        actual.put("writeDelta",delta(writes(before),writes(after)));actual.put("queryDelta",after.get("QueryCall")-before.get("QueryCall"));
        actual.put("responseCount",responses.size());return actual;
    }
    private static void collectResponses(Object value,List<Response> target){
        if(value instanceof Response response){target.add(response);return;}
        if(value instanceof Map<?,?> map){map.values().forEach(item->collectResponses(item,target));return;}
        if(value instanceof Iterable<?> iterable)for(Object item:iterable)collectResponses(item,target);
    }
    private static Oracle oracle(String scenario,String parameter){
        String id=scenario.substring(10,13);Map<String,Integer> codes;List<String> responseSequence=null;Map<String,Object> componentExpected=null;String state=null,message=null,responsibility=null;List<String> actions=List.of(),confirmed=null,unknown=null;Map<String,String> facts=null;long query=0;Map<String,Long>writes=expectedWrites(false,0);Instant nextPoll=null;String submission=null;int review=0;boolean strictThree=false;
        switch(id){
            case "001"->{codes=codes("TOPUP_INTENT_CREATED");state="TOPUP_PROCESSING";message="TOPUP_PROCESSING_DELIVERY_UNCONFIRMED";actions=progressActions();writes=expectedWrites(true,0);}
            case "002"->{codes=codes("TOPUP_INTENT_CREATED","TOPUP_INTENT_REPLAYED");state="TOPUP_PROCESSING";message="TOPUP_PROCESSING_DELIVERY_UNCONFIRMED";actions=progressActions();writes=expectedWrites(true,0);}
            case "003","004","005"->{codes=codes("TOPUP_INTENT_CREATED","IDEMPOTENCY_CONFLICT");state="TOPUP_PROCESSING";message="TOPUP_PROCESSING_DELIVERY_UNCONFIRMED";actions=progressActions();writes=expectedWrites(true,0);}
            case "006"->{codes=codes("TOPUP_QUALIFICATION_UNKNOWN");state="PAID_AWAITING_TOPUP";message="PAYMENT_CONFIRMED_TOPUP_QUALIFICATION_CHECKING";actions=safeReadActions();}
            case "007"->{codes=codes("TOPUP_QUALIFICATION_UNKNOWN");state="PAID_AWAITING_TOPUP";message="PAYMENT_CONFIRMATION_CHECKING_NO_AUTO_TOPUP";actions=safeReadActions();}
            case "008"->{codes=codes("TOPUP_NOT_AVAILABLE","TOPUP_PROGRESS_NOT_AVAILABLE","TOPUP_INTENT_QUERY_NOT_AVAILABLE");query=2;}
            case "009"->{String code=parameter.endsWith("ORDER-VERSION")||parameter.endsWith("PROJECTION-VERSION")?"VERSION_CONFLICT":"TOPUP_NOT_AVAILABLE";codes=codes(code);}
            case "010"->{int workers=parameter.startsWith("P001")?2:8;codes=new TreeMap<>();codes.put("TOPUP_INTENT_CREATED",1);codes.put("IDEMPOTENCY_CONFLICT",workers-1);state="TOPUP_PROCESSING";message="TOPUP_PROCESSING_DELIVERY_UNCONFIRMED";actions=progressActions();writes=expectedWrites(true,0);}
            case "011"->{codes=codes("TOPUP_PROGRESS_READ");query=1;writes=expectedWrites(true,0);
                if(parameter.contains("LONG")||parameter.contains("AGE-UNKNOWN")){state="TOPUP_RESULT_UNKNOWN";message="TOPUP_RESULT_PENDING_CONFIRMATION";actions=unknownActions();}
                else if(parameter.contains("ABSENT")){state="CONFIRMED_NOT_DELIVERED";message="UPSTREAM_CONFIRMED_DELIVERY_ABSENT";actions=progressActions();}
                else{state="TOPUP_PROCESSING";message="TOPUP_PROCESSING_DELIVERY_UNCONFIRMED";actions=progressActions();}
                String delivery=parameter.contains("ABSENT")?"ABSENT_CONFIRMED":parameter.contains("AGE-UNKNOWN")?"NOT_OBSERVED":"UNKNOWN";
                facts=facts("CONFIRMED","CONFIRMED",delivery,"NOT_OBSERVED");responsibility=state.equals("TOPUP_PROCESSING")?"SYSTEM_RECHECK":"SUPPORT_REVIEW";
                confirmed=delivery.equals("ABSENT_CONFIRMED")?List.of("PAYMENT","UPSTREAM_DEBIT","DELIVERY"):List.of("PAYMENT","UPSTREAM_DEBIT");unknown=delivery.equals("ABSENT_CONFIRMED")?List.of("ACCOUNTING_CLOSURE"):List.of("DELIVERY","ACCOUNTING_CLOSURE");}
            case "012"->{codes=codes("TOPUP_PROGRESS_READ");state="SUPPORT_REVIEW";message="DELIVERY_EVIDENCE_UNDER_REVIEW";actions=reviewActions();query=1;writes=expectedWrites(true,0);responsibility="ACCOUNTING_REVIEW";
                boolean uUnknown=parameter.endsWith("U-UNKNOWN")||parameter.contains("U-AND");boolean lUnknown=parameter.contains("ACCOUNTING-UNKNOWN");facts=facts("CONFIRMED",uUnknown?"UNKNOWN":"CONFIRMED","CONFIRMED",lUnknown?"UNKNOWN":"CONFIRMED");
                confirmed=uUnknown?(lUnknown?List.of("PAYMENT","DELIVERY"):List.of("PAYMENT","DELIVERY","ACCOUNTING_CLOSURE")):(lUnknown?List.of("PAYMENT","UPSTREAM_DEBIT","DELIVERY"):List.of("PAYMENT","UPSTREAM_DEBIT","DELIVERY","ACCOUNTING_CLOSURE"));unknown=uUnknown?(lUnknown?List.of("UPSTREAM_DEBIT","ACCOUNTING_CLOSURE"):List.of("UPSTREAM_DEBIT")):List.of("ACCOUNTING_CLOSURE");}
            case "013"->{codes=codes("TOPUP_PROGRESS_READ");query=1;writes=expectedWrites(true,0);if(parameter.endsWith("WITHIN-WINDOW")){state="TOPUP_PROCESSING";message="TOPUP_PROCESSING_DELIVERY_UNCONFIRMED";actions=progressActions();responsibility="SYSTEM_RECHECK";}else{state="TOPUP_RESULT_UNKNOWN";message="TOPUP_RESULT_PENDING_CONFIRMATION";actions=unknownActions();responsibility="SUPPORT_REVIEW";}facts=facts("CONFIRMED","UNKNOWN","UNKNOWN","UNKNOWN");confirmed=List.of("PAYMENT");unknown=List.of("UPSTREAM_DEBIT","DELIVERY","ACCOUNTING_CLOSURE");}
            case "014"->{codes=codes("TOPUP_PROGRESS_READ");state="DELIVERED";message="TOPUP_DELIVERED";actions=List.of("REFRESH_ORDER_PROJECTION","OPEN_SUPPORT","SAFE_LEAVE");query=1;writes=expectedWrites(true,0);}
            case "015"->{codes=codes("TOPUP_PROGRESS_READ","TOPUP_PROGRESS_READ");state="DELIVERED";message="TOPUP_DELIVERED";actions=List.of("REFRESH_ORDER_PROJECTION","OPEN_SUPPORT","SAFE_LEAVE");query=2;writes=expectedWrites(true,0);}
            case "016"->{codes=codes("TOPUP_PROGRESS_READ");state="SUPPORT_REVIEW";message="TOPUP_FACT_CONFLICT_UNDER_REVIEW";actions=reviewActions();query=1;writes=expectedWrites(true,0);}
            case "017"->{codes=codes("TOPUP_PROGRESS_READ");query=2;int synthetic=parameter.endsWith("CONFLICT-AFTER-CONFIRMED")?2:1;writes=expectedWrites(true,synthetic);
                if(parameter.endsWith("D-LATE-BEFORE-U")){state="SUPPORT_REVIEW";message="DELIVERY_EVIDENCE_UNDER_REVIEW";actions=reviewActions();}
                else if(parameter.endsWith("CONFLICT-AFTER-CONFIRMED")){state="SUPPORT_REVIEW";message="TOPUP_FACT_CONFLICT_UNDER_REVIEW";actions=reviewActions();}
                else{state="TOPUP_PROCESSING";message="TOPUP_PROCESSING_DELIVERY_UNCONFIRMED";actions=progressActions();}}
            case "018"->{codes=codes("TOPUP_INTENT_CREATE_UNKNOWN","TOPUP_INTENT_RESULT_UNKNOWN");query=1;nextPoll=parameter.endsWith("RFC3339")?Instant.parse("2026-08-03T00:05:00Z"):null;}
            case "019"->{boolean rejected=parameter.endsWith("UNKNOWN-TO-REJECTED"),noRegression=parameter.endsWith("FOUND-NO-REGRESSION");String terminalCode=rejected?"TOPUP_INTENT_RESULT_REJECTED":"TOPUP_INTENT_RESULT_FOUND";codes=noRegression?codes("TOPUP_INTENT_CREATE_UNKNOWN","TOPUP_INTENT_RESULT_UNKNOWN",terminalCode,"TOPUP_INTENT_RESULT_FOUND"):codes("TOPUP_INTENT_CREATE_UNKNOWN","TOPUP_INTENT_RESULT_UNKNOWN",terminalCode);responseSequence=noRegression?List.of("TOPUP_INTENT_CREATE_UNKNOWN","TOPUP_INTENT_RESULT_UNKNOWN","TOPUP_INTENT_RESULT_FOUND","TOPUP_INTENT_RESULT_FOUND"):List.of("TOPUP_INTENT_CREATE_UNKNOWN","TOPUP_INTENT_RESULT_UNKNOWN",terminalCode);query=noRegression?3:2;writes=expectedWrites(!rejected,0);submission=rejected?"NOT_COMMITTED":"COMMITTED";review=noRegression?1:0;if(!rejected){state="TOPUP_PROCESSING";message="TOPUP_PROCESSING_DELIVERY_UNCONFIRMED";actions=progressActions();}}
            case "020"->{codes=codes("TOPUP_INTENT_CREATED","TOPUP_INTENT_RESULT_FOUND","TOPUP_PROGRESS_READ");state="TOPUP_PROCESSING";message="TOPUP_PROCESSING_DELIVERY_UNCONFIRMED";actions=progressActions();query=2;writes=expectedWrites(false,0);componentExpected=componentExpected020(parameter);strictThree=true;}
            default->throw new IllegalArgumentException(scenario);
        }
        return new Oracle(scenario,parameter,codes,responseSequence,componentExpected,state,message,actions,facts,responsibility,confirmed,unknown,query,writes,nextPoll,submission,review,strictThree);
    }
    private static Map<String,Integer> codes(String...values){Map<String,Integer> result=new TreeMap<>();for(String value:values)result.merge(value,1,Integer::sum);return result;}
    private static List<String> progressActions(){return List.of("QUERY_ORIGINAL_TOPUP","REFRESH_ORDER_PROJECTION","OPEN_SUPPORT","SAFE_LEAVE");}
    private static List<String> safeReadActions(){return List.of("REFRESH_ORDER_PROJECTION","OPEN_SUPPORT","SAFE_LEAVE");}
    private static List<String> unknownActions(){return List.of("QUERY_ORIGINAL_TOPUP","OPEN_SUPPORT","SAFE_LEAVE");}
    private static List<String> reviewActions(){return List.of("QUERY_ORIGINAL_TOPUP","OPEN_SUPPORT","SAFE_LEAVE");}
    private static Map<String,String> facts(String payment,String upstream,String delivery,String accounting){return Map.of("PAYMENT",payment,"UPSTREAM_DEBIT",upstream,"DELIVERY",delivery,"ACCOUNTING_CLOSURE",accounting);}
    private static Map<String,Long> expectedWrites(boolean committed,int synthetic){Map<String,Long> result=new LinkedHashMap<>();for(String name:WRITES)result.put(name,0L);if(committed)for(String name:List.of("Command","CommandAlias","TopupBusinessKey","TopupSemanticAction","TopupIntent","DispatchSemanticAction","DispatchIntent","OrderVersion","ProjectionVersion"))result.put(name,1L);result.put("SyntheticObservation",(long)synthetic);return Map.copyOf(result);}
    private static Map<String,Long> writes(Map<String,Long> source){Map<String,Long> result=new LinkedHashMap<>();WRITES.forEach(k->result.put(k,source.get(k)));return Map.copyOf(result);}
    private static Map<String,Long> delta(Map<String,Long> before,Map<String,Long> after){Map<String,Long> result=new LinkedHashMap<>();before.forEach((k,v)->result.put(k,after.get(k)-v));return Map.copyOf(result);}
    private static Map<String,Object> map(Object...values){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;}
    private static Map.Entry<String,List<String>> e(String scenario,String...parameters){return Map.entry(scenario,List.of(parameters));}
    private static String required(String key){String value=System.getProperty(key);if(value==null||value.isBlank())throw new IllegalStateException(key+" is required");return value;}
    private static String exact(String key,String prefix){String value=required(key);if(!value.matches("^"+prefix+"[A-Za-z0-9][A-Za-z0-9._-]{7,80}$"))throw new IllegalStateException(key+" invalid");return value;}
    private static void verifyBinding(String key,String expected){assertThat(required(key).toUpperCase(Locale.ROOT)).isEqualTo(expected);}
    private static String sha(Path path)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");return HexFormat.of().withUpperCase().formatHex(md.digest(Files.readAllBytes(path)));}
    private static String shaText(String value)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");return HexFormat.of().withUpperCase().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));}
    private void atomicJson(Path target,Object value)throws Exception{Path tmp=target.resolveSibling(target.getFileName()+".tmp");Files.writeString(tmp,json.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n",StandardCharsets.UTF_8);Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);}
    private static Map<String,String> fixedInputs(String runner){return Map.of("D1",D1,"D2",D2,"D3-03",D303,"D3-04",D304,"D3-05",D305,"D3-REGISTRY",D3REG,"REVIEWED-IMPLEMENTATION",REVIEWED_IMPLEMENTATION,"MATRIX",MATRIX,"RUNNER",runner);}
    private record CaseExecution(Map<String,Object> input,Map<String,Object> expected,Map<String,Object> actual,Object completeResponse,Map<String,Long> before,Map<String,Long> after){}
    private record Oracle(String scenarioId,String parameterId,Map<String,Integer> responseCodeCounts,List<String> responseSequence,Map<String,Object> componentExpected,String stateCode,
                          String messageCode,List<String> allowedActions,Map<String,String> factStates,String responsibilityCode,
                          List<String> confirmedItems,List<String> unknownItems,long queryDelta,Map<String,Long> writeDelta,
                          Instant unknownNextPoll,String submissionState,int reviewSignalCount,boolean strictThreeResponses){
        Map<String,Object> expected(){return map("scenarioId",scenarioId,"parameterId",parameterId,"responseCodeCounts",responseCodeCounts,"responseSequence",responseSequence,"components",componentExpected,
                "stateCode",stateCode,"messageCode",messageCode,"allowedActions",allowedActions,"queryDelta",queryDelta,
                "factStates",factStates,"responsibilityCode",responsibilityCode,"confirmedItems",confirmedItems,"unknownItems",unknownItems,
                "writeDelta",writeDelta,"unknownNextPoll",unknownNextPoll,"submissionState",submissionState,
                "reviewSignalCount",reviewSignalCount,"strictCreateResultProjection",strictThreeResponses);}
        @SuppressWarnings("unchecked") void assertMatches(Map<String,Object> actual){
            assertThat(actual.get("responseCodeCounts")).isEqualTo(responseCodeCounts);assertThat(actual.get("queryDelta")).isEqualTo(queryDelta);
            if(responseSequence!=null)assertThat(actual.get("responseSequence")).isEqualTo(responseSequence);
            assertThat(actual.get("writeDelta")).isEqualTo(writeDelta);
            List<String> states=(List<String>)actual.get("projectionStateCodes"),messages=(List<String>)actual.get("messageCodes");
            List<List<String>> actionSets=(List<List<String>>)actual.get("allowedActionSets");
            if(stateCode==null){assertThat(states).isEmpty();assertThat(messages).isEmpty();assertThat(actionSets).isEmpty();}
            else{assertThat(states).containsExactly(stateCode);assertThat(messages).containsExactly(messageCode);assertThat(actionSets).containsExactly(allowedActions);}
            if(factStates!=null){assertThat(actual.get("projectionFactStates")).isEqualTo(List.of(factStates));assertThat(actual.get("responsibilityCodes")).isEqualTo(List.of(responsibilityCode));assertThat(actual.get("confirmedItemSets")).isEqualTo(List.of(confirmedItems));assertThat(actual.get("unknownItemSets")).isEqualTo(List.of(unknownItems));}
            List<Instant> polls=(List<Instant>)actual.get("unknownNextPollValues");if(unknownNextPoll==null)assertThat(polls).allMatch(Objects::isNull);else assertThat(polls).allMatch(unknownNextPoll::equals);
            if(submissionState!=null)assertThat(actual.get("submissionState")).isEqualTo(submissionState);
            assertThat(actual.getOrDefault("reviewSignalCount",0)).isEqualTo(reviewSignalCount);
            if(scenarioId.contains("-017-")){String prior=parameterId.endsWith("D-LATE-BEFORE-U")?"SUPPORT_REVIEW":"TOPUP_PROCESSING";assertThat(actual.get("priorStateCode")).isEqualTo(prior);assertThat(actual.get("finalStateCode")).isEqualTo(stateCode);}
            if(strictThreeResponses){Map<String,Object> components=(Map<String,Object>)actual.get("componentEvidence");assertThat(components.keySet()).containsExactlyInAnyOrder("create","result","projection");componentExpected.forEach((name,expected)->{Map<String,Object> component=(Map<String,Object>)components.get(name),observed=(Map<String,Object>)component.get("Actual"),expectedMap=(Map<String,Object>)expected,expectedEndpoint=(Map<String,Object>)expectedMap.get("endpointRead"),expectedValidation=(Map<String,Object>)expectedMap.get("validation"),actualEndpoint=(Map<String,Object>)observed.get("endpointRead"),actualValidation=(Map<String,Object>)observed.get("validation"),endpointWindow=(Map<String,Object>)component.get("EndpointRead"),validationWindow=(Map<String,Object>)component.get("Validation");assertThat(component.get("Expected")).isEqualTo(expected);assertThat(observed.get("responseType")).isEqualTo(expectedMap.get("responseType"));assertThat(observed.get("projectCode")).isEqualTo(expectedMap.get("projectCode"));assertThat(observed.get("responseFieldCount")).isEqualTo(8);assertThat(actualEndpoint).isEqualTo(expectedEndpoint);assertThat(actualValidation.get("mutationRejected")).isEqualTo(true);assertThat(actualValidation.get("queryDelta")).isEqualTo(0L);assertThat(actualValidation.get("writeDelta")).isEqualTo(expectedWrites(false,0));assertThat(((Map<?,?>)endpointWindow.get("Delta"))).isEqualTo(expectedEndpoint.get("writeDelta"));assertThat(((Map<?,?>)validationWindow.get("Delta"))).isEqualTo(expectedWrites(false,0));assertThat(((Map<?,?>)validationWindow.get("QueryCall")).get("delta")).isEqualTo(0L);});}
        }
    }

    private static final class F {
        String payment="CONFIRMED",mnp="ELIGIBLE",u="NOT_OBSERVED",d="UNKNOWN",l="NOT_OBSERVED";
        String payVer="PAY-V1",mnpVer="MNP-V1",catalog="CAT-V1",supportSet="SET-V1",support="SUPPORT-P014-1",priceDigest;
        boolean catalogCurrent=true,supportCurrent=true,allowed=true,duplicate=false;long pv=1,av=1;
        UnknownAgeDecision age=UnknownAgeDecision.WITHIN_LOCAL_WINDOW;ResultState result=ResultState.FOUND;Instant nextPoll;
        F payment(String v){payment=v;return this;}F mnp(String v){mnp=v;return this;}F u(String v){u=v;return this;}F d(String v){d=v;return this;}F l(String v){l=v;return this;}
        F versions(long v){pv=v;av=v;return this;}F age(UnknownAgeDecision v){age=v;return this;}F result(ResultState v){result=v;return this;}F nextPoll(Instant v){nextPoll=v;return this;}
        F decisionVersions(String p,String m){payVer=p;mnpVer=m;return this;}F catalog(String v,boolean current){catalog=v;catalogCurrent=current;return this;}F duplicate(boolean v){duplicate=v;return this;}F priceDigest(String v){priceDigest=v;return this;}
        Fixture build(){Instant now=Instant.parse("2026-08-03T00:00:00Z");var price=new PriceSnapshotSummary("PS-1",125000,"BDT","DISPLAY-V1","******1234","SYN Operator","SYN Package",100000,"BDT",now.plusSeconds(3600));return new Fixture(ENVIRONMENT,REALITY_LEVEL,"ORDER-P014-1",ID.projectSubjectRef(),ID.sessionRef(),"BUYER",1,"AUTHSET-P014-1","AUTH-EVIDENCE-V1",List.of("ORDER-P014-1"),ORDER_STATE,pv,av,price,priceDigest==null?P014TopupService.snapshotDigest(price):priceDigest,"PAY-DEC-1",payVer,payment,"MNP-DEC-1",mnpVer,mnp,catalog,supportSet,catalogCurrent,supportCurrent,allowed,u,d,l,age,support,duplicate,result,nextPoll,now);}
    }
}
