package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class P021ControlledEvidenceJavaHostTest {
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private static final String HOST_SHA="B".repeat(64);

    @Test void preparationEntryIsBoundAndFormalExecutionRemainsLocked() throws Exception {
        assertThat(P021ControlledEvidenceJavaHost.EXECUTION_SCOPE).isEqualTo("P021_TECHNICAL_EVIDENCE_18_SCENARIO_38_PARAMETER");
        assertThat(P021ControlledEvidenceJavaHost.RUNNER_AGGREGATE_SHA).isEqualTo("7E2B711DF2F4D6621F7A5CD44CA07CC5BB3C032653C61392354205C1214F2B3E");
        assertThat(P021ControlledEvidenceJavaHost.SCENARIO_COUNT).isEqualTo(18);
        assertThat(P021ControlledEvidenceJavaHost.PARAMETER_COUNT).isEqualTo(38);
        assertThat(P021ControlledEvidenceJavaHost.hostPreflightStatus()).contains("FormalTestStarted=false", "EvidenceWritten=false",
                "FORMAL_ENTRY_LOCKED_PENDING_FIXED_SHA_REVIEW_AND_NEW_AUTHORIZATION");
        List<String> command=P021ControlledEvidenceJavaHost.formalCommand("P021-BE-NEW_FINAL_000","AUTH","A".repeat(64),"C".repeat(64),temp);
        assertThat(command).containsExactly("mvn","-pl","apps/api","-am","-Dtest=P021OrderDetailEvidenceFinalRunTest",
                "-Dsurefire.failIfNoSpecifiedTests=false","-Dp021.evidence.confirm=FINAL_RUN","-Dp021.evidence.runId=P021-BE-NEW_FINAL_000",
                "-Dp021.evidence.authorizationRef=AUTH","-Dp021.evidence.authorizationRecordSha="+"A".repeat(64),
                "-Dp021.evidence.authorizationConsumptionSha="+"C".repeat(64),
                "-Dp021.evidence.staging="+temp.toAbsolutePath().normalize(),"test");
        assertThat(String.join(" ",command)).doesNotContain("ExecutionPolicy","Bypass","Invoke-Expression","http://","https://");
        assertThatThrownBy(()->P021ControlledEvidenceJavaHost.main(new String[0])).hasMessage("FORMAL_ARGUMENTS");
        assertThat(Files.readString(Path.of("src/test/java/com/huarenzaimeng/api/P021ControlledEvidenceJavaHost.java"))).doesNotContain("FORMAL_ENTRY_ENABLED","System.getenv","System.getProperty");
    }

    @Test void authorizationIsExactSingleUseAndSealedRunIsRejected() throws Exception {
        Path auth = temp.resolve("auth.json");
        Instant now = Instant.parse("2026-08-03T11:00:00Z");
        Files.writeString(auth, authorization("P021-BE-NEW-FINAL-001", now), StandardCharsets.UTF_8);
        String sha = sha(auth);
        var accepted = P021ControlledEvidenceJavaHost.validateAuthorization(auth, temp, sha, "P021-BE-NEW-FINAL-001",HOST_SHA,P021ControlledEvidenceJavaHost.formalCommandDigest(), now);
        P021ControlledEvidenceJavaHost.consumeOnce(accepted, now);
        assertThat(Files.exists(accepted.marker())).isTrue();
        assertThatThrownBy(() -> P021ControlledEvidenceJavaHost.consumeOnce(accepted, now)).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThatThrownBy(() -> P021ControlledEvidenceJavaHost.validateAuthorization(auth, temp, sha,
                P021ControlledEvidenceJavaHost.SEALED_RUN_ID,HOST_SHA,P021ControlledEvidenceJavaHost.formalCommandDigest(), now)).hasMessage("SEALED_RUN_ID");
        Path outside = Files.createDirectory(temp.resolve("outside")).resolve("auth.json");
        Files.writeString(outside, authorization("P021-BE-NEW-FINAL-001", now));
        assertThatThrownBy(() -> P021ControlledEvidenceJavaHost.validateAuthorization(outside, temp, sha(outside),
                "P021-BE-NEW-FINAL-001",HOST_SHA,P021ControlledEvidenceJavaHost.formalCommandDigest(), now)).hasMessage("AUTHORIZATION_LOCATION");
        assertThatThrownBy(() -> P021ControlledEvidenceJavaHost.validateAuthorization(auth,temp,sha,"P021-BE-NEW-FINAL-001","C".repeat(64),P021ControlledEvidenceJavaHost.formalCommandDigest(),now)).hasMessage("AUTHORIZATION_BINDING");
        assertThatThrownBy(() -> P021ControlledEvidenceJavaHost.validateAuthorization(auth,temp,sha,"P021-BE-NEW-FINAL-001",HOST_SHA,"D".repeat(64),now)).hasMessage("AUTHORIZATION_BINDING");
        assertThat(Files.readString(Path.of("src/test/java/com/huarenzaimeng/api/P021ControlledEvidenceJavaHost.java"))).contains("channel.force(true)");
    }

    @Test void temporaryPublicationWritesReadyLastAndMovesAtomically() throws Exception {
        var paths = P021ControlledEvidenceJavaHost.Paths.forRun(temp, "P021-BE-NEW-FINAL-002");
        P021ControlledEvidenceJavaHost.assertFresh(paths);
        Files.createDirectories(paths.staging());
        Instant now=Instant.parse("2026-08-03T11:00:00Z");Path authFile=temp.resolve("auth-publish.json");
        Files.writeString(authFile,authorization("P021-BE-NEW-FINAL-002",now));
        var authorization=P021ControlledEvidenceJavaHost.validateAuthorization(authFile,temp,sha(authFile),"P021-BE-NEW-FINAL-002",HOST_SHA,P021ControlledEvidenceJavaHost.formalCommandDigest(),now);
        P021ControlledEvidenceJavaHost.consumeOnce(authorization,now);var bindings=bindings(authorization,authFile);
        writeValidCases(paths.staging(),"P021-BE-NEW-FINAL-002",authorization.ref(),authFile);
        Files.createDirectories(paths.process());Files.writeString(paths.process().resolve("process.stdout.txt"),"stdout");Files.writeString(paths.process().resolve("process.stderr.txt"),"stderr");
        String command=String.join(" ",P021ControlledEvidenceJavaHost.formalCommand("P021-BE-NEW-FINAL-002",authorization.ref(),sha(authFile),bindings.authorizationConsumptionSha(),paths.staging()));
        var process = process(command,paths,bindings,0,true);
        stampCases(paths.staging(),bindings);
        P021ControlledEvidenceJavaHost.publishPreparedPackage(paths, "P021-BE-NEW-FINAL-002", authorization, process,bindings,stage->{});
        assertThat(paths.finalDir().resolve("READY")).exists();
        assertThat(paths.publishing()).doesNotExist();
        assertThat(paths.finalDir().resolve("manifest.json")).content().contains("\"Consumable\":true", "\"ScenarioCount\":18", "\"ParameterCount\":38",
                "\"JavaHostAggregateSha\":\""+HOST_SHA+"\"","\"AuthorizationRef\":\"AUTH-NEW\"","\"FixedInputs\"");
        try(var files=Files.list(paths.finalDir())){for(Path file:files.filter(path->path.getFileName().toString().startsWith("ORD03-")).toList())assertThat(Files.readString(file)).contains("\"Consumable\":true","\"ExecutionStatus\":\"PASS\"");}
        assertThat(paths.process()).doesNotExist();
    }

    @Test void temporaryFailureAtomicallyIsolatesAndRevokesEveryJson() throws Exception {
        var paths = P021ControlledEvidenceJavaHost.Paths.forRun(temp, "P021-BE-NEW-FINAL-003");
        Files.createDirectories(paths.publishing().resolve("nested"));
        Files.writeString(paths.publishing().resolve("READY"), "READY");
        Files.writeString(paths.publishing().resolve("nested/case.json"), "{\"Consumable\":true,\"ExecutionStatus\":\"PASS\"}");
        P021ControlledEvidenceJavaHost.blockAndIsolate(paths, "TEST_FAILURE");
        assertThat(paths.publishing()).doesNotExist();
        assertThat(paths.finalDir()).doesNotExist();
        Path isolated = paths.blocked().resolve("NONCONSUMABLE-" + paths.publishing().getFileName());
        P021ControlledEvidenceJavaHost.assertNonConsumable(isolated);
        assertThat(paths.blocked().resolve("BLOCKED.json")).content().contains("\"Consumable\":false", "\"AutomaticRetryAllowed\":false");
    }

    @Test void processFailureCannotPublishReady() throws Exception {
        var paths = P021ControlledEvidenceJavaHost.Paths.forRun(temp, "P021-BE-NEW-FINAL-004");
        Files.createDirectories(paths.staging());
        var process = new P021ControlledEvidenceJavaHost.ProcessEvidence("LOCAL_TEST_ONLY", Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1), 1, "A", "B", true, false,HOST_SHA,"X","Y","Z","M","N");
        Path authFile=temp.resolve("auth-fail.json");Instant now=Instant.parse("2026-08-03T11:00:00Z");Files.writeString(authFile,authorization("P021-BE-NEW-FINAL-004",now));
        var authorization=P021ControlledEvidenceJavaHost.validateAuthorization(authFile,temp,sha(authFile),"P021-BE-NEW-FINAL-004",HOST_SHA,P021ControlledEvidenceJavaHost.formalCommandDigest(),now);
        P021ControlledEvidenceJavaHost.consumeOnce(authorization,now);var bindings=bindings(authorization,authFile);
        var boundProcess=new P021ControlledEvidenceJavaHost.ProcessEvidence(process.command(),process.startedAt(),process.endedAt(),process.osExitCode(),process.stdoutSha(),process.stderrSha(),process.wrapperLoaded(),process.formalTestStarted(),HOST_SHA,P021ControlledEvidenceJavaHost.formalCommandDigest(),authorization.ref(),sha(authFile),bindings.authorizationConsumptionRef(),bindings.authorizationConsumptionSha());
        assertThatThrownBy(() -> P021ControlledEvidenceJavaHost.publishPreparedPackage(paths, "P021-BE-NEW-FINAL-004", authorization, boundProcess,bindings,stage->{}))
                .hasMessage("PROCESS_EVIDENCE_INVALID");
        assertThat(paths.finalDir()).doesNotExist();
        assertThat(paths.publishing()).doesNotExist();
    }

    @Test void malformedJsonStillLeavesFormalNamespaceAndRemovesReadyBeforeFailing() throws Exception {
        var paths = P021ControlledEvidenceJavaHost.Paths.forRun(temp, "P021-BE-NEW-FINAL-005");
        Files.createDirectories(paths.finalDir());
        Files.writeString(paths.finalDir().resolve("READY"), "READY");
        Files.writeString(paths.finalDir().resolve("broken.json"), "not-json");
        P021ControlledEvidenceJavaHost.blockAndIsolate(paths, "MALFORMED");
        assertThat(paths.finalDir()).doesNotExist();
        Path isolated = paths.blocked().resolve("NONCONSUMABLE-" + paths.finalDir().getFileName());
        assertThat(isolated).exists();
        assertThat(isolated.resolve("READY")).doesNotExist();
        assertThat(isolated.resolve("corrupt/broken.json.raw.NONCONSUMABLE")).exists();
        assertThat(isolated.resolve("corrupt/broken.json.blocked.json")).content().contains("CORRUPT_JSON_ISOLATED", "OriginalSha256");
    }

    @Test void strictRunIdAndFormalRootRejectTraversal() {
        assertThatThrownBy(() -> P021ControlledEvidenceJavaHost.Paths.forFormalRun(temp, "../escape")).hasMessage("RUN_ID_INVALID");
        assertThatThrownBy(() -> P021ControlledEvidenceJavaHost.formalCommand("P021-BE-../../escape", "A", "B","C", temp)).hasMessage("RUN_ID_INVALID");
        var paths=P021ControlledEvidenceJavaHost.Paths.forFormalRun(temp,"P021-BE-NEW_FINAL_006");
        assertThat(paths.root()).isEqualTo(temp.toAbsolutePath().normalize().resolve(P021ControlledEvidenceJavaHost.FIXED_EVIDENCE_ROOT));
        assertThat(paths.finalDir().normalize().startsWith(paths.root().normalize())).isTrue();
    }

    @Test void counterArithmeticFailureStopsBeforePublishing() throws Exception {
        var paths=P021ControlledEvidenceJavaHost.Paths.forRun(temp,"P021-BE-NEW-FINAL-007");Files.createDirectories(paths.staging());
        Instant now=Instant.parse("2026-08-03T11:00:00Z");Path authFile=temp.resolve("auth-counter.json");Files.writeString(authFile,authorization("P021-BE-NEW-FINAL-007",now));
        var authorization=P021ControlledEvidenceJavaHost.validateAuthorization(authFile,temp,sha(authFile),"P021-BE-NEW-FINAL-007",HOST_SHA,P021ControlledEvidenceJavaHost.formalCommandDigest(),now);
        P021ControlledEvidenceJavaHost.consumeOnce(authorization,now);var bindings=bindings(authorization,authFile);
        writeValidCases(paths.staging(),"P021-BE-NEW-FINAL-007",authorization.ref(),authFile);
        stampCases(paths.staging(),bindings);
        Path first;try(var files=Files.list(paths.staging())){first=files.findFirst().orElseThrow();}
        ObjectNode broken=(ObjectNode)json.readTree(Files.readString(first));broken.with("Delta").put("QueryCall",2);Files.writeString(first,json.writeValueAsString(broken));
        Files.createDirectories(paths.process());Files.writeString(paths.process().resolve("process.stdout.txt"),"stdout");Files.writeString(paths.process().resolve("process.stderr.txt"),"stderr");
        String command=String.join(" ",P021ControlledEvidenceJavaHost.formalCommand("P021-BE-NEW-FINAL-007",authorization.ref(),sha(authFile),bindings.authorizationConsumptionSha(),paths.staging()));
        var process=process(command,paths,bindings,0,true);
        assertThatThrownBy(()->P021ControlledEvidenceJavaHost.publishPreparedPackage(paths,"P021-BE-NEW-FINAL-007",authorization,process,bindings,stage->{})).hasMessage("COUNTER_ARITHMETIC");
        assertThat(paths.publishing()).doesNotExist();assertThat(paths.finalDir()).doesNotExist();
    }

    @Test void everyInjectedStageFailureRevokesAllTemporaryNamespacesAndReady() throws Exception {
        for(P021ControlledEvidenceJavaHost.Stage failed:P021ControlledEvidenceJavaHost.Stage.values()){
            Path root=Files.createDirectory(temp.resolve("fault-"+failed));String runId="P021-BE-FAULT_"+failed.name();
            var paths=P021ControlledEvidenceJavaHost.Paths.forRun(root,runId);Instant now=Instant.parse("2026-08-03T11:00:00Z");Path auth=root.resolve("auth.json");Files.writeString(auth,authorization(runId,now));
            var request=new P021ControlledEvidenceJavaHost.ExecutionRequest(paths,auth,root,sha(auth),runId,HOST_SHA,P021ControlledEvidenceJavaHost.formalCommandDigest(),now);
            P021ControlledEvidenceJavaHost.ProcessLauncher launcher=(command,p,b)->{writeValidCases(p.staging(),runId,"AUTH-NEW",auth);Files.writeString(p.process().resolve("process.stdout.txt"),"stdout");Files.writeString(p.process().resolve("process.stderr.txt"),"stderr");return process(String.join(" ",command),p,b,0,true);};
            assertThatThrownBy(()->P021ControlledEvidenceJavaHost.orchestratePrepared(request,launcher,stage->{if(stage==failed)throw new IllegalStateException("INJECTED_"+failed);}))
                    .hasMessage("INJECTED_"+failed);
            assertThat(paths.staging()).doesNotExist();assertThat(paths.process()).doesNotExist();assertThat(paths.publishing()).doesNotExist();assertThat(paths.finalDir()).doesNotExist();
            assertThat(Files.walk(paths.blocked()).noneMatch(path->path.getFileName().toString().equals("READY"))).isTrue();
        }
    }

    @Test void caseAndConsumptionBindingDriftFailBeforePublishing() throws Exception {
        for(String field:List.of("JavaHostAggregateSha","FormalCommandDigest","AuthorizationRef","AuthorizationRecordSha","AuthorizationConsumptionSha")){
            Path root=Files.createDirectory(temp.resolve("drift-"+field));String runId="P021-BE-DRIFT_"+field;var paths=P021ControlledEvidenceJavaHost.Paths.forRun(root,runId);Files.createDirectories(paths.staging());
            Instant now=Instant.parse("2026-08-03T11:00:00Z");Path auth=root.resolve("auth.json");Files.writeString(auth,authorization(runId,now));var authorization=P021ControlledEvidenceJavaHost.validateAuthorization(auth,root,sha(auth),runId,HOST_SHA,P021ControlledEvidenceJavaHost.formalCommandDigest(),now);P021ControlledEvidenceJavaHost.consumeOnce(authorization,now);var b=bindings(authorization,auth);
            writeValidCases(paths.staging(),runId,authorization.ref(),auth);stampCases(paths.staging(),b);Path first;try(var files=Files.list(paths.staging())){first=files.findFirst().orElseThrow();}ObjectNode changed=(ObjectNode)json.readTree(Files.readString(first));changed.put(field,field.equals("AuthorizationRef")?"OTHER":"E".repeat(64));Files.writeString(first,json.writeValueAsString(changed));
            Files.createDirectories(paths.process());Files.writeString(paths.process().resolve("process.stdout.txt"),"stdout");Files.writeString(paths.process().resolve("process.stderr.txt"),"stderr");String command=String.join(" ",P021ControlledEvidenceJavaHost.formalCommand(runId,authorization.ref(),sha(auth),b.authorizationConsumptionSha(),paths.staging()));var process=process(command,paths,b,0,true);
            assertThatThrownBy(()->P021ControlledEvidenceJavaHost.publishPreparedPackage(paths,runId,authorization,process,b,stage->{})).hasMessage("CASE_BINDING");assertThat(paths.finalDir()).doesNotExist();
        }
        Path root=Files.createDirectory(temp.resolve("marker-drift"));String runId="P021-BE-MARKER_DRIFT";var paths=P021ControlledEvidenceJavaHost.Paths.forRun(root,runId);Files.createDirectories(paths.staging());Instant now=Instant.parse("2026-08-03T11:00:00Z");Path auth=root.resolve("auth.json");Files.writeString(auth,authorization(runId,now));var authorization=P021ControlledEvidenceJavaHost.validateAuthorization(auth,root,sha(auth),runId,HOST_SHA,P021ControlledEvidenceJavaHost.formalCommandDigest(),now);P021ControlledEvidenceJavaHost.consumeOnce(authorization,now);var b=bindings(authorization,auth);Files.writeString(authorization.marker(),"drift");Files.createDirectories(paths.process());Files.writeString(paths.process().resolve("process.stdout.txt"),"stdout");Files.writeString(paths.process().resolve("process.stderr.txt"),"stderr");String command=String.join(" ",P021ControlledEvidenceJavaHost.formalCommand(runId,authorization.ref(),sha(auth),b.authorizationConsumptionSha(),paths.staging()));var process=process(command,paths,b,0,true);assertThatThrownBy(()->P021ControlledEvidenceJavaHost.publishPreparedPackage(paths,runId,authorization,process,b,stage->{})).hasMessage("CONSUMPTION_BINDING_DRIFT");
    }

    private String authorization(String runId, Instant now) {
        return "{\"AuthorizationRef\":\"AUTH-NEW\",\"ExecutionScope\":\"" + P021ControlledEvidenceJavaHost.EXECUTION_SCOPE
                + "\",\"RunId\":\"" + runId + "\",\"ValidFrom\":\"" + now.minusSeconds(60)
                + "\",\"ValidUntil\":\"" + now.plusSeconds(60) + "\",\"ImplementationAggregateSha\":\""
                + P021ControlledEvidenceJavaHost.IMPLEMENTATION_SHA + "\",\"MatrixIdentitySha\":\""
                + P021ControlledEvidenceJavaHost.MATRIX_IDENTITY_SHA + "\",\"RunnerAggregateSha\":\""
                + P021ControlledEvidenceJavaHost.RUNNER_AGGREGATE_SHA + "\",\"JavaHostAggregateSha\":\""+HOST_SHA+"\",\"FormalCommandDigest\":\""+commandDigest()+"\",\"SingleUse\":true,\"Status\":\"APPROVED\"}";
    }
    private String commandDigest(){try{return P021ControlledEvidenceJavaHost.formalCommandDigest();}catch(Exception exception){throw new IllegalStateException(exception);}}
    private P021ControlledEvidenceJavaHost.Bindings bindings(P021ControlledEvidenceJavaHost.Authorization authorization,Path auth)throws Exception{return new P021ControlledEvidenceJavaHost.Bindings(HOST_SHA,P021ControlledEvidenceJavaHost.formalCommandDigest(),authorization.ref(),sha(auth),authorization.marker().getFileName().toString(),sha(authorization.marker()));}
    private P021ControlledEvidenceJavaHost.ProcessEvidence process(String command,P021ControlledEvidenceJavaHost.Paths paths,P021ControlledEvidenceJavaHost.Bindings b,int exit,boolean started)throws Exception{return new P021ControlledEvidenceJavaHost.ProcessEvidence(command,Instant.EPOCH,Instant.EPOCH.plusSeconds(1),exit,sha(paths.process().resolve("process.stdout.txt")),sha(paths.process().resolve("process.stderr.txt")),true,started,b.javaHostAggregateSha(),b.formalCommandDigest(),b.authorizationRef(),b.authorizationRecordSha(),b.authorizationConsumptionRef(),b.authorizationConsumptionSha());}
    private void stampCases(Path staging,P021ControlledEvidenceJavaHost.Bindings b)throws Exception{try(var files=Files.list(staging)){for(Path file:files.filter(path->path.getFileName().toString().endsWith(".json")).toList()){ObjectNode c=(ObjectNode)json.readTree(Files.readString(file));c.put("JavaHostAggregateSha",b.javaHostAggregateSha()).put("FormalCommandDigest",b.formalCommandDigest()).put("AuthorizationRef",b.authorizationRef()).put("AuthorizationRecordSha",b.authorizationRecordSha()).put("AuthorizationConsumptionRef",b.authorizationConsumptionRef()).put("AuthorizationConsumptionSha",b.authorizationConsumptionSha());Files.writeString(file,json.writeValueAsString(c));}}}
    private String sha(Path path) throws Exception {
        return java.util.HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private void writeValidCases(Path staging,String runId,String authorizationRef,Path authorizationFile)throws Exception{
        String wrapper=Files.readString(Path.of("Invoke-P021OrderDetailEvidenceFinalRun.ps1"));
        List<String> identities=Pattern.compile("'([^']+\\|[^']+\\|[^']+)'").matcher(wrapper).results().map(m->m.group(1))
                .filter(value->value.startsWith("ORD03-P021-")).distinct().toList();
        assertThat(identities).hasSize(38);
        Map<String,Long> before=new LinkedHashMap<>(),after=new LinkedHashMap<>(),delta=new LinkedHashMap<>(),writes=new LinkedHashMap<>();
        List<String> writeKeys=List.of("Command","CommandAlias","TopupBusinessKey","TopupSemanticAction","TopupIntent","DispatchSemanticAction","DispatchIntent","OrderVersion","ProjectionVersion","SyntheticObservation","PaymentAttempt","SendAttempt","RemoteAcceptance","WechatPrepay","RequestPayment","Notification","ExternalFact","W","U","D","L","LedgerEntry","ExternalCall");
        writeKeys.forEach(k->{before.put(k,0L);after.put(k,0L);delta.put(k,0L);writes.put(k,0L);});
        for(String k:List.of("QueryCall","FileWrite","QueueWrite","NotificationSend")){before.put(k,0L);after.put(k,k.equals("QueryCall")?1L:0L);delta.put(k,k.equals("QueryCall")?1L:0L);}
        for(String identity:identities){String[]parts=identity.split("\\|");ObjectNode response=json.createObjectNode().put("requestRef","REQ");
            ObjectNode expected=json.createObjectNode().put("projectCode","ORDER_DETAIL_READY").put("stateCode","AWAITING_PAYMENT").put("validatorRejected",false).put("queryCallDelta",1);expected.set("writeDelta23",json.valueToTree(writes));expected.set("completeResponse",response);
            ObjectNode actual=json.createObjectNode().put("projectCode","ORDER_DETAIL_READY").put("stateCode","AWAITING_PAYMENT").put("validatorRejected",false).put("fixtureDigest","F");actual.set("completeRequest",json.createObjectNode());actual.set("completeResponse",response);
            ObjectNode input=json.createObjectNode().put("FixtureDigest","F").put("AuthorizationRef",authorizationRef);input.set("CompleteRequest",json.createObjectNode());input.set("FixedInputs",json.valueToTree(P021OrderDetailEvidenceFinalRunTest.FIXED_INPUTS));
            input.set("ImplementationFiles",json.valueToTree(P021OrderDetailEvidenceFinalRunTest.IMPLEMENTATION_FILES));
            ObjectNode c=json.createObjectNode().put("Consumable",false).put("ExecutionStatus","PASS").put("ScenarioId",parts[0]).put("SubcaseId",parts[1]).put("ParameterId",parts[2]).put("ExecutionRunId",runId).put("AuthorizationRecordRef",authorizationRef).put("AuthorizationRecordSha",sha(authorizationFile)).put("D3RegistrySha","6321D16CAC17D426801F26F5AD643BAB418D08FF72FD299BCEA7FB20C9522A0C").put("ImplementationAggregateSha",P021ControlledEvidenceJavaHost.IMPLEMENTATION_SHA).put("IdentitySetSha",P021ControlledEvidenceJavaHost.MATRIX_IDENTITY_SHA).put("QueryCallDelta",1).put("ProcessEvidenceRef","process-evidence.json");
            c.set("Input",input);c.set("Expected",expected);c.set("Actual",actual);c.set("CompleteResponse",response);c.set("Before",json.valueToTree(before));c.set("After",json.valueToTree(after));c.set("Delta",json.valueToTree(delta));c.set("WriteDelta23",json.valueToTree(writes));
            Files.writeString(staging.resolve(parts[0]+"__"+parts[2]+".json"),json.writeValueAsString(c));}
    }
}
