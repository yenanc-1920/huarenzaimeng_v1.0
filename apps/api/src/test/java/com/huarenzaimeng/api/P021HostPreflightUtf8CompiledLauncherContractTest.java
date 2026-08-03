package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class P021HostPreflightUtf8CompiledLauncherContractTest {
    @TempDir Path temp;
    private static final Path REPOSITORY_ROOT = repositoryRoot();

    @Test void explicitlyCompilesFrozenWrapperAsUtf8AndPreservesChineseAuthorizationRoot()throws Exception{
        Path classes=Files.createDirectories(temp.resolve("classes"));Path source=REPOSITORY_ROOT.resolve("apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightOuterWrapper.java"),policy=REPOSITORY_ROOT.resolve("apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightRunIdPolicy.java");
        Process p=new ProcessBuilder(P021HostPreflightUtf8CompiledLauncher.JAVAC.toString(),"-encoding","UTF-8","-d",classes.toString(),policy.toString(),source.toString()).redirectErrorStream(true).start();
        assertThat(new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8)).isEmpty();assertThat(p.waitFor()).isZero();
        try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},null)){
            Class<?> type=loader.loadClass("com.huarenzaimeng.api.P021HostPreflightOuterWrapper");var field=type.getDeclaredField("AUTH_ROOT");field.setAccessible(true);
            assertThat(field.get(null).toString()).isEqualTo("项目管理\\正式交付\\D4-开发计划与工程准备\\授权记录\\P021-HOST-PREFLIGHT");
        }
    }

    @Test void compileFailurePublishesOnlyBlockedAndNeverRunsWrapper()throws Exception{
        Path repo=Files.createDirectory(temp.resolve("bad-repo"));installPolicy(repo);Path source=repo.resolve("Broken.java");Files.writeString(source,"this is not java",StandardCharsets.UTF_8);
        String runId="P021-TECH-HOST-PREFLIGHT-TEST-BAD-COMPILE";int exit=P021HostPreflightUtf8CompiledLauncher.run(new String[]{runId,repo.resolve("auth.json").toString(),"A".repeat(64)},repo,
                P021HostPreflightUtf8CompiledLauncher.JAVAC,P021HostPreflightUtf8CompiledLauncher.JAVAC_SHA,P021HostPreflightUtf8CompiledLauncher.JAVA,P021HostPreflightUtf8CompiledLauncher.JAVA_SHA,
                Path.of("Broken.java"),sha(source),"Broken");
        Path root=repo.resolve(P021HostPreflightUtf8CompiledLauncher.REPORT_ROOT),blocked=root.resolve(".blocked-"+runId);
        assertThat(exit).isEqualTo(73);assertThat(blocked.resolve("BLOCKED.utf8-launcher.json")).content().contains("\"Consumable\":false","\"CompileEncoding\":\"UTF-8\"","\"ReadyCount\":0");
        assertThat(blocked.resolve("compile.stderr.txt")).isRegularFile();assertThat(Files.exists(root.resolve(runId))).isFalse();assertThat(Files.walk(root).noneMatch(p->p.getFileName().toString().startsWith("READY"))).isTrue();
    }

    @Test void compiledFakeExitZeroAndNonzeroKeepAtomicPassBlockedBoundary()throws Exception{
        Fixture ok=fixture("OK",0);int pass=run(ok);Path okRoot=ok.repo.resolve(P021HostPreflightUtf8CompiledLauncher.REPORT_ROOT);assertThat(pass).isZero();assertThat(okRoot.resolve(ok.runId).resolve("utf8-compile-evidence.json")).content().contains("\"ExecutionStatus\":\"PASS\"","\"ReadyCount\":0");
        Fixture bad=fixture("BAD",19);int fail=run(bad);Path badRoot=bad.repo.resolve(P021HostPreflightUtf8CompiledLauncher.REPORT_ROOT);assertThat(fail).isEqualTo(19);assertThat(badRoot.resolve(".blocked-"+bad.runId).resolve("BLOCKED.utf8-launcher.json")).content().contains("\"ExecutionStatus\":\"BLOCKED\"","\"WrapperExit\":19");assertThat(Files.exists(badRoot.resolve(bad.runId).resolve("READY"))).isFalse();
    }

    private int run(Fixture f)throws Exception{return P021HostPreflightUtf8CompiledLauncher.run(new String[]{f.runId,f.repo.resolve("auth.json").toString(),"A".repeat(64)},f.repo,
            P021HostPreflightUtf8CompiledLauncher.JAVAC,P021HostPreflightUtf8CompiledLauncher.JAVAC_SHA,P021HostPreflightUtf8CompiledLauncher.JAVA,P021HostPreflightUtf8CompiledLauncher.JAVA_SHA,
            Path.of("FakeWrapper.java"),sha(f.source),"FakeWrapper");}
    private Fixture fixture(String suffix,int exit)throws Exception{Path repo=Files.createDirectory(temp.resolve("repo-"+suffix));installPolicy(repo);String runId="P021-TECH-HOST-PREFLIGHT-TEST-"+suffix+"-0001";Path source=repo.resolve("FakeWrapper.java");String report="项目管理/正式交付/D4-开发计划与工程准备/证据/P021-HOST-PREFLIGHT/";
        Files.writeString(source,"import java.nio.file.*; public class FakeWrapper { public static void main(String[] a) throws Exception { Path r=Path.of(\""+report+"\").resolve(a[0]); Files.createDirectories(r); Files.writeString(r.resolve(\"preflight-result.json\"),\"{}\"); System.out.println(\"FAKE_UTF8_WRAPPER\"); System.exit("+exit+"); }}",StandardCharsets.UTF_8);return new Fixture(repo,source,runId);}
    private static String sha(Path path)throws Exception{return HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static void installPolicy(Path repository)throws Exception{Path target=repository.resolve(P021HostPreflightUtf8CompiledLauncher.POLICY_SOURCE);Files.createDirectories(target.getParent());Files.copy(REPOSITORY_ROOT.resolve("apps/api/src/test/java/com/huarenzaimeng/api/P021HostPreflightRunIdPolicy.java"),target);}
    private static Path repositoryRoot(){
        Path current=Path.of("").toAbsolutePath().normalize();
        while(current!=null){
            if(Files.isRegularFile(current.resolve("pom.xml"))&&Files.isRegularFile(current.resolve("apps/api/pom.xml")))return current;
            current=current.getParent();
        }
        throw new IllegalStateException("P021_REPOSITORY_ROOT_NOT_FOUND");
    }
    private record Fixture(Path repo,Path source,String runId){}
}
