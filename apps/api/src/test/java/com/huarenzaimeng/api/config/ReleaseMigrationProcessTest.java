package com.huarenzaimeng.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseMigrationProcessTest {
    @Test void webPortListensAndHttpStaysClosedWhileMigrationRunnerBlocks() throws Exception {
        int port = availablePort();
        CountDownLatch migrationStarted = new CountDownLatch(1);
        CountDownLatch allowMigrationToFinish = new CountDownLatch(1);
        TestApplication.latches(migrationStarted, allowMigrationToFinish);

        CompletableFuture<ConfigurableApplicationContext> startup = CompletableFuture.supplyAsync(() -> {
            SpringApplication application = new SpringApplication(TestApplication.class);
            application.setAdditionalProfiles("release-mysql");
            return application.run("--server.port=" + port, "--logging.level.root=OFF");
        });

        ConfigurableApplicationContext context = null;
        try {
            assertThat(migrationStarted.await(20, TimeUnit.SECONDS)).isTrue();
            assertTcpConnects(port, Duration.ofSeconds(5));

            HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/probe")
                    .toURL().openConnection();
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(2_000);
            assertThat(connection.getResponseCode()).isEqualTo(503);
            try (InputStream body = connection.getErrorStream()) {
                assertThat(new String(body.readAllBytes(), StandardCharsets.UTF_8))
                        .isEqualTo(ReleaseMigrationGateFilter.NOT_READY_BODY);
            }

            allowMigrationToFinish.countDown();
            context = startup.get(20, TimeUnit.SECONDS);
            assertThat(context.getBean(ReleaseMigrationState.class).isReady()).isTrue();

            HttpURLConnection ready = openProbe(port);
            assertThat(ready.getResponseCode()).isEqualTo(200);
            try (InputStream body = ready.getInputStream()) {
                assertThat(new String(body.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("CONSUMABLE");
            }
        } finally {
            allowMigrationToFinish.countDown();
            if (context != null) context.close();
            if (!startup.isDone()) startup.cancel(true);
            TestApplication.clearLatches();
        }
    }

    @Test void failedMigrationExitsChildJvmNonZeroAndClosesItsPortWithoutChildren() throws Exception {
        int port = availablePort();
        Path output = Files.createTempFile("release-migration-failure-", ".log");
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String testClasspath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(javaExecutable, "-cp", testClasspath,
                FailingProcessMain.class.getName(), Integer.toString(port))
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();

        try {
            assertTcpConnects(port, Duration.ofSeconds(20));
            assertThat(process.waitFor(20, TimeUnit.SECONDS))
                    .withFailMessage(() -> "child JVM did not exit; output=" + readForFailure(output))
                    .isTrue();
            assertThat(process.exitValue())
                    .withFailMessage(() -> "child JVM unexpectedly succeeded; output=" + readForFailure(output))
                    .isNotZero();
            assertTcpCloses(port, Duration.ofSeconds(5));
            assertThat(process.descendants().noneMatch(ProcessHandle::isAlive)).isTrue();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(output);
        }
    }

    private static int availablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void assertTcpConnects(int port, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
                return;
            } catch (Exception failure) {
                last = failure;
                Thread.sleep(50);
            }
        }
        throw last == null ? new IllegalStateException("TCP_LISTENER_NOT_READY") : last;
    }

    private static void assertTcpCloses(int port, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
                Thread.sleep(50);
            } catch (Exception expectedWhenClosed) {
                return;
            }
        }
        throw new IllegalStateException("TCP_LISTENER_STILL_OPEN");
    }

    private static HttpURLConnection openProbe(int port) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/probe")
                .toURL().openConnection();
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        return connection;
    }

    private static String readForFailure(Path output) {
        try {
            return Files.readString(output);
        } catch (Exception unreadable) {
            return "[unavailable]";
        }
    }

    public static final class FailingProcessMain {
        private FailingProcessMain() {}

        public static void main(String[] args) {
            SpringApplication application = new SpringApplication(FailingTestApplication.class);
            application.setAdditionalProfiles("release-mysql");
            application.run("--server.port=" + args[0], "--logging.level.root=OFF");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class })
    @Import(ReleaseMigrationGateFilter.class)
    static class FailingTestApplication {
        @Bean ReleaseMigrationState releaseMigrationState() {
            return new ReleaseMigrationState();
        }

        @Bean ApplicationRunner failingMigrationRunner(ReleaseMigrationState state) {
            return args -> {
                Thread.sleep(3_000);
                state.failed();
                throw new IllegalStateException("SYNTHETIC_FLYWAY_FAILURE");
            };
        }

        @Bean ProbeController probeController() {
            return new ProbeController();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class })
    @Import(ReleaseMigrationGateFilter.class)
    static class TestApplication {
        private static volatile CountDownLatch started;
        private static volatile CountDownLatch finish;

        static void latches(CountDownLatch migrationStarted, CountDownLatch allowMigrationToFinish) {
            started = migrationStarted;
            finish = allowMigrationToFinish;
        }

        static void clearLatches() {
            started = null;
            finish = null;
        }

        @Bean ReleaseMigrationState releaseMigrationState() {
            return new ReleaseMigrationState();
        }

        @Bean ApplicationRunner blockingMigrationRunner(ReleaseMigrationState state) {
            return args -> {
                started.countDown();
                if (!finish.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("TEST_MIGRATION_TIMEOUT");
                state.ready();
            };
        }

        @Bean ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe") String probe() {
            return "CONSUMABLE";
        }
    }
}
