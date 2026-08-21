package com.huarenzaimeng.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseMigrationProcessTest {
    @Test void webPortListensAndHttpStaysClosedWhileMigrationRunnerBlocks() throws Exception {
        int port = availablePort();
        CountDownLatch applicationStarted = new CountDownLatch(1);
        CountDownLatch migrationStarted = new CountDownLatch(1);
        CountDownLatch allowMigrationToFinish = new CountDownLatch(1);
        TestApplication.latches(applicationStarted, migrationStarted, allowMigrationToFinish);

        CompletableFuture<ConfigurableApplicationContext> startup = CompletableFuture.supplyAsync(() -> {
            SpringApplication application = new SpringApplication(TestApplication.class);
            application.setAdditionalProfiles("release-mysql");
            application.addListeners(event -> {
                if (event instanceof ApplicationStartedEvent) applicationStarted.countDown();
            });
            return application.run("--server.port=" + port, "--logging.level.root=OFF");
        });

        ConfigurableApplicationContext context = null;
        try {
            awaitMigrationStarted(migrationStarted, startup, Duration.ofSeconds(20));
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
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            process.onExit().get(5, TimeUnit.SECONDS);
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
            deleteWhenUnlocked(output, Duration.ofSeconds(5));
        }
    }

    private static void awaitMigrationStarted(CountDownLatch migrationStarted,
                                              CompletableFuture<ConfigurableApplicationContext> startup,
                                              Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (migrationStarted.await(50, TimeUnit.MILLISECONDS)) return;
            if (startup.isDone()) {
                try {
                    ConfigurableApplicationContext unexpected = startup.join();
                    if (unexpected != null) unexpected.close();
                    throw new AssertionError("APPLICATION_COMPLETED_BEFORE_MIGRATION_RUNNER_STARTED");
                } catch (CompletionException failure) {
                    throw new AssertionError("APPLICATION_STARTUP_FAILED_BEFORE_MIGRATION_RUNNER_STARTED: "
                            + failure.getCause(), failure.getCause());
                }
            }
        }
        throw new AssertionError("MIGRATION_RUNNER_NOT_STARTED_WITHIN_TIMEOUT; startupDone=" + startup.isDone());
    }

    private static void deleteWhenUnlocked(Path output, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Exception last = null;
        do {
            try {
                Files.deleteIfExists(output);
                return;
            } catch (java.nio.file.FileSystemException locked) {
                last = locked;
                Thread.sleep(50);
            }
        } while (System.nanoTime() < deadline);
        throw last == null ? new IllegalStateException("OUTPUT_FILE_UNLOCK_TIMEOUT") : last;
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

    @Configuration(proxyBeanMethods = false)
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

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class })
    @Import(ReleaseMigrationGateFilter.class)
    static class TestApplication {
        private static volatile CountDownLatch started;
        private static volatile CountDownLatch applicationStarted;
        private static volatile CountDownLatch finish;

        static void latches(CountDownLatch bootStarted, CountDownLatch migrationStarted,
                            CountDownLatch allowMigrationToFinish) {
            applicationStarted = bootStarted;
            started = migrationStarted;
            finish = allowMigrationToFinish;
        }

        static void clearLatches() {
            applicationStarted = null;
            started = null;
            finish = null;
        }

        @Bean ReleaseMigrationState releaseMigrationState() {
            return new ReleaseMigrationState();
        }

        @Bean ApplicationRunner blockingMigrationRunner(ReleaseMigrationState state) {
            return args -> {
                if (applicationStarted.getCount() != 0) {
                    throw new IllegalStateException("APPLICATION_STARTED_EVENT_NOT_PUBLISHED_BEFORE_RUNNER");
                }
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
