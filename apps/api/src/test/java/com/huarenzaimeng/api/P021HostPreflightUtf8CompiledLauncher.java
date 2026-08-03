package com.huarenzaimeng.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/** ASCII-only source launcher which explicitly compiles the frozen wrapper as UTF-8 before class launch. */
public final class P021HostPreflightUtf8CompiledLauncher {
    static final Path JAVA=Path.of("C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/java.exe");
    static final String JAVA_SHA="B3AFE83E1AB067DA4C56F1A7B2BA4C14EC832D694333F35B2B45178E9AC596EF";
    static final Path JAVAC=Path.of("C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot/bin/javac.exe");
    static final String JAVAC_SHA="988446CEEC6E33CE0420DDF2489BB13D3A2278B4C8745309123DFA8F2C0DD0CE";
    static final Path WRAPPER_SOURCE=Path.of("apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightOuterWrapper.java");
    static final String WRAPPER_SHA="A9A3220FA9FCAC0CF9D99C87EE00639819E417BD6E8F1AD538D35A3D31ED4259";
    static final Path POLICY_SOURCE=Path.of("apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightRunIdPolicy.java");
    static final String POLICY_SHA="D915241320B9EF8081D614CE444C40F6306C99BC0E6BFAD5CEEF332F0BD68D6A";
    static final String WRAPPER_CLASS="com.huarenzaimeng.api.P021HostPreflightOuterWrapper";
    static final Path REPORT_ROOT=Path.of("\u9879\u76ee\u7ba1\u7406/\u6b63\u5f0f\u4ea4\u4ed8/D4-\u5f00\u53d1\u8ba1\u5212\u4e0e\u5de5\u7a0b\u51c6\u5907/\u8bc1\u636e/P021-HOST-PREFLIGHT");
    private static final Pattern SHA=Pattern.compile("^[A-F0-9]{64}$");
    private static final Pattern FORBIDDEN=Pattern.compile("[%!\"&|<>^]");

    public static void main(String[]args)throws Exception{
        int exit=run(args,Path.of("").toAbsolutePath().normalize(),JAVAC,JAVAC_SHA,JAVA,JAVA_SHA,WRAPPER_SOURCE,WRAPPER_SHA,WRAPPER_CLASS);
        if(exit!=0)System.exit(exit);
    }

    static int run(String[]args,Path repository,Path javac,String javacSha,Path java,String javaSha,Path source,String sourceSha,String mainClass)throws Exception{
        if(args.length!=3||!SHA.matcher(args[2]).matches())throw new IllegalArgumentException("UTF8_LAUNCHER_ARGUMENTS");
        if(FORBIDDEN.matcher(args[1]).find()||args[1].contains(".."))throw new IllegalArgumentException("UTF8_LAUNCHER_AUTH_CHARACTERS");
        if(!javacSha.equals(sha(javac))||!javaSha.equals(sha(java)))throw new IllegalStateException("UTF8_LAUNCHER_JDK_BINDING");
        Path repo=repository.toAbsolutePath().normalize(),wrapper=repo.resolve(source).normalize(),policy=repo.resolve(POLICY_SOURCE).normalize();
        if(!sourceSha.equals(sha(wrapper))||!POLICY_SHA.equals(sha(policy)))throw new IllegalStateException("UTF8_LAUNCHER_SOURCE_SHA");
        String runId=P021HostPreflightRunIdPolicy.validate(args[0]);Path root=repo.resolve(REPORT_ROOT).normalize();
        Path work=root.resolve(".utf8-compile-"+runId),report=root.resolve(runId),blocked=root.resolve(".blocked-"+runId);
        if(Files.exists(work)||Files.exists(report)||Files.exists(blocked))throw new IllegalStateException("UTF8_LAUNCHER_RUN_NOT_FRESH");
        Files.createDirectories(work);Path classes=Files.createDirectories(work.resolve("classes"));
        Path compileOut=work.resolve("compile.stdout.txt"),compileErr=work.resolve("compile.stderr.txt");
        Instant started=Instant.now();int compileExit=-1,runExit=-1;
        try{
            List<String>compile=List.of(javac.toString(),"-encoding","UTF-8","-d",classes.toString(),policy.toString(),wrapper.toString());
            compileExit=process(compile,repo,compileOut,compileErr);
            if(compileExit!=0||!Files.isRegularFile(classes.resolve(mainClass.replace('.','/')+".class")))throw new IllegalStateException("UTF8_COMPILE_FAILED|"+compileExit);
            List<String>command=new ArrayList<>(List.of(java.toString(),"-cp",classes.toString(),mainClass));command.addAll(List.of(args));
            runExit=process(command,repo,work.resolve("runtime.stdout.txt"),work.resolve("runtime.stderr.txt"));
            if(runExit!=0||!Files.isRegularFile(report.resolve("preflight-result.json")))throw new IllegalStateException("UTF8_COMPILED_WRAPPER_FAILED|"+runExit);
            moveLogs(work,report);write(report.resolve("utf8-compile-evidence.json"),json(runId,"PASS",compileExit,runExit,started,Instant.now()));deleteTree(work);return 0;
        }catch(Exception failure){
            Files.createDirectories(blocked);quarantineReport(report,blocked);moveLogs(work,blocked);write(blocked.resolve("BLOCKED.utf8-launcher.json"),json(runId,"BLOCKED",compileExit,runExit,started,Instant.now()));deleteReady(blocked);deleteTree(report);deleteTree(work);return compileExit!=0?73:(runExit>0?runExit:74);
        }
    }

    private static int process(List<String>command,Path cwd,Path stdout,Path stderr)throws Exception{return new ProcessBuilder(command).directory(cwd.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start().waitFor();}
    private static void moveLogs(Path from,Path to)throws Exception{Files.createDirectories(to);for(String name:List.of("compile.stdout.txt","compile.stderr.txt","runtime.stdout.txt","runtime.stderr.txt")){Path p=from.resolve(name);if(Files.isRegularFile(p))Files.move(p,to.resolve(name),StandardCopyOption.REPLACE_EXISTING);}}
    private static void quarantineReport(Path report,Path blocked)throws Exception{if(!Files.exists(report))return;try(var s=Files.walk(report)){for(Path p:s.filter(Files::isRegularFile).toList()){Path relative=report.relativize(p),target=blocked.resolve("NONCONSUMABLE.report").resolve(relative);Files.createDirectories(target.getParent());Files.move(p,target,StandardCopyOption.REPLACE_EXISTING);}}}
    private static void deleteReady(Path root)throws Exception{if(!Files.exists(root))return;try(var s=Files.walk(root)){for(Path p:s.filter(x->x.getFileName().toString().startsWith("READY")).toList())Files.deleteIfExists(p);}}
    private static void deleteTree(Path root)throws Exception{if(!Files.exists(root))return;try(var s=Files.walk(root)){for(Path p:s.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    private static String json(String runId,String status,int compileExit,int runExit,Instant started,Instant ended){return "{\"ExecutionStatus\":\""+status+"\",\"Consumable\":false,\"AutomaticRetryAllowed\":false,\"RunId\":\""+runId+"\",\"CompileEncoding\":\"UTF-8\",\"CompileExit\":"+compileExit+",\"WrapperExit\":"+runExit+",\"StartedAt\":\""+started+"\",\"EndedAt\":\""+ended+"\",\"FormalScenarioCount\":0,\"FormalTestStarted\":false,\"EvidenceWritten\":false,\"ReadyCount\":0}";}
    private static void write(Path path,String value)throws Exception{Files.writeString(path,value,StandardCharsets.UTF_8);}
    private static String sha(Path path)throws Exception{return HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
}
