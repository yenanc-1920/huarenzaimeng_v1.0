package com.huarenzaimeng.api;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

/** Pure-JDK ordinary self-test. It never invokes the HOST preflight or formal evidence runner. */
public final class P021HostPreflightUtf8CompiledLauncherSelfTest {
    public static void main(String[]args)throws Exception{
        Path repository=Path.of("").toAbsolutePath().normalize(),temp=Files.createTempDirectory("p021-host-utf8-selftest-");
        try{
            verifyRealUtf8Compilation(repository,temp.resolve("real-classes"));
            verifySharedRunIdPolicy();
            verifyChineseAuthorizationBoundary(temp.resolve("auth-boundary"));
            verifyCompileFailure(temp.resolve("compile-failure"));
            verifyFakeExit(temp.resolve("fake-zero"),0);verifyFakeExit(temp.resolve("fake-nonzero"),19);
            System.out.println("P021_HOST_UTF8_COMPILED_LAUNCHER_SELFTEST: PASS (6/6)");
        }finally{deleteTree(temp);}
    }

    private static void verifySharedRunIdPolicy()throws Exception{
        String future="P021-TECH-HOST-PREFLIGHT-FUTURE-SAMPLE-0001";check(P021HostPreflightRunIdPolicy.validate(future).equals(future),"FUTURE_FORMAT");
        expectState("PREFLIGHT_RUN_ID_INVALID_OR_DENIED",()->P021HostPreflightRunIdPolicy.validate("P021-TECH-HOST-PREFLIGHT-20260803-001"));
        expectState("PREFLIGHT_RUN_ID_INVALID_OR_DENIED",()->P021HostPreflightRunIdPolicy.validate("P021-TECH-HOST-PREFLIGHT-20260803-002"));
        P021HostPreflightRunIdPolicy.validateAuthorizationBinding(future,future);
        expectState("PREFLIGHT_AUTHORIZATION_RUN_ID_MISMATCH",()->P021HostPreflightRunIdPolicy.validateAuthorizationBinding(future,"P021-TECH-HOST-PREFLIGHT-FUTURE-OTHER-0001"));
    }

    private static void verifyRealUtf8Compilation(Path repository,Path classes)throws Exception{
        Files.createDirectories(classes);Path source=repository.resolve(P021HostPreflightUtf8CompiledLauncher.WRAPPER_SOURCE);
        Path policy=repository.resolve(P021HostPreflightUtf8CompiledLauncher.POLICY_SOURCE);Process p=new ProcessBuilder(P021HostPreflightUtf8CompiledLauncher.JAVAC.toString(),"-encoding","UTF-8","-d",classes.toString(),policy.toString(),source.toString()).redirectErrorStream(true).start();
        String output=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);check(p.waitFor()==0,"REAL_UTF8_COMPILE|"+output);
        try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},null)){Class<?> type=loader.loadClass(P021HostPreflightUtf8CompiledLauncher.WRAPPER_CLASS);var field=type.getDeclaredField("AUTH_ROOT");field.setAccessible(true);check(field.get(null).toString().replace('\\','/').equals("项目管理/正式交付/D4-开发计划与工程准备/授权记录/P021-HOST-PREFLIGHT"),"CHINESE_ROOT_RUNTIME");}
    }

    private static void verifyChineseAuthorizationBoundary(Path repository)throws Exception{
        Files.createDirectories(repository);Path allowed=repository.resolve(P021HostPreflightOuterWrapper.AUTH_ROOT);Files.createDirectories(allowed);Path auth=allowed.resolve("auth.json");Files.writeString(auth,"{}");
        check(P021HostPreflightOuterWrapper.validateAuthorizationLocation(repository,auth.toString()).equals(auth.toAbsolutePath().normalize()),"CHINESE_ALLOWED");
        Path parent=repository.resolve("auth.json");Files.writeString(parent,"{}");expect("AUTH_RECORD_LOCATION",()->P021HostPreflightOuterWrapper.validateAuthorizationLocation(repository,parent.toString()));
        Path similar=repository.resolve(P021HostPreflightOuterWrapper.AUTH_ROOT+"-SIMILAR");Files.createDirectories(similar);Path similarFile=similar.resolve("auth.json");Files.writeString(similarFile,"{}");expect("AUTH_RECORD_LOCATION",()->P021HostPreflightOuterWrapper.validateAuthorizationLocation(repository,similarFile.toString()));
        Path child=allowed.resolve("child");Files.createDirectories(child);Path childFile=child.resolve("auth.json");Files.writeString(childFile,"{}");expect("AUTH_RECORD_LOCATION",()->P021HostPreflightOuterWrapper.validateAuthorizationLocation(repository,childFile.toString()));
        expect("AUTH_RECORD_CHARACTERS",()->P021HostPreflightOuterWrapper.validateAuthorizationLocation(repository,child.resolve("../auth.json").toString()));
    }

    private static void verifyCompileFailure(Path repository)throws Exception{
        Files.createDirectories(repository);installPolicy(repository);Path source=repository.resolve("Broken.java");Files.writeString(source,"this is not java",StandardCharsets.UTF_8);String runId="P021-TECH-HOST-PREFLIGHT-SELFTEST-BAD-COMPILE";
        int exit=P021HostPreflightUtf8CompiledLauncher.run(new String[]{runId,repository.resolve("auth.json").toString(),"A".repeat(64)},repository,P021HostPreflightUtf8CompiledLauncher.JAVAC,P021HostPreflightUtf8CompiledLauncher.JAVAC_SHA,P021HostPreflightUtf8CompiledLauncher.JAVA,P021HostPreflightUtf8CompiledLauncher.JAVA_SHA,Path.of("Broken.java"),sha(source),"Broken");
        Path root=repository.resolve(P021HostPreflightUtf8CompiledLauncher.REPORT_ROOT),blocked=root.resolve(".blocked-"+runId);check(exit==73&&Files.isRegularFile(blocked.resolve("BLOCKED.utf8-launcher.json"))&&!Files.exists(root.resolve(runId))&&readyCount(root)==0,"COMPILE_FAILURE_BLOCKED");
    }

    private static void verifyFakeExit(Path repository,int expectedExit)throws Exception{
        Files.createDirectories(repository);installPolicy(repository);String suffix=expectedExit==0?"ZERO":"NONZERO",runId="P021-TECH-HOST-PREFLIGHT-SELFTEST-"+suffix+"-0001";Path source=repository.resolve("FakeWrapper.java");String report="项目管理/正式交付/D4-开发计划与工程准备/证据/P021-HOST-PREFLIGHT/";
        Files.writeString(source,"import java.nio.file.*; public class FakeWrapper { public static void main(String[] a) throws Exception { Path r=Path.of(\""+report+"\").resolve(a[0]); Files.createDirectories(r); Files.writeString(r.resolve(\"preflight-result.json\"),\"{}\"); System.out.println(\"FAKE_UTF8_WRAPPER\"); System.exit("+expectedExit+"); }}",StandardCharsets.UTF_8);
        int actual=P021HostPreflightUtf8CompiledLauncher.run(new String[]{runId,repository.resolve("auth.json").toString(),"A".repeat(64)},repository,P021HostPreflightUtf8CompiledLauncher.JAVAC,P021HostPreflightUtf8CompiledLauncher.JAVAC_SHA,P021HostPreflightUtf8CompiledLauncher.JAVA,P021HostPreflightUtf8CompiledLauncher.JAVA_SHA,Path.of("FakeWrapper.java"),sha(source),"FakeWrapper");Path root=repository.resolve(P021HostPreflightUtf8CompiledLauncher.REPORT_ROOT);
        if(expectedExit==0)check(actual==0&&Files.isRegularFile(root.resolve(runId).resolve("utf8-compile-evidence.json"))&&readyCount(root)==0,"FAKE_ZERO");else check(actual==expectedExit&&Files.isRegularFile(root.resolve(".blocked-"+runId).resolve("BLOCKED.utf8-launcher.json"))&&!Files.exists(root.resolve(runId))&&readyCount(root)==0,"FAKE_NONZERO");
    }

    private static long readyCount(Path root)throws Exception{if(!Files.exists(root))return 0;try(var s=Files.walk(root)){return s.filter(p->p.getFileName().toString().startsWith("READY")).count();}}
    private static void installPolicy(Path repository)throws Exception{Path target=repository.resolve(P021HostPreflightUtf8CompiledLauncher.POLICY_SOURCE);Files.createDirectories(target.getParent());Files.copy(Path.of("apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightRunIdPolicy.java").toAbsolutePath(),target);}
    private static String sha(Path p)throws Exception{return HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    private static void check(boolean condition,String code){if(!condition)throw new IllegalStateException(code);}
    private static void expect(String message,Throwing task)throws Exception{try{task.run();throw new IllegalStateException("EXPECTED_"+message);}catch(IllegalArgumentException e){check(message.equals(e.getMessage()),"WRONG_"+message);}}
    private static void expectState(String message,Throwing task)throws Exception{try{task.run();throw new IllegalStateException("EXPECTED_"+message);}catch(IllegalStateException e){check(message.equals(e.getMessage()),"WRONG_"+message);}}
    private static void deleteTree(Path root)throws Exception{if(!Files.exists(root))return;try(var s=Files.walk(root)){for(Path p:s.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    @FunctionalInterface private interface Throwing{void run()throws Exception;}
}
