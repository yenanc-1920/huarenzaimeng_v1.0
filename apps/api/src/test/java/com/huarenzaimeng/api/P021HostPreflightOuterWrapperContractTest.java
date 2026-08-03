package com.huarenzaimeng.api;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class P021HostPreflightOuterWrapperContractTest{
 @TempDir Path temp;
 @BeforeAll static void windowsOnly(){Assumptions.assumeTrue(System.getProperty("os.name","").startsWith("Windows"),"WINDOWS_HOST_TOOLCHAIN_NOT_PRESENT");}
 @Test void bindsTheProjectOfflineRepositoryWithoutUserHomeOrCentralFallback()throws Exception{P021HostPreflightOuterWrapper.validateOfflineRepository();List<String> command=new java.util.ArrayList<>(P021HostPreflightOuterWrapper.mavenLauncherPrefix(Path.of("").toAbsolutePath()));command.addAll(List.of("-o","-Dmaven.repo.local="+P021HostPreflightOuterWrapper.OFFLINE_REPOSITORY.toAbsolutePath().normalize(),"help:evaluate"));String joined=String.join("\n",command);assertThat(joined).contains("\n-o\n","-Dmaven.repo.local=E:\\workspace\\huarenzaimeng\\.m2-local\\repository").doesNotContain("user.home","repo1.maven.org","central");}
 @Test void fakeChildZeroReturnsAndPersistsOuterEvidence()throws Exception{Fixture f=fixture();Path report=f.repo.resolve(P021HostPreflightOuterWrapper.REPORT_ROOT).resolve(P021HostPreflightOuterWrapper.TEST_RUN_ID);
   int exit=run(f,0,report);assertThat(exit).isZero();assertThat(report.resolve("outer-process-evidence.json")).exists();assertThat(report.resolve("outer-process.stdout.txt")).content().contains("FAKE_MAVEN_STDOUT");assertThat(Files.exists(report.resolve("READY"))).isFalse();}
 @Test void fakeChildNonzeroReturnsAndBlocksWithoutReadyOrRetry()throws Exception{Fixture f=fixture();int exit=run(f,19,null);Path blocked=f.repo.resolve(P021HostPreflightOuterWrapper.REPORT_ROOT).resolve(".blocked-"+P021HostPreflightOuterWrapper.TEST_RUN_ID);assertThat(exit).isEqualTo(19);assertThat(blocked.resolve("BLOCKED.outer.json")).content().contains("\"Consumable\":false","\"AutomaticRetryAllowed\":false","\"OsExitCode\":19");assertThat(blocked.resolve("outer-process.stdout.txt")).exists();assertThat(Files.exists(blocked.resolve("READY"))).isFalse();assertThatThrownBy(()->run(f,19,null)).hasMessage("OUTER_RUN_NOT_FRESH");}
 @Test void rejectsMetacharactersQuotesPercentAndTraversal()throws Exception{Fixture f=fixture();for(String bad:List.of("%BAD.json","!BAD.json","\"BAD.json","A&B.json","A|B.json","A<B.json","A>B.json","A^B.json","../BAD.json"))assertThatThrownBy(()->P021HostPreflightOuterWrapper.run(new String[]{P021HostPreflightOuterWrapper.TEST_RUN_ID,bad,"A".repeat(64)},f.repo,List.of("never"),f.fakeShaFile,f.fakeSha)).isInstanceOf(IllegalArgumentException.class);}
 @Test void acceptsOnlyTheExactRealChineseAuthorizationDirectory()throws Exception{Fixture f=fixture();assertThat(P021HostPreflightOuterWrapper.validateAuthorizationLocation(f.repo,f.auth.toString())).isEqualTo(f.auth.toAbsolutePath().normalize());
   Path parent=f.repo.resolve("auth-parent.json");Files.writeString(parent,"{}");assertThatThrownBy(()->P021HostPreflightOuterWrapper.validateAuthorizationLocation(f.repo,parent.toString())).hasMessage("AUTH_RECORD_LOCATION");
   Path similar=f.repo.resolve(P021HostPreflightOuterWrapper.AUTH_ROOT+"-SIMILAR");Files.createDirectories(similar);Path similarFile=similar.resolve("auth.json");Files.writeString(similarFile,"{}");assertThatThrownBy(()->P021HostPreflightOuterWrapper.validateAuthorizationLocation(f.repo,similarFile.toString())).hasMessage("AUTH_RECORD_LOCATION");
   Path child=f.repo.resolve(P021HostPreflightOuterWrapper.AUTH_ROOT).resolve("child");Files.createDirectories(child);Path childFile=child.resolve("auth.json");Files.writeString(childFile,"{}");assertThatThrownBy(()->P021HostPreflightOuterWrapper.validateAuthorizationLocation(f.repo,childFile.toString())).hasMessage("AUTH_RECORD_LOCATION");
   assertThatThrownBy(()->P021HostPreflightOuterWrapper.validateAuthorizationLocation(f.repo,f.repo.resolve(P021HostPreflightOuterWrapper.AUTH_ROOT).resolve("child/../auth.json").toString())).hasMessage("AUTH_RECORD_CHARACTERS");}
 private int run(Fixture f,int exit,Path report)throws Exception{String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();List<String>prefix=new java.util.ArrayList<>(List.of(java,"-Dfake.exit="+exit));if(report!=null)prefix.add("-Dfake.report="+report);prefix.addAll(List.of("-cp",System.getProperty("java.class.path"),P021HostPreflightFakeMaven.class.getName()));return P021HostPreflightOuterWrapper.run(new String[]{P021HostPreflightOuterWrapper.TEST_RUN_ID,f.auth.toString(),f.authSha},f.repo,prefix,f.fakeShaFile,f.fakeSha);}
 private Fixture fixture()throws Exception{Path repo=Files.createDirectory(temp.resolve("r"+System.nanoTime()));Path root=repo.resolve(P021HostPreflightOuterWrapper.AUTH_ROOT);Files.createDirectories(root);Path auth=root.resolve("auth.json");Files.writeString(auth,"{}");Path fake=repo.resolve("fake.exe");Files.writeString(fake,"fake");return new Fixture(repo,auth,sha(auth),fake,sha(fake));}
 private static String sha(Path p)throws Exception{return java.util.HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
 record Fixture(Path repo,Path auth,String authSha,Path fakeShaFile,String fakeSha){}
}
