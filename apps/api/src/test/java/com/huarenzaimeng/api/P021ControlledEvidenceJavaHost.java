package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Preparation-only controlled host. Formal execution stays locked until a new fixed-SHA review and authorization. */
final class P021ControlledEvidenceJavaHost {
    static final String EXECUTION_SCOPE = "P021_TECHNICAL_EVIDENCE_18_SCENARIO_38_PARAMETER";
    static final String SEALED_RUN_ID = "P021-BE-20260803-FINAL-001";
    static final String IMPLEMENTATION_SHA = "C4A8D77A5422354C546473DAF013893952349A447349B3E8CC9F89A884CF0ADE";
    static final String RUNNER_AGGREGATE_SHA = "7E2B711DF2F4D6621F7A5CD44CA07CC5BB3C032653C61392354205C1214F2B3E";
    static final String MATRIX_IDENTITY_SHA = "A0134787E0484705ADE5E32383D778D7851BC2DAD72FE9F6873752CBE639A8DE";
    static final int SCENARIO_COUNT = 18;
    static final int PARAMETER_COUNT = 38;
    static final String FORMAL_LOCK = "FORMAL_ENTRY_LOCKED_PENDING_FIXED_SHA_REVIEW_AND_NEW_AUTHORIZATION";
    static final String RUNNER_SELECTOR = "P021OrderDetailEvidenceFinalRunTest";
    static final String PREFLIGHT_SELECTOR = "P021SameChainHostPreflightSelectorTest";
    static final String PREFLIGHT_SCOPE = "P021_TECHNICAL_HOST_PREFLIGHT_0_SCENARIO";
    static final Path PREFLIGHT_MAVEN_HOME = Path.of("C:/Users/yenanc/tools/apache-maven-3.9.6");
    static final Path PREFLIGHT_MAVEN_LAUNCHER = PREFLIGHT_MAVEN_HOME.resolve("boot/plexus-classworlds-2.7.0.jar");
    static final String PREFLIGHT_MAVEN_LAUNCHER_SHA = "C60AE538BA66ADBC06AAE205FBE2306211D3D213AB6DF3239EC03CDDE2458AD6";
    static final Path PREFLIGHT_MAVEN_CONFIG = PREFLIGHT_MAVEN_HOME.resolve("bin/m2.conf");
    static final String PREFLIGHT_MAVEN_CONFIG_SHA = "E336769BF93A902BAA7A3E827BA55E4CEF7DE4AF2ED1A9541A4261094D748BA9";
    static final Path PREFLIGHT_OFFLINE_REPOSITORY = Path.of("E:/workspace/huarenzaimeng/.m2-local/repository");
    static final String PREFLIGHT_OFFLINE_REPOSITORY_IDENTITY_SHA = "C45CF5E2AD1E8A3021237F922ED2C94EDF85C7281C297A88BE09BFF8F5762BB5";
    static final Path PREFLIGHT_OFFLINE_REPOSITORY_MANIFEST = Path.of("apps/api/manifests/P021-host-offline-repository-identity.txt");
    static final String PREFLIGHT_OFFLINE_REPOSITORY_MANIFEST_SHA = "D57555BEFF4151B6E9B23BEFEA49B002E0F17C4156CAC35BAE42F783F37AC0A2";
    static final Path PREFLIGHT_OUTER_WRAPPER = Path.of("apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightOuterWrapper.java");
    static final String PREFLIGHT_OUTER_WRAPPER_SHA = "A9A3220FA9FCAC0CF9D99C87EE00639819E417BD6E8F1AD538D35A3D31ED4259";
    static final Path PREFLIGHT_RUN_ID_POLICY = Path.of("apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightRunIdPolicy.java");
    static final Path PREFLIGHT_COMPILED_LAUNCHER = Path.of("apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightUtf8CompiledLauncher.java");
    static final Path PREFLIGHT_JAVAC = Path.of("C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/javac.exe");
    static final String PREFLIGHT_JAVAC_SHA = "988446CEEC6E33CE0420DDF2489BB13D3A2278B4C8745309123DFA8F2C0DD0CE";
    static final Path PREFLIGHT_JAVA = Path.of("C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/java.exe");
    static final String PREFLIGHT_JAVA_SHA = "B3AFE83E1AB067DA4C56F1A7B2BA4C14EC832D694333F35B2B45178E9AC596EF";
    static final Path PREFLIGHT_REPORT_ROOT = Path.of("项目管理/正式交付/D4-开发计划与工程准备/证据/P021-HOST-PREFLIGHT");
    static final Path PREFLIGHT_AUTHORIZATION_ROOT = Path.of("项目管理/正式交付/D4-开发计划与工程准备/授权记录/P021-HOST-PREFLIGHT");
    static final Path FIXED_EVIDENCE_ROOT = Path.of("项目管理/正式交付/D4-开发计划与工程准备/证据/D5-ORD-03-P021后端技术证据");
    static final Path FIXED_AUTHORIZATION_ROOT = Path.of("项目管理/正式交付/D4-开发计划与工程准备/授权记录");
    private static final Pattern RUN_ID = Pattern.compile("^P021-BE-[A-Za-z0-9_-]{8,100}$");
    private static final Pattern PREFLIGHT_RUN_ID = Pattern.compile("^P021-TECH-HOST-PREFLIGHT-[A-Za-z0-9_-]{8,100}$");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Set<String> AUTH_KEYS = Set.of("AuthorizationRef", "ExecutionScope", "RunId", "ValidFrom",
            "ValidUntil", "ImplementationAggregateSha", "MatrixIdentitySha", "RunnerAggregateSha", "JavaHostAggregateSha",
            "FormalCommandDigest", "SingleUse", "Status");
    private static final Set<String> PREFLIGHT_AUTH_KEYS = Set.of("AuthorizationRef", "ExecutionScope", "RunId",
            "ValidFrom", "ValidUntil", "ImplementationAggregateSha", "MatrixIdentitySha", "RunnerAggregateSha",
            "JavaHostAggregateSha", "OuterCommandDigest", "InnerCommandDigest", "MavenHome", "MavenLauncherPath",
            "MavenLauncherSha", "MavenConfigPath", "MavenConfigSha", "JavaExecutablePath", "JavaExecutableSha", "OuterWrapperPath", "OuterWrapperSha",
            "Offline", "OfflineRepositoryPath", "OfflineRepositoryIdentitySha", "OfflineRepositoryManifestPath", "OfflineRepositoryManifestSha",
            "SingleUse", "AutomaticRetryAllowed", "Status");
    private static final Set<String> WRITE_KEYS = Set.of("Command", "CommandAlias", "TopupBusinessKey", "TopupSemanticAction",
            "TopupIntent", "DispatchSemanticAction", "DispatchIntent", "OrderVersion", "ProjectionVersion", "SyntheticObservation",
            "PaymentAttempt", "SendAttempt", "RemoteAcceptance", "WechatPrepay", "RequestPayment", "Notification", "ExternalFact",
            "W", "U", "D", "L", "LedgerEntry", "ExternalCall");
    private static final Set<String> COUNT_KEYS = union(WRITE_KEYS, Set.of("QueryCall", "FileWrite", "QueueWrite", "NotificationSend"));
    private static final Map<String,String> FIXED_INPUTS = Map.ofEntries(
            Map.entry("D1RegistrySha","364F73B28E2BC1C4D3827D5B9332C1CEB6D93C928859A226CD8842FF283D9BD8"),Map.entry("D2RegistrySha","516EA3ACA003BEF18A49D5961F13DCCE313CAD4C1B60C44964519CA793CE52BF"),
            Map.entry("D3_03Sha","33CCC6BB94B2D2A95E798C7CAD44C839CC35D8B577727492C64F10776E6FDA95"),Map.entry("D3_04Sha","B12E780F19AB333458018C908324EADA6F84E1ECD35349FB739A7DC5AE22E4DB"),
            Map.entry("D3_05Sha","CC27E94902FF2BCE86E02EE553F2E03F7E7752603AD247D826E619F929856282"),Map.entry("D3RegistrySha","6321D16CAC17D426801F26F5AD643BAB418D08FF72FD299BCEA7FB20C9522A0C"),
            Map.entry("D4_05Sha","4054C8A4DA3C4D32BB7AF10F562827D399CB24380A474B765383791902617618"),Map.entry("D4_08Sha","13DE50B5306F6CA2AEDA735484772DFCDEC00CDC11E0BE082B43787CD2D1250B"),
            Map.entry("D4RebindRecordSha","65F59B4F6F4A5A7A6757D65862D09492217A236859D02B3A026242FDEDA62468"));
    private static final Map<String,String> IMPLEMENTATION_FILES = Map.ofEntries(
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/LocalSyntheticOrderRecoveryService.java","B99CCE05F16C634852216F2E43EDF61DDB5875A0871B9C8E7648712253B9B6D6"),Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/MockFlowController.java","206148EEC6E1C7F183B0DDA938C2B591B952951071AB5C746980544BE3567E4C"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailBoundaryAdapters.java","5F3917190EF6B876B501D492784C2A006A1AD748FF75A5D2C9E29A7622D5F185"),Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailController.java","BC5C73C4D059CBC6304A28CAF546588C75CE74F212288550E85D62C675D8540B"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailDomain.java","ABECE82E7F63954AF8A99B92F212F70366FB1D2996B4815A92BCFE8920BBB525"),Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailFixtureLoader.java","612CC0503C0B50C98E06F9EBBB13B67CFFF31FC4A34FCF7912297020C0A8B1CD"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailService.java","CD343493A6763562D3E7647C9FF405E6F9EC090DB65F45582FE138DCD9D9776C"),Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailSideEffectProbe.java","1036AAC2DE7302A11F678511381C36EFCE766AD076ACBE4F7CDD8DE0256FF004"),
            Map.entry("apps/api/src/main/java/com/huarenzaimeng/api/config/TestAccessTokenFilter.java","BD53C39F56B091B42F3D0569A87EACFE39C1BB07A88210ACC842106F60A03D91"),Map.entry("apps/api/src/test/java/com/huarenzaimeng/api/MockFlowApiContractTest.java","7093D57111BAE5CF2CA902F012AD897510C3D9EF9D86F1AC118312A5131E7514"),
            Map.entry("apps/api/src/test/java/com/huarenzaimeng/api/P021OrderDetailApiContractTest.java","A36EB8EDD25D1E8E7EF7259CC32E98872020F3FE479A89FAB8710AA2A5835CDF"));

    record Paths(Path root, Path staging, Path publishing, Path finalDir, Path blocked, Path process) {
        static Paths forRun(Path root, String runId) {
            return new Paths(root, root.resolve(".staging-" + runId), root.resolve(".publishing-" + runId),
                    root.resolve(runId), root.resolve(".blocked-" + runId), root.resolve(".process-" + runId));
        }
        static Paths forFormalRun(Path repositoryRoot, String runId) {
            validateRunId(runId);
            Path repository = repositoryRoot.toAbsolutePath().normalize();
            Path fixedRoot = repository.resolve(FIXED_EVIDENCE_ROOT).normalize();
            if (!fixedRoot.startsWith(repository) || !fixedRoot.equals(repository.resolve(FIXED_EVIDENCE_ROOT).normalize()))
                throw new IllegalStateException("EVIDENCE_ROOT_ESCAPE");
            return forRun(fixedRoot, runId);
        }
    }

    record Authorization(String ref, String runId, Instant validFrom, Instant validUntil, Path record, Path marker) {}
    record ProcessEvidence(String command, Instant startedAt, Instant endedAt, int osExitCode,
                           String stdoutSha, String stderrSha, boolean wrapperLoaded, boolean formalTestStarted,
                           String javaHostAggregateSha,String formalCommandDigest,String authorizationRef,String authorizationRecordSha,
                           String authorizationConsumptionRef,String authorizationConsumptionSha) {}
    record Bindings(String javaHostAggregateSha,String formalCommandDigest,String authorizationRef,String authorizationRecordSha,
                    String authorizationConsumptionRef,String authorizationConsumptionSha) {}
    enum Stage { AUTHORIZED, FRESH, CONSUMED, PROCESS_STARTED, PROCESS_FINISHED, PACKAGE_VALIDATED, PROCESS_CLEANED, PUBLISHED }
    interface FaultInjector { void checkpoint(Stage stage) throws Exception; }
    interface ProcessLauncher { ProcessEvidence launch(List<String> command, Paths paths,Bindings bindings) throws Exception; }
    record ExecutionRequest(Paths paths, Path authorizationRecord, Path allowedAuthorizationRoot, String authorizationRecordSha, String runId,
                            String javaHostAggregateSha, String formalCommandDigest, Instant now) {}

    static List<String> formalCommand(String runId, String authorizationRef, String authorizationSha,String consumptionSha, Path staging) {
        validateRunId(runId);
        return List.of("mvn", "-pl", "apps/api", "-am", "-Dtest=" + RUNNER_SELECTOR,
                "-Dsurefire.failIfNoSpecifiedTests=false", "-Dp021.evidence.confirm=FINAL_RUN",
                "-Dp021.evidence.runId=" + runId, "-Dp021.evidence.authorizationRef=" + authorizationRef,
                "-Dp021.evidence.authorizationRecordSha=" + authorizationSha,
                "-Dp021.evidence.authorizationConsumptionSha="+consumptionSha,
                "-Dp021.evidence.staging=" + staging.toAbsolutePath().normalize(), "test");
    }

    static void orchestratePrepared(ExecutionRequest request, ProcessLauncher launcher, FaultInjector faults) throws Exception {
        Authorization authorization=null;
        try {
            authorization=validateAuthorization(request.authorizationRecord(),request.allowedAuthorizationRoot(),
                    request.authorizationRecordSha(),request.runId(),request.javaHostAggregateSha(),request.formalCommandDigest(),request.now());
            faults.checkpoint(Stage.AUTHORIZED);assertFresh(request.paths());faults.checkpoint(Stage.FRESH);
            consumeOnce(authorization,request.now());faults.checkpoint(Stage.CONSUMED);
            String consumptionSha=sha(authorization.marker());Bindings bindings=new Bindings(request.javaHostAggregateSha(),request.formalCommandDigest(),authorization.ref(),request.authorizationRecordSha(),authorization.marker().getFileName().toString(),consumptionSha);
            Files.createDirectories(request.paths().staging());Files.createDirectories(request.paths().process());
            List<String>command=formalCommand(request.runId(),authorization.ref(),request.authorizationRecordSha(),consumptionSha,request.paths().staging());
            faults.checkpoint(Stage.PROCESS_STARTED);ProcessEvidence process=launcher.launch(command,request.paths(),bindings);faults.checkpoint(Stage.PROCESS_FINISHED);
            stampStagedBindings(request.paths().staging(),bindings);
            publishPreparedPackage(request.paths(),request.runId(),authorization,process,bindings,faults);
        } catch(Exception failure) {
            blockAndIsolate(request.paths(),failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());throw failure;
        }
    }

    static String hostPreflightStatus() {
        return "WrapperLoaded=true|WrapperParsed=true|FormalTestStarted=false|EvidenceWritten=false|" + FORMAL_LOCK;
    }

    public static void main(String[] args) throws Exception {
        if(args.length==8&&"--selector".equals(args[0])&&"PREFLIGHT".equals(args[1])){
            runSameChainPreflight(args);
            return;
        }
        if(args.length!=6||!"--runId".equals(args[0])||!"--authorizationRecord".equals(args[2])||!"--authorizationRecordSha".equals(args[4]))throw new IllegalArgumentException("FORMAL_ARGUMENTS");
        Path repository=Path.of("").toAbsolutePath().normalize();String runId=args[1];Path authorization=Path.of(args[3]).toAbsolutePath().normalize();
        String hostAggregate=javaHostAggregate(repository);Paths paths=Paths.forFormalRun(repository,runId);
        ExecutionRequest request=new ExecutionRequest(paths,authorization,repository.resolve(FIXED_AUTHORIZATION_ROOT).normalize(),args[5],runId,hostAggregate,formalCommandDigest(),Instant.now());
        orchestratePrepared(request,new SystemProcessLauncher(repository),stage->{});
    }

    private static void runSameChainPreflight(String[] args) throws Exception {
        if(!"--runId".equals(args[2])||!"--authorizationRecord".equals(args[4])
                ||!"--authorizationRecordSha".equals(args[6]))
            throw new IllegalArgumentException("PREFLIGHT_ARGUMENTS");
        Path repository=Path.of("").toAbsolutePath().normalize();
        String runId=args[3];validatePreflightRunId(runId);
        Path authorizationRecord=Path.of(args[5]).toAbsolutePath().normalize();
        Path allowedAuthorizationRoot=repository.resolve(PREFLIGHT_AUTHORIZATION_ROOT).normalize();
        String expectedAuthorizationRef="AUTH-"+runId;
        Path expectedAuthorizationRecord=allowedAuthorizationRoot.resolve(expectedAuthorizationRef+".json").normalize();
        Path reportRoot=repository.resolve(PREFLIGHT_REPORT_ROOT).normalize();
        Path reportDirectory=reportRoot.resolve(runId).normalize();
        Path blockedDirectory=reportRoot.resolve(".blocked-"+runId).normalize();
        if(!authorizationRecord.equals(expectedAuthorizationRecord)
                ||!reportDirectory.getParent().equals(reportRoot)
                ||!blockedDirectory.getParent().equals(reportRoot)
                ||Files.exists(reportDirectory)||Files.exists(blockedDirectory))
            throw new IllegalStateException("PREFLIGHT_PATH_BOUNDARY");
        JsonNode authorization=JSON.readTree(Files.readString(authorizationRecord,StandardCharsets.UTF_8));
        P021HostPreflightRunIdPolicy.validateAuthorizationBinding(runId,authorization.path("RunId").asText());
        String hostAggregate=preflightHostAggregate(repository), outerDigest=preflightOuterCommandDigest(), innerDigest=preflightInnerCommandDigest();
        if(!PREFLIGHT_AUTH_KEYS.equals(fieldNames(authorization))||!sha(authorizationRecord).equals(args[7])
                ||!PREFLIGHT_SCOPE.equals(authorization.path("ExecutionScope").asText())
                ||!"APPROVED".equals(authorization.path("Status").asText())
                ||!authorization.path("SingleUse").asBoolean(false)
                ||authorization.path("AutomaticRetryAllowed").asBoolean(true)
                ||!IMPLEMENTATION_SHA.equals(authorization.path("ImplementationAggregateSha").asText())
                ||!MATRIX_IDENTITY_SHA.equals(authorization.path("MatrixIdentitySha").asText())
                ||!RUNNER_AGGREGATE_SHA.equals(authorization.path("RunnerAggregateSha").asText())
                ||!hostAggregate.equals(authorization.path("JavaHostAggregateSha").asText())
                ||!outerDigest.equals(authorization.path("OuterCommandDigest").asText())
                ||!innerDigest.equals(authorization.path("InnerCommandDigest").asText())
                ||!PREFLIGHT_MAVEN_HOME.toAbsolutePath().normalize().toString().equals(authorization.path("MavenHome").asText())
                ||!PREFLIGHT_MAVEN_LAUNCHER.toAbsolutePath().normalize().toString().equals(authorization.path("MavenLauncherPath").asText())
                ||!PREFLIGHT_MAVEN_LAUNCHER_SHA.equals(authorization.path("MavenLauncherSha").asText())
                ||!PREFLIGHT_MAVEN_CONFIG.toAbsolutePath().normalize().toString().equals(authorization.path("MavenConfigPath").asText())
                ||!PREFLIGHT_MAVEN_CONFIG_SHA.equals(authorization.path("MavenConfigSha").asText())
                ||!authorization.path("Offline").asBoolean(false)
                ||!PREFLIGHT_OFFLINE_REPOSITORY.toAbsolutePath().normalize().toString().equals(authorization.path("OfflineRepositoryPath").asText())
                ||!PREFLIGHT_OFFLINE_REPOSITORY_IDENTITY_SHA.equals(authorization.path("OfflineRepositoryIdentitySha").asText())
                ||!PREFLIGHT_OFFLINE_REPOSITORY_MANIFEST.toString().replace('\\','/').equals(authorization.path("OfflineRepositoryManifestPath").asText())
                ||!PREFLIGHT_OFFLINE_REPOSITORY_MANIFEST_SHA.equals(authorization.path("OfflineRepositoryManifestSha").asText())
                ||!P021HostPreflightOuterWrapper.OFFLINE_REPOSITORY.toAbsolutePath().normalize().equals(PREFLIGHT_OFFLINE_REPOSITORY.toAbsolutePath().normalize())
                ||!P021HostPreflightOuterWrapper.OFFLINE_REPOSITORY_IDENTITY_SHA.equals(PREFLIGHT_OFFLINE_REPOSITORY_IDENTITY_SHA)
                ||!Files.isRegularFile(PREFLIGHT_MAVEN_LAUNCHER)||!PREFLIGHT_MAVEN_LAUNCHER_SHA.equals(sha(PREFLIGHT_MAVEN_LAUNCHER))
                ||!Files.isRegularFile(PREFLIGHT_MAVEN_CONFIG)||!PREFLIGHT_MAVEN_CONFIG_SHA.equals(sha(PREFLIGHT_MAVEN_CONFIG))
                ||!PREFLIGHT_JAVA.toAbsolutePath().normalize().toString().equals(authorization.path("JavaExecutablePath").asText())
                ||!PREFLIGHT_JAVA_SHA.equals(authorization.path("JavaExecutableSha").asText())
                ||!Files.isRegularFile(PREFLIGHT_JAVA)||!PREFLIGHT_JAVA_SHA.equals(sha(PREFLIGHT_JAVA))
                ||!Files.isRegularFile(PREFLIGHT_JAVAC)||!PREFLIGHT_JAVAC_SHA.equals(sha(PREFLIGHT_JAVAC))
                ||!PREFLIGHT_OUTER_WRAPPER.toString().replace('\\','/').equals(authorization.path("OuterWrapperPath").asText())
                ||!PREFLIGHT_OUTER_WRAPPER_SHA.equals(authorization.path("OuterWrapperSha").asText())
                ||!Files.isRegularFile(repository.resolve(PREFLIGHT_OUTER_WRAPPER))
                ||!PREFLIGHT_OUTER_WRAPPER_SHA.equals(sha(repository.resolve(PREFLIGHT_OUTER_WRAPPER))))
            throw new IllegalStateException("PREFLIGHT_AUTHORIZATION_BINDING");
        P021HostPreflightOuterWrapper.validateOfflineRepository();
        Instant now=Instant.now(),from=Instant.parse(authorization.path("ValidFrom").asText()),until=Instant.parse(authorization.path("ValidUntil").asText());
        validatePreflightAuthorizationWindow(from,until,now);
        if(!expectedAuthorizationRef.equals(authorization.path("AuthorizationRef").asText()))
            throw new IllegalStateException("PREFLIGHT_AUTHORIZATION_TIME_OR_REF");
        Path consumption=authorizationRecord.resolveSibling(authorizationRecord.getFileName()+".consumed."+runId+".json");
        ObjectNode consumptionJson=JSON.createObjectNode().put("AuthorizationRef",authorization.path("AuthorizationRef").asText())
                .put("RunId",runId).put("Scope",PREFLIGHT_SCOPE).put("ConsumedAt",now.toString()).put("SingleUse",true);
        byte[] consumptionBytes=JSON.writeValueAsBytes(consumptionJson);
        try(FileChannel channel=FileChannel.open(consumption,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            channel.write(ByteBuffer.wrap(consumptionBytes));channel.force(true);
        }catch(FileAlreadyExistsException duplicate){throw new IllegalStateException("PREFLIGHT_AUTHORIZATION_ALREADY_CONSUMED",duplicate);}
        String consumptionSha=sha(consumption);
        Path stdout=reportDirectory.resolve("process.stdout.txt"),stderr=reportDirectory.resolve("process.stderr.txt");
        List<String>command=preflightInnerCommand();Instant started=Instant.now();int exit=-1;Instant ended;
        try {
            Files.createDirectories(reportDirectory);
            Process process=new ProcessBuilder(command).directory(repository.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
            exit=process.waitFor();ended=Instant.now();String output=Files.readString(stdout,StandardCharsets.UTF_8);
            if(exit!=0||!output.contains("P021_HOST_PREFLIGHT_SELECTOR_OK|FormalScenarioCount=0|FormalTestStarted=false|EvidenceWritten=false"))
                throw new IllegalStateException("PREFLIGHT_CHILD_PROCESS_FAILED");
            ObjectNode result=preflightResult(authorization,args[7],consumption.getFileName().toString(),consumptionSha,runId,hostAggregate,outerDigest,innerDigest,command,repository,started,ended,exit,stdout,stderr,"PASS");
            writeJson(reportDirectory.resolve("preflight-result.json"),result);
        } catch(Exception failure) {
            ended=Instant.now();ObjectNode blocked=preflightResult(authorization,args[7],consumption.getFileName().toString(),consumptionSha,runId,hostAggregate,outerDigest,innerDigest,command,repository,started,ended,exit,stdout,stderr,"BLOCKED");
            blocked.put("Consumable",false).put("AutomaticRetryAllowed",false).put("Reason",failure.getMessage());
            if(Files.exists(reportDirectory))moveAtomically(reportDirectory,blockedDirectory);else Files.createDirectories(blockedDirectory);
            writeJson(blockedDirectory.resolve("BLOCKED.json"),blocked);throw failure;
        }
    }

    private static ObjectNode preflightResult(JsonNode authorization,String authorizationSha,String consumptionRef,String consumptionSha,String runId,String hostAggregate,
                                               String outerDigest,String innerDigest,List<String> command,Path repository,
                                               Instant started,Instant ended,int exit,Path stdout,Path stderr,String status)throws Exception{
        ObjectNode result=JSON.createObjectNode();result.put("ExecutionStatus",status).put("Consumable",false)
                .put("AutomaticRetryAllowed",false).put("RunId",runId).put("ExecutionScope",PREFLIGHT_SCOPE)
                .put("Selector","PREFLIGHT").put("FormalScenarioCount",0).put("FormalTestStarted",false)
                .put("EvidenceWritten",false).put("ReadyCount",0).put("JavaHostAggregateSha",hostAggregate)
                .put("OuterCommandDigest",outerDigest).put("InnerCommandDigest",innerDigest)
                .put("MavenHome",PREFLIGHT_MAVEN_HOME.toAbsolutePath().normalize().toString())
                .put("MavenLauncherPath",PREFLIGHT_MAVEN_LAUNCHER.toAbsolutePath().normalize().toString()).put("MavenLauncherSha",PREFLIGHT_MAVEN_LAUNCHER_SHA)
                .put("MavenConfigPath",PREFLIGHT_MAVEN_CONFIG.toAbsolutePath().normalize().toString()).put("MavenConfigSha",PREFLIGHT_MAVEN_CONFIG_SHA)
                .put("Offline",true).put("OfflineRepositoryPath",PREFLIGHT_OFFLINE_REPOSITORY.toAbsolutePath().normalize().toString())
                .put("OfflineRepositoryIdentitySha",PREFLIGHT_OFFLINE_REPOSITORY_IDENTITY_SHA)
                .put("OfflineRepositoryManifestPath",PREFLIGHT_OFFLINE_REPOSITORY_MANIFEST.toString().replace('\\','/')).put("OfflineRepositoryManifestSha",PREFLIGHT_OFFLINE_REPOSITORY_MANIFEST_SHA)
                .put("JavaExecutablePath",PREFLIGHT_JAVA.toAbsolutePath().normalize().toString()).put("JavaExecutableSha",PREFLIGHT_JAVA_SHA)
                .put("OuterWrapperPath",PREFLIGHT_OUTER_WRAPPER.toString().replace('\\','/')).put("OuterWrapperSha",PREFLIGHT_OUTER_WRAPPER_SHA)
                .put("AuthorizationRef",authorization.path("AuthorizationRef").asText()).put("AuthorizationRecordSha",authorizationSha)
                .put("AuthorizationConsumptionRef",consumptionRef).put("AuthorizationConsumptionSha",consumptionSha)
                .put("Command",String.join(" ",command)).put("Cwd",repository.toString()).put("StartedAt",started.toString())
                .put("EndedAt",ended.toString()).put("OsExitCode",exit);
        result.put("StdoutSha",Files.isRegularFile(stdout)?sha(stdout):null).put("StderrSha",Files.isRegularFile(stderr)?sha(stderr):null);
        return result;
    }

    static List<String> preflightInnerCommand(){List<String> command=new ArrayList<>(List.of(PREFLIGHT_JAVA.toAbsolutePath().normalize().toString(),
            "-Dclassworlds.conf="+PREFLIGHT_MAVEN_CONFIG.toAbsolutePath().normalize(),"-Dmaven.home="+PREFLIGHT_MAVEN_HOME.toAbsolutePath().normalize(),
            "-Dmaven.multiModuleProjectDirectory="+Path.of("").toAbsolutePath().normalize(),"-classpath",PREFLIGHT_MAVEN_LAUNCHER.toAbsolutePath().normalize().toString(),
            "org.codehaus.plexus.classworlds.launcher.Launcher"));command.addAll(List.of("-o","-Dmaven.repo.local="+PREFLIGHT_OFFLINE_REPOSITORY.toAbsolutePath().normalize(),"-pl","apps/api","-am","-Dtest="+PREFLIGHT_SELECTOR,
            "-Dsurefire.failIfNoSpecifiedTests=false","-Dp021.host.selector=PREFLIGHT","test"));return List.copyOf(command);}
    static String preflightInnerCommandDigest()throws Exception{return digest(String.join("\n",preflightInnerCommand()).getBytes(StandardCharsets.UTF_8));}
    static String preflightOuterCommandDigest()throws Exception{return digest(String.join("\n",List.of(
            PREFLIGHT_JAVAC.toAbsolutePath().normalize().toString(),"-encoding","UTF-8","-d","{FIXED_BOOTSTRAP_CLASSES_DIR}",
            PREFLIGHT_RUN_ID_POLICY.toAbsolutePath().normalize().toString(),PREFLIGHT_COMPILED_LAUNCHER.toAbsolutePath().normalize().toString(),"--THEN--",
            PREFLIGHT_JAVA.toAbsolutePath().normalize().toString(),"-cp","{FIXED_BOOTSTRAP_CLASSES_DIR}",
            "com.huarenzaimeng.api.P021HostPreflightUtf8CompiledLauncher","{RUN_ID}","{AUTH_RECORD}","{AUTH_SHA}")).getBytes(StandardCharsets.UTF_8));}
    static String preflightHostAggregate(Path repository)throws Exception{List<String> paths=List.of(
            "apps/api/src/test/java/com/huarenzaimeng/api/P021ControlledEvidenceJavaHost.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightRunIdPolicy.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightOuterWrapper.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightUtf8CompiledLauncher.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/P021SameChainHostPreflightExecutionTest.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/P021SameChainHostPreflightSelectorTest.java",
            "apps/api/manifests/P021-host-offline-repository-identity.txt");
        List<String> lines=new ArrayList<>();for(String path:paths)lines.add(path+"|"+sha(repository.resolve(path)));
        return digest(String.join("\n",lines).getBytes(StandardCharsets.UTF_8));}
    static void validatePreflightAuthorizationWindow(Instant from,Instant until,Instant now){
        if(!from.isBefore(until)||!java.time.Duration.ofMinutes(15).equals(java.time.Duration.between(from,until))
                ||now.isBefore(from)||now.isAfter(until))throw new IllegalStateException("PREFLIGHT_AUTHORIZATION_WINDOW");
    }
    static void validatePreflightRunId(String runId){P021HostPreflightRunIdPolicy.validate(runId);}

    static final class SystemProcessLauncher implements ProcessLauncher {
        private final Path repository;SystemProcessLauncher(Path repository){this.repository=repository;}
        public ProcessEvidence launch(List<String> command,Paths paths,Bindings bindings)throws Exception{
            Path stdout=paths.process().resolve("process.stdout.txt"),stderr=paths.process().resolve("process.stderr.txt");Instant started=Instant.now();
            Process process=new ProcessBuilder(command).directory(repository.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();int exit=process.waitFor();Instant ended=Instant.now();
            return new ProcessEvidence(String.join(" ",command),started,ended,exit,sha(stdout),sha(stderr),true,true,
                    bindings.javaHostAggregateSha(),bindings.formalCommandDigest(),bindings.authorizationRef(),bindings.authorizationRecordSha(),bindings.authorizationConsumptionRef(),bindings.authorizationConsumptionSha());
        }
    }

    static String formalCommandDigest() throws Exception {
        return textSha(List.of("mvn|-pl|apps/api|-am|-Dtest="+RUNNER_SELECTOR+"|-Dsurefire.failIfNoSpecifiedTests=false|"
                +"-Dp021.evidence.confirm=FINAL_RUN|-Dp021.evidence.runId={RUN_ID}|-Dp021.evidence.authorizationRef={AUTH_REF}|"
                +"-Dp021.evidence.authorizationRecordSha={AUTH_SHA}|-Dp021.evidence.authorizationConsumptionSha={CONSUMPTION_SHA}|-Dp021.evidence.staging={STAGING}|test"));
    }
    static String javaHostAggregate(Path repository)throws Exception{
        List<String>paths=List.of("apps/api/src/test/java/com/huarenzaimeng/api/P021ControlledEvidenceJavaHost.java",
                "apps/api/src/test/java/com/huarenzaimeng/api/P021ControlledEvidenceJavaHostTest.java",
                "apps/api/src/test/java/com/huarenzaimeng/api/P021EvidenceHostPreflightTest.java");
        List<String>lines=new ArrayList<>();for(String path:paths)lines.add(path+"|"+sha(repository.resolve(path)));return textSha(lines);
    }

    static Authorization validateAuthorization(Path record, Path allowedAuthorizationRoot, String expectedRecordSha, String runId,
                                               String javaHostAggregateSha, String formalCommandDigest, Instant now) throws Exception {
        validateRunId(runId);
        Path allowed = allowedAuthorizationRoot.toAbsolutePath().normalize();
        Path normalizedRecord = record.toAbsolutePath().normalize();
        if (!normalizedRecord.getParent().equals(allowed) || !normalizedRecord.getFileName().toString().endsWith(".json"))
            throw new IllegalStateException("AUTHORIZATION_LOCATION");
        JsonNode auth = JSON.readTree(Files.readString(record, StandardCharsets.UTF_8));
        if (!AUTH_KEYS.equals(fieldNames(auth))) throw new IllegalStateException("AUTHORIZATION_SCHEMA");
        if (!sha(record).equals(expectedRecordSha)
                || !EXECUTION_SCOPE.equals(auth.path("ExecutionScope").asText())
                || !runId.equals(auth.path("RunId").asText())
                || !IMPLEMENTATION_SHA.equals(auth.path("ImplementationAggregateSha").asText())
                || !MATRIX_IDENTITY_SHA.equals(auth.path("MatrixIdentitySha").asText())
                || !RUNNER_AGGREGATE_SHA.equals(auth.path("RunnerAggregateSha").asText())
                || !javaHostAggregateSha.equals(auth.path("JavaHostAggregateSha").asText())
                || !formalCommandDigest.equals(auth.path("FormalCommandDigest").asText())
                || !auth.path("SingleUse").asBoolean(false)
                || !"APPROVED".equals(auth.path("Status").asText())) throw new IllegalStateException("AUTHORIZATION_BINDING");
        Instant from = Instant.parse(auth.path("ValidFrom").asText());
        Instant until = Instant.parse(auth.path("ValidUntil").asText());
        if (!from.isBefore(until) || now.isBefore(from) || now.isAfter(until) || auth.path("AuthorizationRef").asText().isBlank()) throw new IllegalStateException("AUTHORIZATION_TIME_OR_REF");
        Path marker = record.resolveSibling(record.getFileName() + ".consumed." + runId + ".json");
        return new Authorization(auth.path("AuthorizationRef").asText(), runId, from, until, record, marker);
    }
    static Authorization validateFormalAuthorization(Path repositoryRoot, Path record, String expectedRecordSha, String runId,
                                                     String javaHostAggregateSha, String formalCommandDigest, Instant now) throws Exception {
        Path repository=repositoryRoot.toAbsolutePath().normalize();Path allowed=repository.resolve(FIXED_AUTHORIZATION_ROOT).normalize();
        if(!allowed.startsWith(repository))throw new IllegalStateException("AUTHORIZATION_ROOT_ESCAPE");
        return validateAuthorization(record,allowed,expectedRecordSha,runId,javaHostAggregateSha,formalCommandDigest,now);
    }

    static void consumeOnce(Authorization authorization, Instant consumedAt) throws Exception {
        ObjectNode marker = JSON.createObjectNode().put("AuthorizationRef", authorization.ref()).put("RunId", authorization.runId())
                .put("ConsumedAt", consumedAt.toString()).put("Scope", EXECUTION_SCOPE).put("SingleUse", true);
        byte[] bytes = JSON.writeValueAsBytes(marker);
        try (FileChannel channel = FileChannel.open(authorization.marker(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes)); channel.force(true);
        }
    }

    static void assertFresh(Paths paths) {
        if (List.of(paths.staging(), paths.publishing(), paths.finalDir(), paths.blocked(), paths.process())
                .stream().anyMatch(Files::exists)) throw new IllegalStateException("RUN_ID_ALREADY_USED");
    }

    static void publishPreparedPackage(Paths paths, String runId, Authorization authorization, ProcessEvidence processEvidence,
                                       Bindings bindings, FaultInjector faults) throws Exception {
        validateRunId(runId);
        if (!Files.isDirectory(paths.staging())) throw new IllegalStateException("STAGING_MISSING");
        validateProcessEvidence(paths, runId, authorization, processEvidence,bindings);
        List<ObjectNode> indexEntries = validateStagedCases(paths.staging(), runId, authorization,bindings);
        faults.checkpoint(Stage.PACKAGE_VALIDATED);
        moveAtomically(paths.staging(), paths.publishing());
        for(ObjectNode entry:indexEntries){Path caseFile=paths.publishing().resolve(entry.path("File").asText());ObjectNode c=(ObjectNode)JSON.readTree(Files.readString(caseFile));c.put("Consumable",true);writeJson(caseFile,c);entry.put("Sha256",sha(caseFile));}
        ObjectNode process = JSON.valueToTree(processEvidence);
        writeJson(paths.publishing().resolve("process-evidence.json"), process);
        Files.copy(paths.process().resolve("process.stdout.txt"), paths.publishing().resolve("process.stdout.txt"));
        Files.copy(paths.process().resolve("process.stderr.txt"), paths.publishing().resolve("process.stderr.txt"));
        ObjectNode index=JSON.createObjectNode();putBindings(index,bindings);index.set("Entries",JSON.valueToTree(indexEntries));writeJson(paths.publishing().resolve("index.json"),index);
        ObjectNode manifest = JSON.createObjectNode().put("Consumable", true).put("ExecutionStatus", "PASS")
                .put("RunId", runId).put("ScenarioCount", SCENARIO_COUNT).put("ParameterCount", PARAMETER_COUNT)
                .put("RunnerAggregateSha", RUNNER_AGGREGATE_SHA).put("MatrixIdentitySha", MATRIX_IDENTITY_SHA)
                .put("JavaHostAggregateSha",bindings.javaHostAggregateSha()).put("FormalCommandDigest",bindings.formalCommandDigest())
                .put("AuthorizationRef",bindings.authorizationRef()).put("AuthorizationRecordSha",bindings.authorizationRecordSha())
                .put("AuthorizationConsumptionRef",bindings.authorizationConsumptionRef()).put("AuthorizationConsumptionSha",bindings.authorizationConsumptionSha())
                .set("FixedInputs",JSON.valueToTree(FIXED_INPUTS));
        manifest.put("ProcessEvidenceRef", "process-evidence.json");
        writeJson(paths.publishing().resolve("manifest.json"), manifest);
        List<String> packageLines = new ArrayList<>();
        try (var files = Files.list(paths.publishing())) {
            for (Path file : files.filter(Files::isRegularFile).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList())
                packageLines.add(file.getFileName() + "|" + sha(file));
        }
        ObjectNode packageJson = JSON.createObjectNode().put("PackageSha256", textSha(packageLines)).put("FileCount", packageLines.size());putBindings(packageJson,bindings);
        writeJson(paths.publishing().resolve("package.json"), packageJson);
        validatePublicationFiles(paths.publishing(), processEvidence, indexEntries.size(),bindings);
        deleteTree(paths.process());if(Files.exists(paths.process()))throw new IllegalStateException("PROCESS_CLEANUP_FAILED");faults.checkpoint(Stage.PROCESS_CLEANED);
        Files.writeString(paths.publishing().resolve("READY"), "READY", StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        moveAtomically(paths.publishing(), paths.finalDir());
        faults.checkpoint(Stage.PUBLISHED);
    }

    static void blockAndIsolate(Paths paths, String reason) throws Exception {
        Files.createDirectories(paths.blocked());
        for (Path candidate : List.of(paths.staging(),paths.process(),paths.publishing(), paths.finalDir())) {
            if (!Files.exists(candidate)) continue;
            Path isolated = paths.blocked().resolve("NONCONSUMABLE-" + candidate.getFileName());
            moveAtomically(candidate, isolated);
            try (var readyFiles = Files.walk(isolated)) {
                for (Path ready : readyFiles.filter(Files::isRegularFile)
                        .filter(path -> "READY".equals(path.getFileName().toString())).toList()) Files.delete(ready);
            }
            try (var files = Files.walk(isolated)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    if (file.getFileName().toString().endsWith(".json")) {
                        try {
                            ObjectNode json = (ObjectNode) JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
                            json.put("Consumable", false).put("ExecutionStatus", "BLOCKED"); writeJson(file, json);
                        } catch (Exception corrupt) {
                            String originalSha = sha(file); Path corruptRoot = isolated.resolve("corrupt"); Files.createDirectories(corruptRoot);
                            Path raw = corruptRoot.resolve(file.getFileName() + ".raw.NONCONSUMABLE"); moveAtomically(file, raw);
                            ObjectNode diagnostic = JSON.createObjectNode().put("Consumable", false).put("ExecutionStatus", "BLOCKED")
                                    .put("Reason", "CORRUPT_JSON_ISOLATED").put("OriginalSha256", originalSha)
                                    .put("RawEvidenceRef", isolated.relativize(raw).toString().replace('\\', '/'));
                            writeJson(corruptRoot.resolve(file.getFileName() + ".blocked.json"), diagnostic);
                        }
                    }
                }
            }
            assertNonConsumable(isolated);
        }
        ObjectNode blocked = JSON.createObjectNode().put("Consumable", false).put("ExecutionStatus", "BLOCKED")
                .put("Reason", reason).put("AutomaticRetryAllowed", false);
        writeJson(paths.blocked().resolve("BLOCKED.json"), blocked);
        if (Files.exists(paths.staging())||Files.exists(paths.process())||Files.exists(paths.publishing()) || Files.exists(paths.finalDir())) throw new IllegalStateException("FORMAL_NAMESPACE_NOT_REVOKED");
    }

    static void assertNonConsumable(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if ("READY".equals(file.getFileName().toString())) throw new IllegalStateException("READY_REMAINS");
                if (file.getFileName().toString().endsWith(".json")) {
                    JsonNode json = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
                    if (json.path("Consumable").asBoolean(true) || !"BLOCKED".equals(json.path("ExecutionStatus").asText()))
                        throw new IllegalStateException("CONSUMABLE_JSON_REMAINS");
                }
            }
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        java.util.HashSet<String> names = new java.util.HashSet<>(); node.fieldNames().forEachRemaining(names::add); return names;
    }
    private static void validateRunId(String runId) {
        if (SEALED_RUN_ID.equals(runId)) throw new IllegalStateException("SEALED_RUN_ID");
        if (!RUN_ID.matcher(runId).matches() || runId.contains("..") || runId.contains("/") || runId.contains("\\"))
            throw new IllegalStateException("RUN_ID_INVALID");
    }
    private static void validateProcessEvidence(Paths paths, String runId, Authorization authorization, ProcessEvidence process,Bindings bindings) throws Exception {
        String expectedCommand = String.join(" ", formalCommand(runId, authorization.ref(), sha(authorization.record()),bindings.authorizationConsumptionSha(), paths.staging()));
        if (!expectedCommand.equals(process.command()) || process.startedAt() == null || process.endedAt() == null
                || process.endedAt().isBefore(process.startedAt()) || process.osExitCode() != 0 || !process.wrapperLoaded()
                || !process.formalTestStarted() || !isSha(process.stdoutSha()) || !isSha(process.stderrSha()))
            throw new IllegalStateException("PROCESS_EVIDENCE_INVALID");
        if(!Files.isRegularFile(authorization.marker())||!sha(authorization.marker()).equals(bindings.authorizationConsumptionSha())
                ||!authorization.marker().getFileName().toString().equals(bindings.authorizationConsumptionRef()))throw new IllegalStateException("CONSUMPTION_BINDING_DRIFT");
        assertBindings(JSON.valueToTree(process),bindings);
        Path stdout = paths.process().resolve("process.stdout.txt"), stderr = paths.process().resolve("process.stderr.txt");
        if (!Files.isRegularFile(stdout) || !Files.isRegularFile(stderr) || !sha(stdout).equals(process.stdoutSha()) || !sha(stderr).equals(process.stderrSha()))
            throw new IllegalStateException("PROCESS_OUTPUT_INVALID");
    }
    private static void stampStagedBindings(Path staging,Bindings bindings)throws Exception{try(var files=Files.list(staging)){for(Path file:files.filter(path->path.getFileName().toString().endsWith(".json")).toList()){ObjectNode c=(ObjectNode)JSON.readTree(Files.readString(file));putBindings(c,bindings);writeJson(file,c);}}}
    private static void putBindings(ObjectNode node,Bindings bindings){node.put("JavaHostAggregateSha",bindings.javaHostAggregateSha()).put("FormalCommandDigest",bindings.formalCommandDigest()).put("AuthorizationRef",bindings.authorizationRef()).put("AuthorizationRecordSha",bindings.authorizationRecordSha()).put("AuthorizationConsumptionRef",bindings.authorizationConsumptionRef()).put("AuthorizationConsumptionSha",bindings.authorizationConsumptionSha());}
    private static List<ObjectNode> validateStagedCases(Path staging, String runId, Authorization authorization,Bindings bindings) throws Exception {
        List<Path> files;
        try (var stream = Files.list(staging)) { files = stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList(); }
        if (files.size() != PARAMETER_COUNT) throw new IllegalStateException("CASE_COUNT");
        Set<String> identities = new HashSet<>(); List<ObjectNode> index = new ArrayList<>();
        for (Path file : files) {
            JsonNode c = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            String identity = c.path("ScenarioId").asText() + "|" + c.path("SubcaseId").asText() + "|" + c.path("ParameterId").asText();
            if (!"PASS".equals(c.path("ExecutionStatus").asText())||c.path("Consumable").asBoolean(true)
                    ||!identities.add(identity) || !runId.equals(c.path("ExecutionRunId").asText()) || !authorization.ref().equals(c.path("AuthorizationRecordRef").asText())
                    || !IMPLEMENTATION_SHA.equals(c.path("ImplementationAggregateSha").asText()) || !MATRIX_IDENTITY_SHA.equals(c.path("IdentitySetSha").asText())
                    || !sha(authorization.record()).equals(c.path("AuthorizationRecordSha").asText())
                    ||!bindings.javaHostAggregateSha().equals(c.path("JavaHostAggregateSha").asText())||!bindings.formalCommandDigest().equals(c.path("FormalCommandDigest").asText())
                    ||!bindings.authorizationRef().equals(c.path("AuthorizationRef").asText())||!bindings.authorizationRecordSha().equals(c.path("AuthorizationRecordSha").asText())
                    ||!bindings.authorizationConsumptionRef().equals(c.path("AuthorizationConsumptionRef").asText())||!bindings.authorizationConsumptionSha().equals(c.path("AuthorizationConsumptionSha").asText())
                    || !FIXED_INPUTS.get("D3RegistrySha").equals(c.path("D3RegistrySha").asText())
                    || !"process-evidence.json".equals(c.path("ProcessEvidenceRef").asText()))
                throw new IllegalStateException("CASE_BINDING");
            for (String key : List.of("Input", "Expected", "Actual", "CompleteResponse", "Before", "After", "Delta", "WriteDelta23"))
                if (c.path(key).isMissingNode() || c.path(key).isNull()) throw new IllegalStateException("CASE_INCOMPLETE");
            JsonNode input = c.path("Input");
            for (String key : List.of("FixtureDigest", "CompleteRequest", "FixedInputs", "ImplementationFiles", "AuthorizationRef"))
                if (input.path(key).isMissingNode() || input.path(key).isNull()) throw new IllegalStateException("CASE_INPUT_INCOMPLETE");
            if (!input.path("FixedInputs").equals(JSON.valueToTree(FIXED_INPUTS)) || !input.path("ImplementationFiles").equals(JSON.valueToTree(IMPLEMENTATION_FILES))
                    || !authorization.ref().equals(input.path("AuthorizationRef").asText())) throw new IllegalStateException("CASE_INPUT_BINDING");
            if (!c.path("Expected").path("completeResponse").equals(c.path("CompleteResponse"))
                    || !c.path("Actual").path("completeResponse").equals(c.path("CompleteResponse"))) throw new IllegalStateException("CASE_RESPONSE_MISMATCH");
            JsonNode expected=c.path("Expected"),actual=c.path("Actual");
            if(!fieldNames(expected).equals(Set.of("projectCode","stateCode","validatorRejected","queryCallDelta","writeDelta23","completeResponse"))
                    ||!fieldNames(actual).equals(Set.of("projectCode","stateCode","validatorRejected","completeResponse","fixtureDigest","completeRequest"))
                    ||!expected.path("projectCode").equals(actual.path("projectCode"))||!expected.path("stateCode").equals(actual.path("stateCode"))
                    ||!expected.path("validatorRejected").equals(actual.path("validatorRejected"))||expected.path("queryCallDelta").asLong()!=c.path("QueryCallDelta").asLong()
                    ||!expected.path("writeDelta23").equals(c.path("WriteDelta23")))throw new IllegalStateException("CASE_ORACLE_MISMATCH");
            validateCounters(c);
            ObjectNode entry = JSON.createObjectNode().put("File", file.getFileName().toString()).put("Sha256", sha(file)).put("Identity", identity); index.add(entry);
        }
        if (!textSha(identities.stream().toList()).equals(MATRIX_IDENTITY_SHA)) throw new IllegalStateException("CASE_IDENTITY_SHA");
        return index;
    }
    private static void validateCounters(JsonNode c) {
        if (!fieldNames(c.path("Before")).equals(COUNT_KEYS) || !fieldNames(c.path("After")).equals(COUNT_KEYS)
                || !fieldNames(c.path("Delta")).equals(COUNT_KEYS) || !fieldNames(c.path("WriteDelta23")).equals(WRITE_KEYS))
            throw new IllegalStateException("COUNTER_KEYS");
        for (String key : COUNT_KEYS) {
            JsonNode before=c.path("Before").path(key),after=c.path("After").path(key),delta=c.path("Delta").path(key);
            if (!before.isIntegralNumber() || !after.isIntegralNumber() || !delta.isIntegralNumber() || after.asLong()-before.asLong()!=delta.asLong())
                throw new IllegalStateException("COUNTER_ARITHMETIC");
        }
        for(String key:WRITE_KEYS) if(c.path("Delta").path(key).asLong()!=0 || c.path("WriteDelta23").path(key).asLong()!=0) throw new IllegalStateException("WRITE_DELTA");
        if(c.path("QueryCallDelta").asLong()!=1 || c.path("Delta").path("QueryCall").asLong()!=1) throw new IllegalStateException("QUERY_DELTA");
    }
    private static void validatePublicationFiles(Path publishing, ProcessEvidence process, int indexCount,Bindings bindings) throws Exception {
        for(String name:List.of("index.json","package.json","manifest.json","process-evidence.json","process.stdout.txt","process.stderr.txt"))
            if(!Files.isRegularFile(publishing.resolve(name))) throw new IllegalStateException("PUBLICATION_FILE_MISSING|"+name);
        JsonNode index=JSON.readTree(Files.readString(publishing.resolve("index.json")));assertBindings(index,bindings);JsonNode entries=index.path("Entries");if(!entries.isArray()||entries.size()!=indexCount)throw new IllegalStateException("INDEX_INVALID");
        Set<String>indexIdentities=new HashSet<>();for(JsonNode entry:entries){Path caseFile=publishing.resolve(entry.path("File").asText()).normalize();if(!caseFile.getParent().equals(publishing)||!Files.isRegularFile(caseFile)||!sha(caseFile).equals(entry.path("Sha256").asText())||!indexIdentities.add(entry.path("Identity").asText()))throw new IllegalStateException("INDEX_ENTRY_INVALID");}
        JsonNode packageJson=JSON.readTree(Files.readString(publishing.resolve("package.json")));List<String>packageLines=new ArrayList<>();
        try(var files=Files.list(publishing)){for(Path file:files.filter(Files::isRegularFile).filter(path->!path.getFileName().toString().equals("package.json")).sorted(Comparator.comparing(path->path.getFileName().toString())).toList())packageLines.add(file.getFileName()+"|"+sha(file));}
        assertBindings(packageJson,bindings);if(!textSha(packageLines).equals(packageJson.path("PackageSha256").asText())||packageJson.path("FileCount").asInt()!=packageLines.size())throw new IllegalStateException("PACKAGE_INVALID");
        JsonNode persisted=JSON.readTree(Files.readString(publishing.resolve("process-evidence.json")));
        if(!fieldNames(persisted).equals(Set.of("command","startedAt","endedAt","osExitCode","stdoutSha","stderrSha","wrapperLoaded","formalTestStarted","javaHostAggregateSha","formalCommandDigest","authorizationRef","authorizationRecordSha","authorizationConsumptionRef","authorizationConsumptionSha"))
                ||!process.command().equals(persisted.path("command").asText())||persisted.path("startedAt").asDouble()!=epochDecimal(process.startedAt())
                ||persisted.path("endedAt").asDouble()!=epochDecimal(process.endedAt())||persisted.path("osExitCode").asInt()!=process.osExitCode()
                ||!process.stdoutSha().equals(persisted.path("stdoutSha").asText())||!process.stderrSha().equals(persisted.path("stderrSha").asText())
                ||persisted.path("wrapperLoaded").asBoolean()!=process.wrapperLoaded()||persisted.path("formalTestStarted").asBoolean()!=process.formalTestStarted())
            throw new IllegalStateException("PROCESS_PERSISTENCE_INVALID");
        assertBindings(persisted,bindings);assertBindings(JSON.readTree(Files.readString(publishing.resolve("manifest.json"))),bindings);
        if(!sha(publishing.resolve("process.stdout.txt")).equals(process.stdoutSha())||!sha(publishing.resolve("process.stderr.txt")).equals(process.stderrSha()))throw new IllegalStateException("PROCESS_OUTPUT_PERSISTENCE_INVALID");
    }
    private static boolean isSha(String value){return value!=null&&value.matches("^[A-F0-9]{64}$");}
    private static void assertBindings(JsonNode node,Bindings b){if(!b.javaHostAggregateSha().equals(bindingValue(node,"JavaHostAggregateSha","javaHostAggregateSha"))||!b.formalCommandDigest().equals(bindingValue(node,"FormalCommandDigest","formalCommandDigest"))||!b.authorizationRef().equals(bindingValue(node,"AuthorizationRef","authorizationRef"))||!b.authorizationRecordSha().equals(bindingValue(node,"AuthorizationRecordSha","authorizationRecordSha"))||!b.authorizationConsumptionRef().equals(bindingValue(node,"AuthorizationConsumptionRef","authorizationConsumptionRef"))||!b.authorizationConsumptionSha().equals(bindingValue(node,"AuthorizationConsumptionSha","authorizationConsumptionSha")))throw new IllegalStateException("END_TO_END_BINDING_DRIFT");}
    private static String bindingValue(JsonNode node,String canonical,String javaName){return node.has(canonical)?node.path(canonical).asText():node.path(javaName).asText();}
    private static double epochDecimal(Instant instant){return instant.getEpochSecond()+instant.getNano()/1_000_000_000d;}
    private static Set<String> union(Set<String>a,Set<String>b){Set<String>r=new HashSet<>(a);r.addAll(b);return Set.copyOf(r);}
    private static String textSha(List<String> lines)throws Exception{List<String>sorted=lines.stream().sorted().toList();return digest(String.join("\n",sorted).getBytes(StandardCharsets.UTF_8));}
    private static String digest(byte[]bytes)throws Exception{return java.util.HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void writeJson(Path path, JsonNode node) throws IOException {
        Files.writeString(path, JSON.writeValueAsString(node), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }
    private static void deleteTree(Path root)throws IOException{if(!Files.exists(root))return;try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    private static void moveAtomically(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException exception) { throw new IOException("ATOMIC_MOVE_NOT_SUPPORTED", exception); }
    }
    private static String sha(Path path) throws Exception {
        return digest(Files.readAllBytes(path));
    }
}
