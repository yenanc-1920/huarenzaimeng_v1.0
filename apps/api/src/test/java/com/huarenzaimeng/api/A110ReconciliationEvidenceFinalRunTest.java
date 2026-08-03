package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {"spring.profiles.active=mock", "hz.a110.mode=local-synthetic",
        "hz.test-access-token=a110-local-synthetic-token"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "a110.evidence.finalRun", matches = "true")
class A110ReconciliationEvidenceFinalRunTest {
    private static final String TOKEN = "a110-local-synthetic-token";
    private static final String SUBJECT = LocalSyntheticIdentity.fromToken(TOKEN).projectSubjectRef();
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
    private static final Set<String> TOP = Set.of("requestRef", "viewState", "projectCode", "schemaVersion",
            "roleProjection", "roleBindingVersion", "authorizationDecisionVersion", "projectionVersion", "items",
            "allowedActions", "retryClass");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired A110ReconciliationService service;

    @Test void ADM02_A110_001_FIN_READY() throws Exception { run("ADM02-A110-001-FIN-READY", false, fin("READY", List.of(finReady())), "READY", false); }
    @Test void ADM02_A110_002_CS_READY() throws Exception { run("ADM02-A110-002-CS-READY", false, cs("READY", List.of(csReady())), "READY", false); }
    @Test void ADM02_A110_003_FIN_EMPTY() throws Exception { run("ADM02-A110-003-FIN-EMPTY", false, fin("EMPTY", List.of()), "EMPTY", true); }
    @Test void ADM02_A110_004_CS_EMPTY() throws Exception { run("ADM02-A110-004-CS-EMPTY", false, cs("EMPTY", List.of()), "EMPTY", true); }
    @Test void ADM02_A110_006_READ_ERROR() throws Exception { run("ADM02-A110-006-READ-ERROR", true, fin("READ_ERROR", List.of()), "READ_ERROR", true); }
    @Test void ADM02_A110_007_UNAVAILABLE() throws Exception { run("ADM02-A110-007-UNAVAILABLE", true, fin("UNAVAILABLE", List.of()), "UNAVAILABLE", true); }
    @Test void ADM02_A110_008_CONTENT_ACCESS_DENIED() throws Exception { run("ADM02-A110-008-CONTENT-ACCESS-DENIED", false, fixture("CONTENT", "AUTHORIZED", "READY", SUBJECT, List.of()), "ACCESS_DENIED", true); }

    @Test void ADM02_A110_009_CROSS_ROLE_EXISTENCE_SAME_SHAPE() throws Exception {
        String id = "ADM02-A110-009-CROSS-ROLE-EXISTENCE-SAME-SHAPE";
        BranchEvidence nonexistentBranch = deniedBranch(fin("ACCESS_DENIED", List.of()), TOKEN);
        BranchEvidence contentBranch = deniedBranch(fixture("CONTENT", "AUTHORIZED", "READY", SUBJECT, List.of()), TOKEN);
        BranchEvidence crossBranch = deniedBranch(fixture("FIN", "AUTHORIZED", "READY", "SYN-OTHER", List.of(finReady())), TOKEN);
        BranchEvidence unauthenticatedBranch = deniedBranch(fin("READY", List.of(finReady())), null);
        JsonNode nonexistent = nonexistentBranch.response();
        JsonNode content = contentBranch.response();
        JsonNode cross = crossBranch.response();
        JsonNode unauthenticated = unauthenticatedBranch.response();
        for (JsonNode response : List.of(nonexistent, content, cross, unauthenticated)) denied(response);
        A110ReconciliationSnapshot before = nonexistentBranch.before();
        Map<String, Long> combinedAfter = new LinkedHashMap<>(unauthenticatedBranch.after().counters());
        combinedAfter.put("QueryCall", 4L);
        A110ReconciliationSnapshot after = new A110ReconciliationSnapshot(
                unauthenticatedBranch.after().fixtureRevision(), Map.copyOf(combinedAfter));
        Map<String, Object> actual = Map.of("responses", List.of(nonexistent, content, cross, unauthenticated),
                "branchQueryDeltas", List.of(1, 1, 1, 1), "sameShape", true, "leakCount", 0);
        write(id, false, Map.of("parameterSet", "CONTENT_UNAUTH_CROSS_NONEXISTENT"),
                Map.of("viewState", "ACCESS_DENIED", "sameShape", true, "leakCount", 0), actual, before, after, 4);
    }

    @Test void ADM02_A110_010_AUTHORITY_UNKNOWN() throws Exception { run("ADM02-A110-010-AUTHORITY-UNKNOWN", false, fixture("FIN", "UNKNOWN", "READY", SUBJECT, List.of(finReady())), "AUTHORITY_UNKNOWN", true); }
    @Test void ADM02_A110_011_REVOKED() throws Exception { run("ADM02-A110-011-REVOKED", false, fixture("FIN", "REVOKED", "READY", SUBJECT, List.of(finReady())), "REVOKED", true); }
    @Test void ADM02_A110_012_VERSION_CONFLICT() throws Exception { run("ADM02-A110-012-VERSION-CONFLICT", true, fin("VERSION_CONFLICT", List.of()), "VERSION_CONFLICT", true); }
    @Test void ADM02_A110_013_LONG_RUNNING_UNKNOWN() throws Exception { run("ADM02-A110-013-LONG-RUNNING-UNKNOWN", false, fin("LONG_RUNNING_UNKNOWN", List.of(longUnknown())), "LONG_RUNNING_UNKNOWN", false); }
    @Test void ADM02_A110_014_W_U_ASYMMETRIC() throws Exception { run("ADM02-A110-014-W-U-ASYMMETRIC", false, fin("ASYMMETRIC_FACTS", List.of(wuAsymmetric())), "ASYMMETRIC_FACTS", false); }
    @Test void ADM02_A110_015_U_D_ASYMMETRIC() throws Exception { run("ADM02-A110-015-U-D-ASYMMETRIC", false, fin("ASYMMETRIC_FACTS", List.of(udAsymmetric())), "ASYMMETRIC_FACTS", false); }
    @Test void ADM02_A110_016_R_L_ACCOUNTING_INCOMPLETE() throws Exception { run("ADM02-A110-016-R-L-ACCOUNTING-INCOMPLETE", false, fin("ASYMMETRIC_FACTS", List.of(rlIncomplete())), "ASYMMETRIC_FACTS", false); }
    @Test void ADM02_A110_017_REFUND_DELIVERY_CONFLICT() throws Exception { run("ADM02-A110-017-REFUND-DELIVERY-CONFLICT", false, fin("REFUND_DELIVERY_CONFLICT", List.of(refundDeliveryConflict())), "REFUND_DELIVERY_CONFLICT", false); }
    @Test void ADM02_A110_018_READ_REFRESH_ZERO_WRITE() throws Exception { run("ADM02-A110-018-READ-REFRESH-ZERO-WRITE", false, fin("READY", List.of(finReady())), "READY", false); }

    @Test void ADM02_A110_020_STRICT_DTO_ZERO_SIDE_EFFECT() throws Exception {
        String id = "ADM02-A110-020-STRICT-DTO-ZERO-SIDE-EFFECT";
        service.installFixtureForTest(fin("READY", List.of(finReady())));
        A110ReconciliationSnapshot before = service.snapshotForTest();
        JsonNode response = read(TOKEN);
        A110ReconciliationSnapshot after = service.snapshotForTest();
        Map<String, Boolean> rejected = new LinkedHashMap<>();
        ObjectNode missing = response.deepCopy(); missing.remove("requestRef"); rejected.put("missing", !strict(missing));
        ObjectNode added = response.deepCopy(); added.put("unexpected", true); rejected.put("additional", !strict(added));
        ObjectNode unknown = response.deepCopy(); unknown.put("viewState", "UNKNOWN_ENUM"); rejected.put("unknown", !strict(unknown));
        ObjectNode type = response.deepCopy(); type.put("projectionVersion", "1"); rejected.put("type", !strict(type));
        ObjectNode mapping = response.deepCopy(); mapping.put("projectCode", "A110_EMPTY"); rejected.put("mapping", !strict(mapping));
        assertThat(rejected.values()).allMatch(Boolean::booleanValue);
        write(id, true, Map.of("mutations", rejected.keySet()), Map.of("allRejected", true),
                Map.of("baselineResponse", response, "rejected", rejected), before, after, 1);
    }

    private void run(String id, boolean half, A110ReconciliationFixture fixture, String expectedState,
                     boolean expectEmptyItems) throws Exception {
        service.installFixtureForTest(fixture);
        A110ReconciliationSnapshot before = service.snapshotForTest();
        JsonNode response = read(TOKEN);
        A110ReconciliationSnapshot after = service.snapshotForTest();
        assertThat(response.path("viewState").asText()).isEqualTo(expectedState);
        assertThat(response.path("projectCode").asText()).isEqualTo("A110_" + expectedState);
        assertThat(response.path("items").isEmpty()).isEqualTo(expectEmptyItems);
        assertThat(strict(response)).isTrue();
        boolean nonDisclosureState = id.startsWith("ADM02-A110-008-")
                || id.startsWith("ADM02-A110-010-") || id.startsWith("ADM02-A110-011-");
        if (nonDisclosureState) assertThat(response.path("allowedActions").isEmpty()).isTrue();
        write(id, half, Map.of("fixtureRole", fixture.role(), "readOutcome", fixture.readOutcome()),
                Map.of("viewState", expectedState, "strictDto", true, "queryDelta", 1, "writeDelta", 0,
                        "leakCount", 0),
                Map.of("response", response, "strictDto", true, "leakCount", 0), before, after, 1);
    }

    private JsonNode read(String token) throws Exception {
        var request = get("/api/v1/admin/reconciliations");
        if (token != null) request.header("X-HZM-Test-Access-Token", token);
        var result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private BranchEvidence deniedBranch(A110ReconciliationFixture fixture, String token) throws Exception {
        service.installFixtureForTest(fixture);
        A110ReconciliationSnapshot before = service.snapshotForTest();
        JsonNode response = read(token);
        A110ReconciliationSnapshot after = service.snapshotForTest();
        Map<String, Long> branchDelta = delta(before.counters(), after.counters());
        assertThat(branchDelta.get("QueryCall")).isOne();
        branchDelta.forEach((name, value) -> {
            if (!"QueryCall".equals(name)) assertThat(value).as(name).isZero();
        });
        return new BranchEvidence(response, before, after);
    }

    private void write(String id, boolean half, Object input, Object expected, Object actual,
                       A110ReconciliationSnapshot before, A110ReconciliationSnapshot after, long queries) throws Exception {
        Map<String, Long> delta = delta(before.counters(), after.counters());
        assertThat(delta.get("QueryCall")).isEqualTo(queries);
        delta.forEach((name, value) -> { if (!"QueryCall".equals(name)) assertThat(value).as(name).isZero(); });
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("EvidencePackageRef", "A110-BE-" + id);
        payload.put("ScenarioId", id);
        payload.put("SubcaseId", id);
        payload.put("ParameterId", "BACKEND_LOCAL_SYNTHETIC");
        payload.put("RunId", required("a110.evidence.runId"));
        payload.put("ExecutedAt", Instant.now().toString());
        payload.put("FixedAcceptanceSha256", required("a110.evidence.acceptanceSha"));
        payload.put("BackendImplementationAggregateSha256", required("a110.evidence.backendSha"));
        payload.put("RunnerSourceSha256", required("a110.evidence.runnerSha"));
        payload.put("Scope", "LOCAL_SYNTHETIC_READ_ONLY");
        payload.put("ComponentExecutionStatus", "PASS");
        payload.put("ExecutionStatus", half ? "BLOCKED" : "PASS");
        payload.put("BlockedReason", half ? "PENDING_FRONTEND_HALF" : null);
        payload.put("ConsumableForBackendComponent", true);
        payload.put("ConsumableForWholeScenario", !half);
        payload.put("Input", input);
        payload.put("Expected", expected);
        payload.put("Actual", actual);
        payload.put("Before", before.counters());
        payload.put("After", after.counters());
        payload.put("Delta", delta);
        payload.put("Process", Map.of("commandRef", "process/process-evidence.json", "stdoutRef", "process/stdout.log",
                "stderrRef", "process/stderr.log", "exitRef", "process/process-evidence.json"));
        Path dir = Path.of(required("a110.evidence.outputDir"), "cases");
        Files.createDirectories(dir);
        Path target = dir.resolve(id + ".json");
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, json.writerWithDefaultPrettyPrinter().writeValueAsString(payload) + "\n",
                StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Map<String, Long> delta(Map<String, Long> before, Map<String, Long> after) {
        Map<String, Long> result = new LinkedHashMap<>();
        before.forEach((key, value) -> result.put(key, after.get(key) - value));
        return result;
    }

    private static boolean strict(JsonNode body) {
        if (!fieldNames(body).equals(TOP) || !body.path("schemaVersion").asText().equals("A110_RECONCILIATION_READ_V1")
                || !body.path("projectCode").asText().equals("A110_" + body.path("viewState").asText())
                || !body.path("requestRef").isTextual() || body.path("requestRef").asText().isBlank()
                || !(body.path("projectionVersion").isIntegralNumber() || body.path("projectionVersion").isNull())) return false;
        return Set.of("READY", "EMPTY", "READ_ERROR", "UNAVAILABLE", "ACCESS_DENIED", "AUTHORITY_UNKNOWN",
                "REVOKED", "VERSION_CONFLICT", "LONG_RUNNING_UNKNOWN", "ASYMMETRIC_FACTS",
                "REFUND_DELIVERY_CONFLICT").contains(body.path("viewState").asText());
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>(); node.fieldNames().forEachRemaining(names::add); return names;
    }

    private static void denied(JsonNode response) {
        assertThat(response.path("viewState").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(response.path("items").isEmpty()).isTrue();
        assertThat(response.path("allowedActions").isEmpty()).isTrue();
    }

    private static String required(String name) {
        String value = System.getProperty(name); if (value == null || value.isBlank()) throw new IllegalStateException(name); return value;
    }

    private static A110ReconciliationFixture fin(String outcome, List<?> items) { return fixture("FIN", "AUTHORIZED", outcome, SUBJECT, items); }
    private static A110ReconciliationFixture cs(String outcome, List<?> items) { return fixture("CS", "AUTHORIZED", outcome, SUBJECT, items); }
    private static A110ReconciliationFixture fixture(String role, String auth, String outcome, String subject, List<?> items) {
        return new A110ReconciliationFixture(true, "LOCAL_SYNTHETIC", subject, role, "SYN-RBV-1",
                A110ReconciliationService.READ_SCOPE, "SYN-ADV-1", auth, 1L, outcome, items);
    }

    private static A110FinItem finReady() { return finItem(cleanFacts(), List.of(), "CURRENT", NOW.plusSeconds(3600)); }
    private static A110FinItem longUnknown() { Map<String,A110FactSummary> f=unknownFacts(); return finItem(f,List.of(),"LONG_RUNNING",null); }
    private static A110FinItem wuAsymmetric() { Map<String,A110FactSummary> f=new LinkedHashMap<>(cleanFacts());f.put("U",fact("UNKNOWN",null,null,NOW.minusSeconds(60)));return finItem(f,List.of("MISSING"),"CURRENT",NOW.plusSeconds(3600)); }
    private static A110FinItem udAsymmetric() { Map<String,A110FactSummary> f=new LinkedHashMap<>(cleanFacts());f.put("D",fact("UNKNOWN",null,null,NOW.minusSeconds(60)));return finItem(f,List.of("MISSING"),"CURRENT",NOW.plusSeconds(3600)); }
    private static A110FinItem rlIncomplete() { Map<String,A110FactSummary> f=new LinkedHashMap<>(cleanFacts());f.put("R",fact("CONFIRMED",1000L,"BDT",NOW.minusSeconds(30)));f.put("L",fact("PENDING_OR_INFLIGHT",null,null,NOW.minusSeconds(20)));return finItem(f,List.of("ACCOUNTING_INCOMPLETE"),"CURRENT",null); }
    private static A110FinItem refundDeliveryConflict() { Map<String,A110FactSummary> f=new LinkedHashMap<>(cleanFacts());f.put("R",fact("CONFIRMED",1000L,"BDT",NOW.minusSeconds(120)));f.put("D",fact("CONFIRMED",1000L,"BDT",NOW.minusSeconds(60)));return finItem(f,List.of("REFUND_DELIVERY_CONFLICT"),"CURRENT",NOW.plusSeconds(3600)); }
    private static Map<String,A110FactSummary> cleanFacts() { return Map.of("W",fact("CONFIRMED",1000L,"BDT",NOW.minusSeconds(180)),"U",fact("CONFIRMED",1000L,"BDT",NOW.minusSeconds(120)),"D",fact("CONFIRMED",1000L,"BDT",NOW.minusSeconds(60)),"R",fact("ABSENT_CONFIRMED",null,null,null),"L",fact("CONFIRMED",1000L,"BDT",NOW)); }
    private static Map<String,A110FactSummary> unknownFacts() { return Map.of("W",fact("UNKNOWN",null,null,NOW.minusSeconds(180)),"U",fact("UNKNOWN",null,null,NOW.minusSeconds(120)),"D",fact("UNKNOWN",null,null,NOW.minusSeconds(60)),"R",fact("ABSENT_CONFIRMED",null,null,null),"L",fact("UNKNOWN",null,null,NOW)); }
    private static A110FactSummary fact(String state,Long amount,String currency,Instant occurred){return new A110FactSummary(state,amount,currency,occurred,NOW);}
    private static A110FinItem finItem(Map<String,A110FactSummary> facts,List<String> differences,String age,Instant next){return new A110FinItem("SYN-REC-001","SYN-ORDER-001",null,facts,differences,age,"FIN_REVIEW",List.of(),next,NOW,1L,"SYN-DISPLAY-V1");}

    private record BranchEvidence(JsonNode response, A110ReconciliationSnapshot before,
                                  A110ReconciliationSnapshot after) {}
    private static A110CsItem csReady(){return new A110CsItem("SYN-REC-001","SYN-SUPPORT-001","SYN-ORDER-001","已脱敏主体","合成只读核对",List.of("PAYMENT_CONFIRMED"),List.of(),"CS_REVIEW",null,NOW,1L);}
}
