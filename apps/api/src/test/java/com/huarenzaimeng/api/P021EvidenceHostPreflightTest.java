package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class P021EvidenceHostPreflightTest {
    private static final String WRAPPER_SHA = "3E04E5D1130261728E517AEA5A71E1E5468032DB0DA3EDB8A34E7195DC644264";
    private static final String RUNNER_SHA = "07BDE8E82DE315172D7216515949D14316E26AE8F6E4E16E7CCC1B9BA69C4B35";
    private static final String RUNNER_AGGREGATE = "A55AEDA9018F4BB0C1FF9F0E06E7874B25568BF99D2D080830E82C1916B5ADEA";
    private static final Path WRAPPER = Path.of("Invoke-P021OrderDetailEvidenceFinalRun.ps1");
    private static final Path RUNNER = Path.of("src/test/java/com/huarenzaimeng/api/P021OrderDetailEvidenceFinalRunTest.java");
    private static final Path EVIDENCE_ROOT = Path.of("../../项目管理/正式交付/D4-开发计划与工程准备/证据/D5-ORD-03-P021后端技术证据").normalize();

    @Test
    void allowedHostLoadsAndParsesFrozenWrapperThenExitsBeforeFormalTest() throws Exception {
        String evidenceBefore = treeDigest(EVIDENCE_ROOT);
        assertThat(sha(WRAPPER)).isEqualTo(WRAPPER_SHA);
        assertThat(sha(RUNNER)).isEqualTo(RUNNER_SHA);
        String aggregateInput = "apps/api/Invoke-P021OrderDetailEvidenceFinalRun.ps1|" + sha(WRAPPER)
                + "\napps/api/src/test/java/com/huarenzaimeng/api/P021OrderDetailEvidenceFinalRunTest.java|" + sha(RUNNER);
        assertThat(digest(aggregateInput.getBytes(StandardCharsets.UTF_8))).isEqualTo(RUNNER_AGGREGATE);

        String wrapperPath = WRAPPER.toAbsolutePath().normalize().toString().replace("'", "''");
        String command = "$ErrorActionPreference='Stop';"
                + "$source=[IO.File]::ReadAllText('" + wrapperPath + "',[Text.UTF8Encoding]::new($false));"
                + "$tokens=$null;$errors=$null;"
                + "[System.Management.Automation.Language.Parser]::ParseInput($source,[ref]$tokens,[ref]$errors)|Out-Null;"
                + "if($errors.Count-ne 0){throw 'WRAPPER_PARSE_FAILED'};"
                + "if($source-notmatch 'Start-Process' -or $source-notmatch 'P021OrderDetailEvidenceFinalRunTest'){throw 'WRAPPER_ENTRYPOINT_MISSING'};"
                + "Write-Output 'WRAPPER_LOADED=true|WRAPPER_PARSED=true|FORMAL_TEST_STARTED=false|EVIDENCE_WRITTEN=false'";
        Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command)
                .redirectErrorStream(false).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exit = process.waitFor();

        assertThat(exit).as(stderr).isZero();
        assertThat(stdout).isEqualTo("WRAPPER_LOADED=true|WRAPPER_PARSED=true|FORMAL_TEST_STARTED=false|EVIDENCE_WRITTEN=false");
        assertThat(stderr).isEmpty();
        assertThat(treeDigest(EVIDENCE_ROOT)).isEqualTo(evidenceBefore);
    }

    private static String treeDigest(Path root) throws Exception {
        if (!Files.exists(root)) return "ABSENT";
        try (Stream<Path> stream = Files.walk(root)) {
            List<String> lines = stream.filter(Files::isRegularFile).map(path -> {
                try {
                    return root.relativize(path).toString().replace('\\', '/') + "|" + sha(path);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).sorted().toList();
            return digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String sha(Path path) throws Exception {
        return digest(Files.readAllBytes(path));
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
