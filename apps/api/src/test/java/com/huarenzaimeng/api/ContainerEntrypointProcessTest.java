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
        Path capture=temp.resolve("java.args"),fakeJava=temp.resolve("java"),fakeKeytool=temp.resolve("keytool"),truststore=temp.resolve("cacerts");
        Files.writeString(fakeJava,"#!/bin/sh\nprintf '%s\\n' \"$*\" > \"$ENTRYPOINT_CAPTURE\"\n",StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(fakeJava,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE));
        Files.writeString(fakeKeytool,"#!/bin/sh\nexit 0\n",StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(fakeKeytool,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE));

        assertThat(run(shell,temp,capture,"release-mysql,local-mysql",truststore)).isEqualTo(78);
        Files.writeString(truststore,"fixed-truststore",StandardCharsets.UTF_8);

        assertThat(run(shell,temp,capture,null,truststore)).isEqualTo(64);
        assertThat(run(shell,temp,capture,"mock",truststore)).isEqualTo(64);
        assertThat(run(shell,temp,capture,"release-mysql,prod-mysql,mock",truststore)).isEqualTo(64);
        for(String profile:new String[]{"release-mysql,local-mysql","release-mysql,test-mysql","release-mysql,stage-mysql","release-mysql,prod-mysql"}){
            Files.deleteIfExists(capture);
            assertThat(run(shell,temp,capture,profile,truststore)).as(profile).isZero();
            assertThat(Files.readString(capture)).isEqualTo("-jar /app/app.jar\n");
        }
    }

    private static int run(Path shell,Path fakeBin,Path capture,String profile,Path truststore)throws IOException,InterruptedException{
        ProcessBuilder builder=new ProcessBuilder(shell.toString(),ROOT.resolve("tools/container-entrypoint.sh").toString());
        builder.environment().put("PATH",fakeBin+":"+builder.environment().getOrDefault("PATH","/usr/bin:/bin"));
        builder.environment().put("ENTRYPOINT_CAPTURE",capture.toString());
        builder.environment().put("HZ_JAVA_TRUSTSTORE_PATH",truststore.toString());
        builder.environment().put("JAVA_TOOL_OPTIONS","-Djavax.net.ssl.trustStore="+truststore);
        if(profile==null)builder.environment().remove("SPRING_PROFILES_ACTIVE");else builder.environment().put("SPRING_PROFILES_ACTIVE",profile);
        builder.redirectErrorStream(true);
        Process process=builder.start();
        assertThat(process.waitFor(Duration.ofSeconds(5).toMillis(),TimeUnit.MILLISECONDS)).isTrue();
        return process.exitValue();
    }
}
