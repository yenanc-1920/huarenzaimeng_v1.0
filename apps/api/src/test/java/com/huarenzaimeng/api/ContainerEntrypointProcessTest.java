package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Executes the real POSIX entrypoint where a POSIX shell is available (CI/container gate). */
class ContainerEntrypointProcessTest {
    private static final Path ROOT=Path.of(System.getProperty("user.dir")).resolve("../..").normalize();

    @Test
    @EnabledOnOs(OS.LINUX)
    void invalidProfilesExit64AndEverySupportedPairExecsJava(@TempDir Path temp) throws Exception {
        Path shell=Path.of("/bin/sh");
        Path capture=temp.resolve("java.args"),optionsCapture=temp.resolve("java.options"),keytoolCapture=temp.resolve("keytool.args"),
                fakeJava=temp.resolve("java"),fakeKeytool=temp.resolve("keytool"),truststore=temp.resolve("cacerts"),
                runtimeTruststore=temp.resolve("runtime/cacerts"),runtimeCa=temp.resolve("runtime-ca"),systemCa=temp.resolve("system-ca");
        Files.writeString(fakeJava,"#!/bin/sh\nprintf '%s\\n' \"$*\" > \"$ENTRYPOINT_CAPTURE\"\nprintf '%s\\n' \"$JAVA_TOOL_OPTIONS\" > \"$ENTRYPOINT_OPTIONS_CAPTURE\"\n",StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(fakeJava,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE));
        Files.writeString(fakeKeytool,"#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"$ENTRYPOINT_KEYTOOL_CAPTURE\"\nexit 0\n",StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(fakeKeytool,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE));
        Files.createDirectories(runtimeCa);
        Files.createDirectories(systemCa);
        Files.writeString(runtimeCa.resolve("cloudbase-runtime.pem"),"runtime-ca",StandardCharsets.UTF_8);

        assertThat(run(shell,temp,capture,optionsCapture,keytoolCapture,"release-mysql,local-mysql",truststore,runtimeTruststore,runtimeCa,systemCa)).isEqualTo(78);
        Files.writeString(truststore,"fixed-truststore",StandardCharsets.UTF_8);

        assertThat(run(shell,temp,capture,optionsCapture,keytoolCapture,null,truststore,runtimeTruststore,runtimeCa,systemCa)).isEqualTo(64);
        assertThat(runtimeTruststore).doesNotExist();
        assertThat(run(shell,temp,capture,optionsCapture,keytoolCapture,"mock",truststore,runtimeTruststore,runtimeCa,systemCa)).isEqualTo(64);
        assertThat(run(shell,temp,capture,optionsCapture,keytoolCapture,"release-mysql,prod-mysql,mock",truststore,runtimeTruststore,runtimeCa,systemCa)).isEqualTo(64);
        for(String profile:new String[]{"release-mysql,local-mysql","release-mysql,test-mysql","release-mysql,stage-mysql","release-mysql,prod-mysql"}){
            Files.deleteIfExists(capture);
            Files.deleteIfExists(optionsCapture);
            Files.deleteIfExists(keytoolCapture);
            assertThat(run(shell,temp,capture,optionsCapture,keytoolCapture,profile,truststore,runtimeTruststore,runtimeCa,systemCa)).as(profile).isZero();
            assertThat(Files.readString(capture)).isEqualTo("-jar /app/app.jar\n");
            assertThat(Files.readString(optionsCapture)).contains("-Djavax.net.ssl.trustStore="+runtimeTruststore).contains("trustStorePassword=changeit");
            assertThat(runtimeTruststore).hasContent("fixed-truststore");
            assertThat(Files.readString(keytoolCapture)).contains("-importcert").contains(runtimeCa.resolve("cloudbase-runtime.pem").toString()).contains(runtimeTruststore.toString());
        }
    }

    private static int run(Path shell,Path fakeBin,Path capture,Path optionsCapture,Path keytoolCapture,String profile,Path truststore,
                           Path runtimeTruststore,Path runtimeCa,Path systemCa)throws IOException,InterruptedException{
        ProcessBuilder builder=new ProcessBuilder(shell.toString(),ROOT.resolve("tools/container-entrypoint.sh").toString());
        builder.environment().put("PATH",fakeBin+":"+builder.environment().getOrDefault("PATH","/usr/bin:/bin"));
        builder.environment().put("ENTRYPOINT_CAPTURE",capture.toString());
        builder.environment().put("ENTRYPOINT_OPTIONS_CAPTURE",optionsCapture.toString());
        builder.environment().put("ENTRYPOINT_KEYTOOL_CAPTURE",keytoolCapture.toString());
        builder.environment().put("HZ_JAVA_TRUSTSTORE_PATH",truststore.toString());
        builder.environment().put("HZ_JAVA_RUNTIME_TRUSTSTORE_PATH",runtimeTruststore.toString());
        builder.environment().put("HZ_RUNTIME_CA_DIRECTORY",runtimeCa.toString());
        builder.environment().put("HZ_SYSTEM_CA_DIRECTORY",systemCa.toString());
        if(profile==null)builder.environment().remove("SPRING_PROFILES_ACTIVE");else builder.environment().put("SPRING_PROFILES_ACTIVE",profile);
        builder.redirectErrorStream(true);
        Process process=builder.start();
        assertThat(process.waitFor(Duration.ofSeconds(5).toMillis(),TimeUnit.MILLISECONDS)).isTrue();
        return process.exitValue();
    }
}
