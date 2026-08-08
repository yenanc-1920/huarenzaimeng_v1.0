package com.huarenzaimeng.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** UTF-8 compiled outer wrapper. It is never source-launched or interpreted through CMD. */
public final class P021HostPreflightOuterWrapper {
    static final String TEST_RUN_ID="P021-TECH-HOST-PREFLIGHT-TEST-0001";
    static final String SCOPE="P021_TECHNICAL_HOST_PREFLIGHT_0_SCENARIO";
    static final Path AUTH_ROOT=Path.of("\u9879\u76ee\u7ba1\u7406/\u6b63\u5f0f\u4ea4\u4ed8/D4-\u5f00\u53d1\u8ba1\u5212\u4e0e\u5de5\u7a0b\u51c6\u5907/\u6388\u6743\u8bb0\u5f55/P021-HOST-PREFLIGHT");
    static final Path REPORT_ROOT=Path.of("\u9879\u76ee\u7ba1\u7406/\u6b63\u5f0f\u4ea4\u4ed8/D4-\u5f00\u53d1\u8ba1\u5212\u4e0e\u5de5\u7a0b\u51c6\u5907/\u8bc1\u636e/P021-HOST-PREFLIGHT");
    static final Path JAVA=Path.of("C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/java.exe");
    static final String JAVA_SHA="B3AFE83E1AB067DA4C56F1A7B2BA4C14EC832D694333F35B2B45178E9AC596EF";
    static final Path MAVEN_HOME=Path.of("C:/Users/yenanc/tools/apache-maven-3.9.6");
    static final Path MAVEN_LAUNCHER=MAVEN_HOME.resolve("boot/plexus-classworlds-2.7.0.jar");
    static final String MAVEN_LAUNCHER_SHA="C60AE538BA66ADBC06AAE205FBE2306211D3D213AB6DF3239EC03CDDE2458AD6";
    static final Path MAVEN_CONFIG=MAVEN_HOME.resolve("bin/m2.conf");
    static final String MAVEN_CONFIG_SHA="E336769BF93A902BAA7A3E827BA55E4CEF7DE4AF2ED1A9541A4261094D748BA9";
    static final Path OFFLINE_REPOSITORY=Path.of("E:/workspace/huarenzaimeng/.m2-local/repository");
    static final Path FIXED_PROJECT_ROOT=Path.of("E:/workspace/huarenzaimeng");
    static final String OFFLINE_REPOSITORY_IDENTITY_SHA="C45CF5E2AD1E8A3021237F922ED2C94EDF85C7281C297A88BE09BFF8F5762BB5";
    static final Path OFFLINE_REPOSITORY_MANIFEST=Path.of("apps/api/manifests/P021-host-offline-repository-identity.txt");
    static final String OFFLINE_REPOSITORY_MANIFEST_SHA="D57555BEFF4151B6E9B23BEFEA49B002E0F17C4156CAC35BAE42F783F37AC0A2";
    static final Map<String,String> OFFLINE_REPOSITORY_FILES=offlineRepositoryFiles();
    private static final Pattern SHA=Pattern.compile("^[A-F0-9]{64}$");
    private static final Pattern FORBIDDEN=Pattern.compile("[%!\"&|<>^]");

    public static void main(String[] args)throws Exception{
        Path repository=Path.of("").toAbsolutePath().normalize();
        int exit=run(args,repository,mavenLauncherPrefix(repository),JAVA,JAVA_SHA);
        if(exit!=0)System.exit(exit);
    }

    static List<String> mavenLauncherPrefix(Path repository)throws Exception{
        if(!JAVA_SHA.equals(sha(JAVA))||!MAVEN_LAUNCHER_SHA.equals(sha(MAVEN_LAUNCHER))||!MAVEN_CONFIG_SHA.equals(sha(MAVEN_CONFIG)))throw new IllegalStateException("MAVEN_LAUNCHER_BINDING");
        return List.of(JAVA.toAbsolutePath().normalize().toString(),"-Dclassworlds.conf="+MAVEN_CONFIG.toAbsolutePath().normalize(),
                "-Dmaven.home="+MAVEN_HOME.toAbsolutePath().normalize(),"-Dmaven.multiModuleProjectDirectory="+repository.toAbsolutePath().normalize(),
                "-classpath",MAVEN_LAUNCHER.toAbsolutePath().normalize().toString(),"org.codehaus.plexus.classworlds.launcher.Launcher");
    }

    static int run(String[]args,Path repository,List<String> executablePrefix,Path executableForSha,String expectedExecutableSha)throws Exception{
        if(args.length!=3||!SHA.matcher(args[2]).matches())throw new IllegalArgumentException("OUTER_ARGUMENTS");
        String runId=P021HostPreflightRunIdPolicy.validate(args[0]);
        if(FORBIDDEN.matcher(args[1]).find()||args[1].contains(".."))throw new IllegalArgumentException("AUTH_RECORD_CHARACTERS");
        Path repo=repository.toAbsolutePath().normalize();
        Path auth=validateAuthorizationLocation(repo,args[1]);
        if(!expectedExecutableSha.equals(sha(executableForSha)))throw new IllegalStateException("EXECUTABLE_SHA");
        validateOfflineRepository();
        Path root=repo.resolve(REPORT_ROOT).normalize(),process=root.resolve(".outer-process-"+runId),report=root.resolve(runId),blocked=root.resolve(".blocked-"+runId);
        if(Files.exists(process)||Files.exists(report)||Files.exists(blocked))throw new IllegalStateException("OUTER_RUN_NOT_FRESH");
        Files.createDirectories(process);Path stdout=process.resolve("process.stdout.txt"),stderr=process.resolve("process.stderr.txt");
        List<String>command=new ArrayList<>(executablePrefix);command.addAll(List.of("-o","-Dmaven.repo.local="+OFFLINE_REPOSITORY.toAbsolutePath().normalize(),"-pl","apps/api","-am","-Dtest=P021SameChainHostPreflightExecutionTest",
                "-Dsurefire.failIfNoSpecifiedTests=false","-Dp021.preflight.runId="+runId,"-Dp021.preflight.authorizationRecord="+auth,
                "-Dp021.preflight.authorizationRecordSha="+args[2],"test"));
        Instant started=Instant.now();int exit=-1;Instant ended;
        try{
            Process child=new ProcessBuilder(command).directory(repo.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
            exit=child.waitFor();ended=Instant.now();
            if(exit!=0||!Files.isRegularFile(report.resolve("preflight-result.json")))throw new IllegalStateException("OUTER_CHILD_FAILED|"+exit);
            Files.move(stdout,report.resolve("outer-process.stdout.txt"),StandardCopyOption.REPLACE_EXISTING);
            Files.move(stderr,report.resolve("outer-process.stderr.txt"),StandardCopyOption.REPLACE_EXISTING);
            write(report.resolve("outer-process-evidence.json"),json(runId,"PASS",exit,started,ended));Files.delete(process);return 0;
        }catch(Exception failure){
            ended=Instant.now();Files.createDirectories(blocked);
            if(Files.isRegularFile(stdout))Files.move(stdout,blocked.resolve("outer-process.stdout.txt"),StandardCopyOption.REPLACE_EXISTING);
            if(Files.isRegularFile(stderr))Files.move(stderr,blocked.resolve("outer-process.stderr.txt"),StandardCopyOption.REPLACE_EXISTING);
            write(blocked.resolve("BLOCKED.outer.json"),json(runId,"BLOCKED",exit,started,ended));if(Files.exists(process))Files.delete(process);return exit==0?71:(exit<0?72:exit);
        }
    }

    static Path validateAuthorizationLocation(Path repository,String raw){
        if(FORBIDDEN.matcher(raw).find()||raw.contains(".."))throw new IllegalArgumentException("AUTH_RECORD_CHARACTERS");
        Path repo=repository.toAbsolutePath().normalize(),allowed=repo.resolve(AUTH_ROOT).normalize();
        Path auth=Path.of(raw).toAbsolutePath().normalize();
        if(!auth.getParent().equals(allowed)||!auth.getFileName().toString().endsWith(".json")||!Files.isRegularFile(auth))throw new IllegalArgumentException("AUTH_RECORD_LOCATION");
        return auth;
    }

    static void validateOfflineRepository()throws Exception{
        Path fixed=OFFLINE_REPOSITORY.toAbsolutePath().normalize();
        if(!fixed.equals(Path.of("E:/workspace/huarenzaimeng/.m2-local/repository").toAbsolutePath().normalize())||!Files.isDirectory(fixed)||!Files.isReadable(fixed))throw new IllegalStateException("OFFLINE_REPOSITORY_BOUNDARY");
        List<String> lines=new ArrayList<>();for(var entry:OFFLINE_REPOSITORY_FILES.entrySet()){Path file=fixed.resolve(entry.getKey()).normalize();if(!file.startsWith(fixed)||!Files.isRegularFile(file)||!Files.isReadable(file)||!entry.getValue().equals(sha(file)))throw new IllegalStateException("OFFLINE_REPOSITORY_FILE_BINDING|"+entry.getKey());lines.add(entry.getKey().replace('\\','/')+"|"+entry.getValue());}
        Path manifest=FIXED_PROJECT_ROOT.resolve(OFFLINE_REPOSITORY_MANIFEST).toAbsolutePath().normalize();
        if(!Files.isRegularFile(manifest)||!Files.isReadable(manifest)||!OFFLINE_REPOSITORY_MANIFEST_SHA.equals(sha(manifest)))throw new IllegalStateException("OFFLINE_REPOSITORY_MANIFEST_FILE");
        if(!lines.equals(Files.readAllLines(manifest,StandardCharsets.UTF_8)))throw new IllegalStateException("OFFLINE_REPOSITORY_MANIFEST_CONTENT");
        if(!OFFLINE_REPOSITORY_IDENTITY_SHA.equals(textSha(lines)))throw new IllegalStateException("OFFLINE_REPOSITORY_IDENTITY");
    }
    private static Map<String,String> offlineRepositoryFiles(){Map<String,String> files=new LinkedHashMap<>();
        files.put("org/springframework/boot/spring-boot-starter-parent/3.5.16/spring-boot-starter-parent-3.5.16.pom","D00E26B8F354697F9F134822AF44C8CC79B5F7B553F8BB1043220BBDC11462C2");
        files.put("org/springframework/boot/spring-boot-dependencies/3.5.16/spring-boot-dependencies-3.5.16.pom","B99B1870802B9A652221C99C491678F37773A8C0AD14E80BD6B275AE9201638A");
        files.put("org/apache/maven/plugins/maven-surefire-plugin/3.5.6/maven-surefire-plugin-3.5.6.pom","A9FE60995E2BF4146E3BDD4E4A12737BDAFD23CFFA52626AF57E2162CB077EE1");files.put("org/apache/maven/plugins/maven-surefire-plugin/3.5.6/maven-surefire-plugin-3.5.6.jar","5FEA3D2F0AD1D790717D66AA31D995C22CA528612316F8380D67C40367AF3464");
        files.put("org/apache/maven/plugins/maven-compiler-plugin/3.14.1/maven-compiler-plugin-3.14.1.pom","E8CB177FF488BB127B8E519D29C67456288DEEA5609A66F891CFF99573C72AF2");files.put("org/apache/maven/plugins/maven-compiler-plugin/3.14.1/maven-compiler-plugin-3.14.1.jar","85BC857C3BF91C87B65DFC15235154EB83B8F4539F0AFBC7E6C93EB4A0BCDD38");
        files.put("org/apache/maven/plugins/maven-resources-plugin/3.3.1/maven-resources-plugin-3.3.1.pom","3269D0A6E3CD614A29486F57FC86488B0F1E458A11BEBC61F9408FD6C7CF85AE");files.put("org/apache/maven/plugins/maven-resources-plugin/3.3.1/maven-resources-plugin-3.3.1.jar","EB4069C7FE50A313B3F5295CCD214F30402F63971C26F443F7F3E798BE8CC2A7");return java.util.Collections.unmodifiableMap(files);}
    private static String json(String runId,String status,int exit,Instant started,Instant ended){return "{\"ExecutionStatus\":\""+status+"\",\"Consumable\":false,\"AutomaticRetryAllowed\":false,\"RunId\":\""+runId+"\",\"ExecutionScope\":\""+SCOPE+"\",\"Offline\":true,\"OfflineRepositoryPath\":\""+OFFLINE_REPOSITORY.toAbsolutePath().normalize().toString().replace("\\","\\\\")+"\",\"OfflineRepositoryIdentitySha\":\""+OFFLINE_REPOSITORY_IDENTITY_SHA+"\",\"OfflineRepositoryManifestPath\":\""+OFFLINE_REPOSITORY_MANIFEST.toString().replace("\\","/")+"\",\"OfflineRepositoryManifestSha\":\""+OFFLINE_REPOSITORY_MANIFEST_SHA+"\",\"StartedAt\":\""+started+"\",\"EndedAt\":\""+ended+"\",\"OsExitCode\":"+exit+",\"StdoutRef\":\"outer-process.stdout.txt\",\"StderrRef\":\"outer-process.stderr.txt\",\"FormalScenarioCount\":0,\"FormalTestStarted\":false,\"EvidenceWritten\":false,\"ReadyCount\":0}";}
    private static void write(Path path,String value)throws Exception{Files.writeString(path,value,StandardCharsets.UTF_8);}
    private static String sha(Path path)throws Exception{return java.util.HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static String textSha(List<String> lines)throws Exception{return java.util.HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(String.join("\n",lines).getBytes(StandardCharsets.UTF_8)));}
}
