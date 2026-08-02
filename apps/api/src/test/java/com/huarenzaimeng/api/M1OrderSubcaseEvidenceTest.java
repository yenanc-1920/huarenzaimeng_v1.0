package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huarenzaimeng.api.config.TestAccessTokenFilter;
import com.huarenzaimeng.core.MnpState;
import com.huarenzaimeng.core.Quote;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "hz.persistence.mode=in-memory",
        "hz.test-access-token=local-synthetic-order-creation-secret"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "m1.evidence.finalRun", matches = "true")
class M1OrderSubcaseEvidenceTest {
    private static final String TOKEN = "local-synthetic-order-creation-secret";
    private static final long SESSION_VERSION = 7L;
    private static final String AUTHORIZATION_SET_REF = "SYN-AS-M1-EVIDENCE";
    private static final String AUTHORIZATION_EVIDENCE_VERSION = "SYN-AUTH-EVIDENCE-M1";
    private static final String EVIDENCE_PACKAGE_ID = "D5-M1-BE-LOCAL-SYNTHETIC";
    private static final String REQUIRED_TEST_SELECTOR = "M1OrderSubcaseEvidenceTest";
    private static final String MATRIX_INPUT_SHA = "4673D29642AB461B62AB73EDCF828644234C2D1FBAFBF62C4729D226C45726C5";
    private static final String D1_SHA = "F2CA50A6C8EAD6463753E33B50A819EE51DA69117D7EDDCE9A54EC4A8F785538";
    private static final String D2_SHA = "2E9BC7D6F9075018CF5DB447571FD8FA9E23DA2E237EC0EEF13AA6D64628AA73";
    private static final String D3_REGISTRY_SHA = "358CBFCC3DEAEE618C8F478603367FA3001B84BE63AAF11ED9CCB9B3EA5BDB8C";
    private static final String D3_03_SHA = "2C3CE8BB4A872953CFAFAAEB8AC4983839BAD21A162FF6B40B09F6A20CCB6B5A";
    private static final String D3_05_SHA = "BA21D617C9FBB96F5CBC7628ACFE53312B51735B5894D813C4725B7F12FB09A0";
    private static final String EXPECTED_IMPLEMENTATION_AGGREGATE_SHA =
            "DF0759508BD7E3588277BDCE7773A983C3389DBB0F17B602F0625BDC78058658";
    private static final String IMPLEMENTATION_AGGREGATE_ALGORITHM =
            "fixed 15-path manifest order; path|UPPERCASE_SHA256; UTF-8 no BOM; LF between; no terminal LF";
    private static final String GENERATOR_PATH =
            "apps/api/src/test/java/com/huarenzaimeng/api/M1OrderSubcaseEvidenceTest.java";
    private static final String WRAPPER_PATH = "apps/api/Invoke-M1OrderEvidenceFinalRun.ps1";
    private static final Map<String, String> FIXED_INPUT_FILES = Map.of(
            "项目管理/正式交付/D1-产品规划与需求/D1产品基线版本清单.md", D1_SHA,
            "项目管理/正式交付/D2-体验与UI设计/D2体验与UI设计版本清单.md", D2_SHA,
            "项目管理/正式交付/D3-技术实现基线/D3技术基线版本清单.md", D3_REGISTRY_SHA,
            "项目管理/正式交付/D3-技术实现基线/D3-03-领域状态与项目API协议.md", D3_03_SHA,
            "项目管理/正式交付/D3-技术实现基线/D3-05-安全可靠性与开发门禁.md", D3_05_SHA,
            "项目管理/正式交付/D4-开发计划与工程准备/D5-M1报价后稳定建单本地质量矩阵.md",
            MATRIX_INPUT_SHA);
    private static final List<String> IMPLEMENTATION_PATHS = List.of(
            "apps/api/src/main/java/com/huarenzaimeng/api/FlowMapper.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/FlowStore.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/InMemoryFlowStore.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/LocalSyntheticOrderRecoveryService.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/MockFlowController.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/MockFlowService.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/MyBatisFlowStore.java",
            "apps/api/src/main/java/com/huarenzaimeng/api/OrderCreationDomain.java",
            "apps/api/src/main/resources/db/migration/V5__tighten_order_command_idempotency_scope.sql",
            "apps/api/src/main/resources/db/migration/V5_ORDER_COMMAND_ALIAS_FORWARD_RUNBOOK.md",
            "apps/api/src/test/java/com/huarenzaimeng/api/MockFlowApiContractTest.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/MockFlowServiceTest.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/OrderCreationApiContractTest.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/OrderCreationMigrationContractTest.java",
            "apps/api/src/test/java/com/huarenzaimeng/api/OrderRecoveryApiContractTest.java");
    private static final List<String> STATUS_DENOMINATOR =
            List.of("PASS", "FAIL", "BLOCKED", "SKIPPED", "NA", "NOT_RUN");
    private static final List<String> PROHIBITED_PAYLOAD_MARKERS = List.of(
            TOKEN, "client_secret", "access_token", "refresh_token", "\"openid\"", "\"unionid\"",
            "\"phoneNumber\"", "\"mobileNumber\"");
    private static final Map<String, SubcaseDefinition> SUBCASES = subcases();
    private static final List<String> FIXED_SUBCASE_IDS = List.of(
            "M1-ORD-002-RECEIVER-REPLAY", "M1-ORD-005-EXPIRED-QUOTE",
            "M1-ORD-006-LEGACY-V4-NULL", "M1-ORD-007-SUPPORT-VERSION-DRIFT",
            "M1-ORD-008-CATALOG-VERSION-DRIFT", "M1-ORD-009-CROSS-SUBJECT",
            "M1-ORD-010-MISSING-QUOTEREF", "M1-ORD-011-MISSING-COMMANDID",
            "M1-ORD-012-MISSING-IDEMPOTENCYKEY", "M1-ORD-013-INVALID-CREATION-PRECONDITION",
            "M1-ORD-014-MISSING-SNAPSHOT-FIELD", "M1-ORD-014A-MISSING-SESSIONVERSION",
            "M1-ORD-014B-MISSING-AUTHSETREF", "M1-ORD-014C-STALE-SESSIONVERSION",
            "M1-ORD-014D-WRONG-AUTHSETREF", "M1-ORD-014E-REVOKED-OR-EVIDENCE-MISMATCH",
            "M1-ORD-014F-A-NONLOCAL-PROFILE", "M1-ORD-014F-B-RELEASE-HIT",
            "M1-ORD-014F-C-NO-TEST-TOKEN", "M1-ORD-015-CLIENT-AMOUNT-TAMPER",
            "M1-ORD-016-CLIENT-CURRENCY-TAMPER", "M1-ORD-017-CLIENT-PRODUCT-TAMPER",
            "M1-ORD-018-CLIENT-DENOMINATION-TAMPER");
    private static String implementationAggregateSha;
    private static String generatorSha;
    private static String executionRunId;
    private static String executedAt;
    private static Path stagingDirectory;
    private static Path evidenceDirectory;
    private static boolean finalRunEnabled;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MockFlowService service;
    @Autowired LocalSyntheticOrderRecoveryService recovery;
    @Autowired InMemoryFlowStore store;
    @Autowired InMemoryCatalogStore catalog;
    @Autowired Clock clock;

    private LocalSyntheticIdentity identity;

    @BeforeAll
    static void prepareEvidenceRun() throws Exception {
        finalRunEnabled = "true".equals(System.getProperty("m1.evidence.finalRun"));
        Assumptions.assumeTrue(finalRunEnabled,
                "M1 evidence generator is disabled; use the approved one-shot wrapper only");

        Path root = repositoryRoot();
        executionRunId = requireProperty("m1.evidence.executionRunId");
        assertThat(executionRunId).matches("^M1-BE-[A-Za-z0-9][A-Za-z0-9._-]{7,80}$");
        assertThat(requireProperty("m1.evidence.testSelector")).isEqualTo(REQUIRED_TEST_SELECTOR);

        implementationAggregateSha = implementationAggregate();
        generatorSha = sha256(Files.readAllBytes(root.resolve(GENERATOR_PATH)));
        assertThat(generatorSha).as("externally authorized generator SHA")
                .isEqualTo(requireProperty("m1.evidence.expectedGeneratorSha256"));
        assertThat(implementationAggregateSha).as("fixed M1 15-file implementation aggregate")
                .isEqualTo(EXPECTED_IMPLEMENTATION_AGGREGATE_SHA);
        verifyFixedInputs(root);
        verifyFixedSubcaseIdentity();

        Path finalDirectory = root.resolve("apps/api/target/m1-subcase-evidence/runs").resolve(executionRunId);
        assertThat(finalDirectory).as("formal RunId directory must not already exist").doesNotExist();
        Path authorizationFile = Path.of(requireProperty("m1.evidence.authorizationFile"))
                .toAbsolutePath().normalize();
        assertThat(authorizationFile).isRegularFile();
        assertThat(sha256(Files.readAllBytes(authorizationFile)))
                .isEqualTo(requireProperty("m1.evidence.authorizationFileSha256"));
        verifyConsumedWrapperAuthorization(authorizationFile, root);

        stagingDirectory = Path.of(requireProperty("m1.evidence.stagingDirectory"))
                .toAbsolutePath().normalize();
        Path allowedStagingRoot = root.resolve("apps/api/target/m1-subcase-evidence/staging")
                .toAbsolutePath().normalize();
        assertThat(stagingDirectory.startsWith(allowedStagingRoot)).isTrue();
        evidenceDirectory = stagingDirectory.resolve("cases");
        assertThat(evidenceDirectory).as("case staging directory must be new").doesNotExist();
        executedAt = Instant.now().toString();
        Files.createDirectories(evidenceDirectory);
    }

    @AfterAll
    static void finalizeGeneratorValidation() throws Exception {
        if (!finalRunEnabled || evidenceDirectory == null || !Files.isDirectory(evidenceDirectory)) return;
        ObjectMapper mapper = new ObjectMapper();
        List<Path> caseFiles;
        try (var stream = Files.list(evidenceDirectory)) {
            caseFiles = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        ObjectNode validation = mapper.createObjectNode();
        appendFixedIdentity(validation);
        validation.put("candidateConsumable", false);
        validation.put("readyWritten", false);
        validation.put("expectedStableEvidenceCount", 23);
        validation.put("actualEvidenceFileCount", caseFiles.size());
        validation.put("implementationAggregateAlgorithm", IMPLEMENTATION_AGGREGATE_ALGORITHM);
        ArrayNode implementationManifest = validation.putArray("implementationManifest");
        for (String path : IMPLEMENTATION_PATHS) {
            ObjectNode item = implementationManifest.addObject();
            item.put("path", path);
            item.put("sha256", sha256(Files.readAllBytes(repositoryRoot().resolve(path))));
        }

        ArrayNode stableEvidence = validation.putArray("stableEvidenceItems");
        Map<String, String> statuses = new LinkedHashMap<>();
        Set<String> expectedFileNames = new java.util.HashSet<>();
        boolean caseSchemaAndAssertionBindingValid = true;
        for (Map.Entry<String, SubcaseDefinition> entry : SUBCASES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            String subcaseId = entry.getKey();
            SubcaseDefinition definition = entry.getValue();
            ObjectNode row = stableEvidence.addObject();
            row.put("scenarioId", definition.scenarioId());
            row.put("matrixSubcaseId", definition.matrixSubcaseId());
            row.put("subcaseId", subcaseId);
            row.put("parameterId", definition.parameterId());
            Path caseFile = evidenceDirectory.resolve(evidenceFileName(subcaseId, definition.parameterId()));
            expectedFileNames.add(caseFile.getFileName().toString());
            JsonNode caseEvidence = Files.exists(caseFile)
                    ? mapper.readTree(Files.readString(caseFile, StandardCharsets.UTF_8)) : null;
            String status = caseEvidence == null ? "NOT_RUN"
                    : caseEvidence.path("executionStatus").asText("FAIL");
            if (caseEvidence != null && !caseEvidenceValid(caseEvidence, subcaseId, definition)) {
                caseSchemaAndAssertionBindingValid = false;
                status = "FAIL";
            }
            row.put("executionStatus", status);
            if (Files.exists(caseFile)) row.put("evidenceFile", caseFile.getFileName().toString());
            statuses.put(subcaseId, status);
        }

        ArrayNode matrixCoverage = validation.putArray("matrixCoverage");
        SUBCASES.values().stream().map(SubcaseDefinition::matrixSubcaseId).distinct().sorted()
                .forEach(matrixSubcaseId -> {
            ObjectNode row = matrixCoverage.addObject();
            row.put("matrixSubcaseId", matrixSubcaseId);
            List<String> childStatuses = SUBCASES.entrySet().stream()
                    .filter(entry -> matrixSubcaseId.equals(entry.getValue().matrixSubcaseId()))
                    .map(entry -> statuses.get(entry.getKey())).toList();
            row.put("executionStatus", childStatuses.stream().allMatch("PASS"::equals) ? "PASS" :
                    childStatuses.stream().allMatch("NOT_RUN"::equals) ? "NOT_RUN" : "FAIL");
        });

        ObjectNode statusCounts = validation.putObject("statusCounts");
        long statusTotal = 0;
        for (String status : STATUS_DENOMINATOR) {
            long count = statuses.values().stream().filter(status::equals).count();
            statusCounts.put(status, count);
            statusTotal += count;
        }
        validation.put("statusTotal", statusTotal);

        ArrayNode evidenceFiles = validation.putArray("evidenceFiles");
        for (Path file : caseFiles) {
            ObjectNode item = evidenceFiles.addObject();
            item.put("path", file.getFileName().toString());
            item.put("sha256", sha256(Files.readAllBytes(file)));
        }

        ArrayNode prohibitedScan = validation.putArray("prohibitedPayloadScan");
        long prohibitedHits = 0;
        for (String marker : PROHIBITED_PAYLOAD_MARKERS) {
            long hits = 0;
            for (Path file : caseFiles) {
                hits += countOccurrences(Files.readString(file, StandardCharsets.UTF_8), marker);
            }
            ObjectNode rule = prohibitedScan.addObject();
            rule.put("markerSha256", sha256(marker.getBytes(StandardCharsets.UTF_8)));
            rule.put("hitCount", hits);
            prohibitedHits += hits;
        }
        validation.put("prohibitedPayloadHitCount", prohibitedHits);
        String canary = "M1-SCAN-CANARY-" + UUID.randomUUID();
        boolean canarySensitive = countOccurrences("prefix|" + canary + "|suffix", canary) == 1;
        validation.put("prohibitedScanCanarySensitive", canarySensitive);
        validation.put("prohibitedScanCanarySha256", sha256(canary.getBytes(StandardCharsets.UTF_8)));

        ObjectNode notRun = validation.putObject("notRunBoundaries");
        notRun.put("realMySql", "NOT_RUN");
        notRun.put("crossProcessConcurrency", "NOT_RUN");
        notRun.put("externalNetwork", "NOT_RUN");
        notRun.put("wechat", "NOT_RUN");
        notRun.put("realIdentity", "NOT_RUN");
        notRun.put("realFunds", "NOT_RUN");
        Set<String> actualFileNames = caseFiles.stream().map(path -> path.getFileName().toString())
                .collect(java.util.stream.Collectors.toSet());
        boolean exactFiles = caseFiles.size() == 23 && actualFileNames.equals(expectedFileNames);
        boolean allPass = statuses.size() == 23 && statuses.values().stream().allMatch("PASS"::equals);
        boolean statusInvariant = statusTotal == 23 && statuses.values().stream()
                .allMatch(STATUS_DENOMINATOR::contains);
        boolean generatorPass = exactFiles && allPass && statusInvariant
                && caseSchemaAndAssertionBindingValid && prohibitedHits == 0 && canarySensitive;
        validation.put("exact23NoExtras", exactFiles);
        validation.put("allCasesPass", allPass);
        validation.put("sixStateTotalEquals23", statusInvariant);
        validation.put("caseSchemaAndAssertionBindingValid", caseSchemaAndAssertionBindingValid);
        validation.put("executionStatus", generatorPass ? "PASS" : "BLOCKED");
        writeJsonAtomically(mapper, stagingDirectory.resolve("generator-validation.json"), validation);
        assertThat(generatorPass).as("generator handoff gate for outer wrapper").isTrue();
    }

    @BeforeEach
    void prepareIndependentFixture() {
        identity = LocalSyntheticIdentity.fromToken(TOKEN);
        store.resetSubjectForTest(identity.projectSubjectRef());
        catalog.resetForTest();
        recovery.resetForTest();
        recovery.installBuyerAuthorizationForTest(identity.projectSubjectRef(), identity.sessionRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF, AUTHORIZATION_EVIDENCE_VERSION);
    }

    @Test void M1_ORD_002_RECEIVER_REPLAY() throws Exception {
        executeSubcase("M1-ORD-002-RECEIVER-REPLAY", Variant.RECEIVER_REPLAY);
    }

    @Test void M1_ORD_005_EXPIRED_QUOTE() throws Exception {
        executeSubcase("M1-ORD-005-EXPIRED-QUOTE", Variant.EXPIRED_QUOTE);
    }

    @Test void M1_ORD_006_LEGACY_V4_NULL() throws Exception {
        executeSubcase("M1-ORD-006-LEGACY-V4-NULL", Variant.LEGACY_V4_NULL);
    }

    @Test void M1_ORD_007_SUPPORT_VERSION_DRIFT() throws Exception {
        executeSubcase("M1-ORD-007-SUPPORT-VERSION-DRIFT", Variant.SUPPORT_VERSION_DRIFT);
    }

    @Test void M1_ORD_008_CATALOG_VERSION_DRIFT() throws Exception {
        executeSubcase("M1-ORD-008-CATALOG-VERSION-DRIFT", Variant.CATALOG_VERSION_DRIFT);
    }

    @Test void M1_ORD_009_CROSS_SUBJECT() throws Exception {
        executeSubcase("M1-ORD-009-CROSS-SUBJECT", Variant.CROSS_SUBJECT);
    }

    @Test void M1_ORD_010_MISSING_QUOTEREF() throws Exception {
        executeSubcase("M1-ORD-010-MISSING-QUOTEREF", Variant.MISSING_QUOTE_REF);
    }

    @Test void M1_ORD_011_MISSING_COMMANDID() throws Exception {
        executeSubcase("M1-ORD-011-MISSING-COMMANDID", Variant.MISSING_COMMAND_ID);
    }

    @Test void M1_ORD_012_MISSING_IDEMPOTENCYKEY() throws Exception {
        executeSubcase("M1-ORD-012-MISSING-IDEMPOTENCYKEY", Variant.MISSING_IDEMPOTENCY_KEY);
    }

    @Test void M1_ORD_013_INVALID_CREATION_PRECONDITION() throws Exception {
        executeSubcase("M1-ORD-013-INVALID-CREATION-PRECONDITION", Variant.INVALID_CREATION_PRECONDITION);
    }

    @Test void M1_ORD_014_MISSING_SNAPSHOT_FIELD() throws Exception {
        executeSubcase("M1-ORD-014-MISSING-SNAPSHOT-FIELD", Variant.MISSING_SNAPSHOT_FIELD);
    }

    @Test void M1_ORD_014A_MISSING_SESSIONVERSION() throws Exception {
        executeSubcase("M1-ORD-014A-MISSING-SESSIONVERSION", Variant.MISSING_SESSION_VERSION);
    }

    @Test void M1_ORD_014B_MISSING_AUTHSETREF() throws Exception {
        executeSubcase("M1-ORD-014B-MISSING-AUTHSETREF", Variant.MISSING_AUTHORIZATION_SET_REF);
    }

    @Test void M1_ORD_014C_STALE_SESSIONVERSION() throws Exception {
        executeSubcase("M1-ORD-014C-STALE-SESSIONVERSION", Variant.STALE_SESSION_VERSION);
    }

    @Test void M1_ORD_014D_WRONG_AUTHSETREF() throws Exception {
        executeSubcase("M1-ORD-014D-WRONG-AUTHSETREF", Variant.WRONG_AUTHORIZATION_SET_REF);
    }

    @Test void M1_ORD_014E_REVOKED_OR_EVIDENCE_MISMATCH() throws Exception {
        executeSubcase("M1-ORD-014E-REVOKED-OR-EVIDENCE-MISMATCH", Variant.REVOKED_OR_EVIDENCE_MISMATCH);
    }

    @Test void M1_ORD_014F_A_NONLOCAL_PROFILE() throws Exception {
        executeQualificationSubcase("M1-ORD-014F-A-NONLOCAL-PROFILE", "production", "in-memory");
    }

    @Test void M1_ORD_014F_B_RELEASE_HIT() throws Exception {
        executeQualificationSubcase("M1-ORD-014F-B-RELEASE-HIT", "release-mysql", "mysql");
    }

    @Test void M1_ORD_014F_C_NO_TEST_TOKEN() throws Exception {
        executeSubcase("M1-ORD-014F-C-NO-TEST-TOKEN", Variant.NO_TEST_TOKEN);
    }

    @Test void M1_ORD_015_CLIENT_AMOUNT_TAMPER() throws Exception {
        executeSubcase("M1-ORD-015-CLIENT-AMOUNT-TAMPER", Variant.CLIENT_AMOUNT_TAMPER);
    }

    @Test void M1_ORD_016_CLIENT_CURRENCY_TAMPER() throws Exception {
        executeSubcase("M1-ORD-016-CLIENT-CURRENCY-TAMPER", Variant.CLIENT_CURRENCY_TAMPER);
    }

    @Test void M1_ORD_017_CLIENT_PRODUCT_TAMPER() throws Exception {
        executeSubcase("M1-ORD-017-CLIENT-PRODUCT-TAMPER", Variant.CLIENT_PRODUCT_TAMPER);
    }

    @Test void M1_ORD_018_CLIENT_DENOMINATION_TAMPER() throws Exception {
        executeSubcase("M1-ORD-018-CLIENT-DENOMINATION-TAMPER", Variant.CLIENT_DENOMINATION_TAMPER);
    }

    private void executeSubcase(String subcaseId, Variant variant)
            throws Exception {
        Quote quote = quoteFor(identity.projectSubjectRef());
        ObjectNode request = validRequest(subcaseId, quote.quoteRef());
        boolean includeTrustedToken = true;
        int expectedHttpStatus = 422;
        String expectedProjectCode = "ORDER_CREATION_NOT_AVAILABLE";
        String expectedOrderRef = null;

        switch (variant) {
            case RECEIVER_REPLAY -> {
                MvcResult first = perform(request, true);
                assertThat(first.getResponse().getStatus()).isEqualTo(200);
                JsonNode firstResponse = json.readTree(first.getResponse().getContentAsString());
                assertThat(firstResponse.path("projectCode").asText()).isEqualTo("ORDER_CREATED");
                expectedOrderRef = firstResponse.path("resourceRef").asText();
                expectedHttpStatus = 200;
                expectedProjectCode = "ORDER_REPLAYED";
            }
            case EXPIRED_QUOTE -> store.installQuoteForTest(identity.projectSubjectRef(), copyQuote(quote,
                    quote.denominationRef(), quote.supportedOperatorSetVersion(), quote.catalogVersion(),
                    quote.currency(), clock.instant().minusSeconds(1)));
            case LEGACY_V4_NULL -> {
                Quote legacy = new Quote("Q-M1-EVIDENCE-LEGACY-" + UUID.randomUUID(), quote.maskedPhone(),
                        quote.operatorCode(), quote.productCode(), null, 0L, 0L, quote.totalAmountMinor(),
                        quote.currency(), clock.instant().plusSeconds(600));
                store.installQuoteForTest(identity.projectSubjectRef(), legacy);
                request.put("quoteRef", legacy.quoteRef());
            }
            case SUPPORT_VERSION_DRIFT -> installCatalog(2L, 1L);
            case CATALOG_VERSION_DRIFT -> installCatalog(1L, 2L);
            case CROSS_SUBJECT -> request.put("quoteRef", quoteFor("SYN-SUBJECT-M1-EVIDENCE-FOREIGN").quoteRef());
            case MISSING_QUOTE_REF -> {
                request.remove("quoteRef");
                expectedHttpStatus = 400;
                expectedProjectCode = "INVALID_REQUEST";
            }
            case MISSING_COMMAND_ID -> {
                request.remove("commandId");
                expectedHttpStatus = 400;
                expectedProjectCode = "INVALID_REQUEST";
            }
            case MISSING_IDEMPOTENCY_KEY -> {
                request.remove("idempotencyKey");
                expectedHttpStatus = 400;
                expectedProjectCode = "INVALID_REQUEST";
            }
            case INVALID_CREATION_PRECONDITION -> {
                request.put("orderCreationPrecondition", "WRONG_PRECONDITION");
                expectedProjectCode = "ORDER_CREATION_PRECONDITION_INVALID";
            }
            case MISSING_SNAPSHOT_FIELD -> store.installQuoteForTest(identity.projectSubjectRef(), copyQuote(quote,
                    quote.denominationRef(), quote.supportedOperatorSetVersion(), quote.catalogVersion(),
                    null, quote.expiresAt()));
            case MISSING_SESSION_VERSION -> {
                request.remove("sessionVersion");
                expectedHttpStatus = 400;
                expectedProjectCode = "INVALID_REQUEST";
            }
            case MISSING_AUTHORIZATION_SET_REF -> {
                request.remove("authorizationSetRef");
                expectedHttpStatus = 400;
                expectedProjectCode = "INVALID_REQUEST";
            }
            case STALE_SESSION_VERSION -> request.put("sessionVersion", SESSION_VERSION - 1);
            case WRONG_AUTHORIZATION_SET_REF -> request.put("authorizationSetRef", "SYN-AS-M1-WRONG");
            case REVOKED_OR_EVIDENCE_MISMATCH -> recovery.installBuyerAuthorizationForTest(
                    identity.projectSubjectRef(), identity.sessionRef(), SESSION_VERSION,
                    AUTHORIZATION_SET_REF, "");
            case NO_TEST_TOKEN -> {
                includeTrustedToken = false;
                expectedHttpStatus = 401;
                expectedProjectCode = "UNAUTHORIZED";
            }
            case CLIENT_AMOUNT_TAMPER -> {
                request.put("amountMinor", 1);
                expectedHttpStatus = 400;
                expectedProjectCode = "INVALID_REQUEST";
            }
            case CLIENT_CURRENCY_TAMPER -> {
                request.put("currency", "USD");
                expectedHttpStatus = 400;
                expectedProjectCode = "INVALID_REQUEST";
            }
            case CLIENT_PRODUCT_TAMPER -> {
                request.put("productRef", "FORGED-PRODUCT");
                expectedHttpStatus = 400;
                expectedProjectCode = "INVALID_REQUEST";
            }
            case CLIENT_DENOMINATION_TAMPER -> {
                request.put("denominationRef", "FORGED-DENOMINATION");
                expectedHttpStatus = 400;
                expectedProjectCode = "INVALID_REQUEST";
            }
        }

        CountSnapshot before = counts();
        MvcResult result = perform(request, includeTrustedToken);
        CountSnapshot after = counts();
        String responseBody = result.getResponse().getContentAsString();
        JsonNode response = json.readTree(responseBody);

        assertThat(result.getResponse().getStatus()).as(subcaseId + " httpStatus")
                .isEqualTo(expectedHttpStatus);
        String actualCode = response.has("projectCode")
                ? response.path("projectCode").asText() : response.path("status").asText();
        assertThat(actualCode).as(subcaseId + " responseCode").isEqualTo(expectedProjectCode);
        if (variant == Variant.RECEIVER_REPLAY) {
            assertThat(response.path("resourceRef").asText()).isEqualTo(expectedOrderRef);
            assertThat(response.path("currentProjection").path("orderRef").asText()).isEqualTo(expectedOrderRef);
            assertThat(response.path("aggregateVersion").asLong()).isEqualTo(1L);
        } else if (expectedHttpStatus != 401) {
            assertThat(response.hasNonNull("data")).as(subcaseId + " response data").isFalse();
        }
        assertNoWriteDelta(subcaseId, before, after);
        writeEvidence(subcaseId, variant, request, expectedHttpStatus, expectedProjectCode, expectedOrderRef,
                result.getResponse().getStatus(), actualCode, response, before, after);
    }

    private void executeQualificationSubcase(String subcaseId, String activeProfile, String persistenceMode)
            throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);
        LocalSyntheticOrderRecoveryService isolated = new LocalSyntheticOrderRecoveryService(
                clock, environment, persistenceMode);
        CountSnapshot before = counts();

        Throwable rejection = catchThrowable(() -> isolated.requireBuyerAuthorization(
                LocalSyntheticOrderRecoveryService.ENVIRONMENT, identity.projectSubjectRef(), identity.sessionRef(),
                SESSION_VERSION, AUTHORIZATION_SET_REF));
        CountSnapshot after = counts();

        assertThat(rejection).as(subcaseId + " qualification rejection")
                .isInstanceOf(FlowRejectedException.class)
                .hasMessage("LOCAL_SYNTHETIC_IDENTITY_REQUIRED");
        assertNoWriteDelta(subcaseId, before, after);

        ObjectNode response = json.createObjectNode();
        response.put("status", "REJECTED");
        response.put("projectCode", "LOCAL_SYNTHETIC_IDENTITY_REQUIRED");
        response.putNull("data");
        writeQualificationEvidence(subcaseId, activeProfile, persistenceMode,
                "LOCAL_SYNTHETIC_IDENTITY_REQUIRED", rejection, response, before, after);
    }

    private MvcResult perform(ObjectNode request, boolean includeTrustedToken) throws Exception {
        var builder = post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request));
        if (includeTrustedToken) builder.header(TestAccessTokenFilter.HEADER_NAME, TOKEN);
        return mvc.perform(builder).andReturn();
    }

    private Quote quoteFor(String subject) {
        String suffix = UUID.randomUUID().toString();
        return service.createQuote(subject, "8801700000000", "SYN-OP", "SYN-PRODUCT", "SYN-DENOM-1000",
                1L, 1L, "CMD-M1-EVIDENCE-QUOTE-" + suffix,
                "IDEM-M1-EVIDENCE-QUOTE-" + suffix, MnpState.CONFIRMED);
    }

    private ObjectNode validRequest(String subcaseId, String quoteRef) {
        String key = subcaseId.replaceAll("[^A-Z0-9]", "-");
        ObjectNode request = json.createObjectNode();
        request.put("commandId", "CMD-" + key);
        request.put("idempotencyKey", "IDEM-" + key);
        request.put("orderCreationPrecondition", MockFlowService.ORDER_CREATION_PRECONDITION);
        request.put("quoteRef", quoteRef);
        request.put("sessionVersion", SESSION_VERSION);
        request.put("authorizationSetRef", AUTHORIZATION_SET_REF);
        return request;
    }

    private Quote copyQuote(Quote quote, String denominationRef, long supportVersion, long catalogVersion,
                            String currency, Instant expiresAt) {
        return new Quote(quote.quoteRef(), quote.maskedPhone(), quote.operatorCode(), quote.productCode(),
                denominationRef, supportVersion, catalogVersion, quote.totalAmountMinor(), currency, expiresAt);
    }

    private void installCatalog(long supportVersion, long catalogVersion) {
        Instant now = clock.instant();
        catalog.installForTest(new CatalogBatch(supportVersion, catalogVersion, "SYN-M1-EVIDENCE-APPROVAL",
                now.minusSeconds(60), now.plusSeconds(3600), true, List.of("SYN-OP"),
                List.of(new CatalogItem("SYN-OP", "SYN-PRODUCT", "SYN-DENOM-1000",
                        "PRESET_DENOMINATION", 1000L, "CNY"))));
    }

    private CountSnapshot counts() {
        return new CountSnapshot(store.orderCountForTest(identity.projectSubjectRef()),
                store.orderBusinessKeyCountForTest(identity.projectSubjectRef()),
                store.sideEffectSnapshot(identity.projectSubjectRef()));
    }

    private static void assertNoWriteDelta(String subcaseId, CountSnapshot before, CountSnapshot after) {
        assertThat(after.orders() - before.orders()).as(subcaseId + " Order delta").isZero();
        assertThat(after.orderBusinessKeys() - before.orderBusinessKeys())
                .as(subcaseId + " OrderBusinessKey delta").isZero();
        assertThat(after.effects().paymentIntents() - before.effects().paymentIntents())
                .as(subcaseId + " PaymentIntent delta").isZero();
        assertThat(after.effects().paymentAttempts() - before.effects().paymentAttempts())
                .as(subcaseId + " PaymentAttempt delta").isZero();
        assertThat(after.effects().dispatchIntents() - before.effects().dispatchIntents())
                .as(subcaseId + " DispatchIntent delta").isZero();
        assertThat(after.effects().wechatPaymentFacts() - before.effects().wechatPaymentFacts())
                .as(subcaseId + " W delta").isZero();
        assertThat(after.effects().upstreamDebitFacts() - before.effects().upstreamDebitFacts())
                .as(subcaseId + " U delta").isZero();
        assertThat(after.effects().deliveryFacts() - before.effects().deliveryFacts())
                .as(subcaseId + " D delta").isZero();
        assertThat(after.effects().refundFacts() - before.effects().refundFacts())
                .as(subcaseId + " R delta").isZero();
        assertThat(after.effects().localLedgerFacts() - before.effects().localLedgerFacts())
                .as(subcaseId + " L delta").isZero();
        assertThat(after.effects().ledgerEntries() - before.effects().ledgerEntries())
                .as(subcaseId + " LedgerEntry delta").isZero();
        assertThat(after.effects().externalCalls() - before.effects().externalCalls())
                .as(subcaseId + " ExternalCall delta").isZero();
    }

    private void writeEvidence(String subcaseId, Variant variant, ObjectNode request,
                               int expectedHttpStatus, String expectedProjectCode,
                               String expectedOrderRef,
                               int actualHttpStatus, String actualProjectCode, JsonNode response,
                               CountSnapshot before, CountSnapshot after) throws Exception {
        ObjectNode evidence = baseEvidence(subcaseId, variant.name(),
                sha256(json.writeValueAsBytes(request)));
        ObjectNode expected = evidence.putObject("expected");
        expected.put("httpStatus", expectedHttpStatus);
        expected.put("projectCode", expectedProjectCode);
        expected.put("allObservedWriteDelta", 0);
        if (variant == Variant.RECEIVER_REPLAY) {
            expected.put("resourceRef", expectedOrderRef);
            expected.put("currentProjectionOrderRef", expectedOrderRef);
            expected.put("aggregateVersion", 1L);
        } else if (expectedHttpStatus != 401) {
            expected.put("dataAbsent", true);
        }
        ObjectNode actual = evidence.putObject("actual");
        actual.put("httpStatus", actualHttpStatus);
        actual.put("projectCode", actualProjectCode);
        actual.put("dataAbsent", !response.hasNonNull("data"));
        actual.put("resourceRef", response.path("resourceRef").asText(null));
        actual.put("currentProjectionOrderRef",
                response.path("currentProjection").path("orderRef").asText(null));
        if (response.has("aggregateVersion")) actual.put("aggregateVersion", response.path("aggregateVersion").asLong());
        else actual.putNull("aggregateVersion");
        actual.set("delta", deltaJson(before, after));
        evidence.set("response", response);
        evidence.set("before", countJson(before));
        evidence.set("after", countJson(after));
        evidence.set("delta", deltaJson(before, after));
        writeCaseEvidence(subcaseId, evidence);
    }

    private void writeQualificationEvidence(String subcaseId, String activeProfile, String persistenceMode,
                                            String expectedProjectCode, Throwable rejection, JsonNode response,
                                            CountSnapshot before, CountSnapshot after)
            throws Exception {
        ObjectNode evidence = baseEvidence(subcaseId, "SERVICE_QUALIFICATION",
                sha256((activeProfile + "|" + persistenceMode).getBytes(StandardCharsets.UTF_8)));
        evidence.put("responseKind", "SERVICE_QUALIFICATION");
        evidence.put("activeProfile", activeProfile);
        evidence.put("persistenceMode", persistenceMode);
        evidence.put("qualification", 0);
        ObjectNode expected = evidence.putObject("expected");
        expected.put("qualification", 0);
        expected.put("projectCode", expectedProjectCode);
        expected.put("rejectionType", FlowRejectedException.class.getName());
        expected.put("rejectionMessage", "LOCAL_SYNTHETIC_IDENTITY_REQUIRED");
        expected.put("allObservedWriteDelta", 0);
        ObjectNode actual = evidence.putObject("actual");
        actual.put("qualification", 0);
        actual.put("projectCode", response.path("projectCode").asText());
        actual.put("rejectionType", rejection.getClass().getName());
        actual.put("rejectionMessage", rejection.getMessage());
        actual.set("delta", deltaJson(before, after));
        evidence.set("response", response);
        evidence.set("before", countJson(before));
        evidence.set("after", countJson(after));
        evidence.set("delta", deltaJson(before, after));
        writeCaseEvidence(subcaseId, evidence);
    }

    private ObjectNode baseEvidence(String subcaseId, String inputVariant, String fixtureDigest) {
        SubcaseDefinition definition = SUBCASES.get(subcaseId);
        if (definition == null) throw new IllegalArgumentException("UNREGISTERED_STABLE_SUBCASE");
        ObjectNode evidence = json.createObjectNode();
        appendFixedIdentity(evidence);
        evidence.put("scenarioId", definition.scenarioId());
        evidence.put("matrixSubcaseId", definition.matrixSubcaseId());
        evidence.put("subcaseId", subcaseId);
        evidence.put("parameterId", definition.parameterId());
        evidence.put("inputVariant", inputVariant);
        evidence.put("implementationManifestRef", "evidence-package-index.json#/implementationManifest");
        evidence.put("fixtureDigest", fixtureDigest);
        evidence.put("environment", "LOCAL_SYNTHETIC");
        evidence.put("networkPolicy", "DENY_EXTERNAL");
        evidence.put("executionStatus", "PASS");
        evidence.put("defectRef", "N/A");
        return evidence;
    }

    private void writeCaseEvidence(String subcaseId, ObjectNode evidence) throws Exception {
        SubcaseDefinition definition = SUBCASES.get(subcaseId);
        Path file = evidenceDirectory.resolve(evidenceFileName(subcaseId, definition.parameterId()));
        String serialized = json.writeValueAsString(evidence);
        for (String marker : PROHIBITED_PAYLOAD_MARKERS) {
            assertThat(serialized).as(subcaseId + " prohibited payload marker "
                    + sha256(marker.getBytes(StandardCharsets.UTF_8))).doesNotContain(marker);
        }
        writeJsonAtomically(json, file, evidence);
        System.out.println("M1_SUBCASE_EVIDENCE|" + json.writeValueAsString(evidence));
    }

    private ObjectNode countJson(CountSnapshot snapshot) {
        ObjectNode counts = json.createObjectNode();
        counts.put("Order", snapshot.orders());
        counts.put("OrderBusinessKey", snapshot.orderBusinessKeys());
        appendEffects(counts, snapshot.effects());
        return counts;
    }

    private ObjectNode deltaJson(CountSnapshot before, CountSnapshot after) {
        ObjectNode delta = json.createObjectNode();
        delta.put("Order", after.orders() - before.orders());
        delta.put("OrderBusinessKey", after.orderBusinessKeys() - before.orderBusinessKeys());
        delta.put("PaymentIntent", after.effects().paymentIntents() - before.effects().paymentIntents());
        delta.put("PaymentAttempt", after.effects().paymentAttempts() - before.effects().paymentAttempts());
        delta.put("DispatchIntent", after.effects().dispatchIntents() - before.effects().dispatchIntents());
        delta.put("W", after.effects().wechatPaymentFacts() - before.effects().wechatPaymentFacts());
        delta.put("U", after.effects().upstreamDebitFacts() - before.effects().upstreamDebitFacts());
        delta.put("D", after.effects().deliveryFacts() - before.effects().deliveryFacts());
        delta.put("R", after.effects().refundFacts() - before.effects().refundFacts());
        delta.put("L", after.effects().localLedgerFacts() - before.effects().localLedgerFacts());
        delta.put("LedgerEntry", after.effects().ledgerEntries() - before.effects().ledgerEntries());
        delta.put("ExternalCall", after.effects().externalCalls() - before.effects().externalCalls());
        return delta;
    }

    private static void appendEffects(ObjectNode counts, OrderCreationSideEffectSnapshot effects) {
        counts.put("PaymentIntent", effects.paymentIntents());
        counts.put("PaymentAttempt", effects.paymentAttempts());
        counts.put("DispatchIntent", effects.dispatchIntents());
        counts.put("W", effects.wechatPaymentFacts());
        counts.put("U", effects.upstreamDebitFacts());
        counts.put("D", effects.deliveryFacts());
        counts.put("R", effects.refundFacts());
        counts.put("L", effects.localLedgerFacts());
        counts.put("LedgerEntry", effects.ledgerEntries());
        counts.put("ExternalCall", effects.externalCalls());
    }

    private static void appendFixedIdentity(ObjectNode node) {
        node.put("evidencePackageId", EVIDENCE_PACKAGE_ID);
        node.put("executionRunId", executionRunId);
        node.put("executedAt", executedAt);
        node.put("matrixInputSha256", MATRIX_INPUT_SHA);
        node.put("d1Sha256", D1_SHA);
        node.put("d2Sha256", D2_SHA);
        node.put("d3RegistrySha256", D3_REGISTRY_SHA);
        node.put("d3_03Sha256", D3_03_SHA);
        node.put("d3_05Sha256", D3_05_SHA);
        node.put("backendImplementationAggregateSha256", implementationAggregateSha);
        node.put("evidenceGeneratorSha256", generatorSha);
    }

    private static Map<String, SubcaseDefinition> subcases() {
        Map<String, SubcaseDefinition> definitions = new LinkedHashMap<>();
        register(definitions, "M1-S01", "M1-ORD-002-RECEIVER-REPLAY", "M1-ORD-002-RECEIVER-REPLAY",
                "FX-M1-Q-VALID-A+FX-M1-AUTH-CURRENT");
        register(definitions, "M1-S03", "M1-ORD-005-EXPIRED-QUOTE", "M1-ORD-005-EXPIRED-QUOTE",
                "FX-M1-Q-EXPIRED");
        register(definitions, "M1-S03", "M1-ORD-006-LEGACY-V4-NULL", "M1-ORD-006-LEGACY-V4-NULL",
                "FX-M1-Q-LEGACY-V4-NULL");
        register(definitions, "M1-S03", "M1-ORD-007-SUPPORT-VERSION-DRIFT",
                "M1-ORD-007-SUPPORT-VERSION-DRIFT", "FX-M1-Q-SUPPORT-DRIFT");
        register(definitions, "M1-S03", "M1-ORD-008-CATALOG-VERSION-DRIFT",
                "M1-ORD-008-CATALOG-VERSION-DRIFT", "FX-M1-Q-CATALOG-DRIFT");
        register(definitions, "M1-S03", "M1-ORD-009-CROSS-SUBJECT", "M1-ORD-009-CROSS-SUBJECT",
                "FX-M1-Q-FOREIGN");
        register(definitions, "M1-S04", "M1-ORD-010-MISSING-QUOTEREF", "M1-ORD-010-MISSING-QUOTEREF",
                "MISSING_QUOTE_REF");
        register(definitions, "M1-S04", "M1-ORD-011-MISSING-COMMANDID", "M1-ORD-011-MISSING-COMMANDID",
                "MISSING_COMMAND_ID");
        register(definitions, "M1-S04", "M1-ORD-012-MISSING-IDEMPOTENCYKEY",
                "M1-ORD-012-MISSING-IDEMPOTENCYKEY", "MISSING_IDEMPOTENCY_KEY");
        register(definitions, "M1-S04", "M1-ORD-013-INVALID-CREATION-PRECONDITION",
                "M1-ORD-013-INVALID-CREATION-PRECONDITION", "INVALID_CREATION_PRECONDITION");
        register(definitions, "M1-S04", "M1-ORD-014-MISSING-SNAPSHOT-FIELD",
                "M1-ORD-014-MISSING-SNAPSHOT-FIELD", "MISSING_CURRENCY");
        register(definitions, "M1-S04", "M1-ORD-014A-MISSING-SESSIONVERSION",
                "M1-ORD-014A-MISSING-SESSIONVERSION", "MISSING_SESSION_VERSION");
        register(definitions, "M1-S04", "M1-ORD-014B-MISSING-AUTHSETREF",
                "M1-ORD-014B-MISSING-AUTHSETREF", "MISSING_AUTHORIZATION_SET_REF");
        register(definitions, "M1-S05", "M1-ORD-014C-STALE-SESSIONVERSION",
                "M1-ORD-014C-STALE-SESSIONVERSION", "STALE_SESSION_VERSION");
        register(definitions, "M1-S05", "M1-ORD-014D-WRONG-AUTHSETREF",
                "M1-ORD-014D-WRONG-AUTHSETREF", "WRONG_AUTHORIZATION_SET_REF");
        register(definitions, "M1-S05", "M1-ORD-014E-REVOKED-OR-EVIDENCE-MISMATCH",
                "M1-ORD-014E-REVOKED-OR-EVIDENCE-MISMATCH", "REVOKED_OR_EVIDENCE_MISMATCH");
        register(definitions, "M1-S05", "M1-ORD-014F-NONLOCAL-OR-RELEASE",
                "M1-ORD-014F-A-NONLOCAL-PROFILE", "NONLOCAL_PROFILE");
        register(definitions, "M1-S05", "M1-ORD-014F-NONLOCAL-OR-RELEASE",
                "M1-ORD-014F-B-RELEASE-HIT", "RELEASE_HIT");
        register(definitions, "M1-S05", "M1-ORD-014F-NONLOCAL-OR-RELEASE",
                "M1-ORD-014F-C-NO-TEST-TOKEN", "NO_TRUSTED_TEST_TOKEN");
        register(definitions, "M1-S06", "M1-ORD-015-CLIENT-AMOUNT-TAMPER",
                "M1-ORD-015-CLIENT-AMOUNT-TAMPER", "CLIENT_AMOUNT_TAMPER");
        register(definitions, "M1-S06", "M1-ORD-016-CLIENT-CURRENCY-TAMPER",
                "M1-ORD-016-CLIENT-CURRENCY-TAMPER", "CLIENT_CURRENCY_TAMPER");
        register(definitions, "M1-S06", "M1-ORD-017-CLIENT-PRODUCT-TAMPER",
                "M1-ORD-017-CLIENT-PRODUCT-TAMPER", "CLIENT_PRODUCT_TAMPER");
        register(definitions, "M1-S06", "M1-ORD-018-CLIENT-DENOMINATION-TAMPER",
                "M1-ORD-018-CLIENT-DENOMINATION-TAMPER", "CLIENT_DENOMINATION_TAMPER");
        return Map.copyOf(definitions);
    }

    private static void register(Map<String, SubcaseDefinition> definitions, String scenarioId,
                                 String matrixSubcaseId, String subcaseId, String parameterId) {
        if (definitions.put(subcaseId,
                new SubcaseDefinition(scenarioId, matrixSubcaseId, parameterId)) != null) {
            throw new IllegalStateException("DUPLICATE_STABLE_SUBCASE");
        }
    }

    private static String evidenceFileName(String subcaseId, String parameterId) {
        return (subcaseId + "__" + parameterId).replaceAll("[^A-Za-z0-9._-]", "_") + ".json";
    }

    private static boolean caseEvidenceValid(JsonNode evidence, String subcaseId,
                                             SubcaseDefinition definition) {
        List<String> required = List.of("evidencePackageId", "scenarioId", "matrixSubcaseId", "subcaseId",
                "parameterId", "executionRunId", "executedAt", "matrixInputSha256", "d1Sha256", "d2Sha256",
                "d3RegistrySha256", "d3_03Sha256", "d3_05Sha256",
                "backendImplementationAggregateSha256", "implementationManifestRef", "evidenceGeneratorSha256",
                "environment", "networkPolicy", "fixtureDigest", "expected", "actual", "response", "before",
                "after", "delta", "executionStatus", "defectRef");
        if (required.stream().anyMatch(field -> !evidence.has(field))) return false;
        if (!EVIDENCE_PACKAGE_ID.equals(evidence.path("evidencePackageId").asText())
                || !definition.scenarioId().equals(evidence.path("scenarioId").asText())
                || !definition.matrixSubcaseId().equals(evidence.path("matrixSubcaseId").asText())
                || !subcaseId.equals(evidence.path("subcaseId").asText())
                || !definition.parameterId().equals(evidence.path("parameterId").asText())
                || !executionRunId.equals(evidence.path("executionRunId").asText())
                || !MATRIX_INPUT_SHA.equals(evidence.path("matrixInputSha256").asText())
                || !implementationAggregateSha.equals(
                evidence.path("backendImplementationAggregateSha256").asText())
                || !generatorSha.equals(evidence.path("evidenceGeneratorSha256").asText())
                || !"PASS".equals(evidence.path("executionStatus").asText())) return false;
        JsonNode expected = evidence.path("expected");
        JsonNode actual = evidence.path("actual");
        if (expected.has("httpStatus")
                && expected.path("httpStatus").asInt() != actual.path("httpStatus").asInt()) return false;
        if (expected.has("projectCode")
                && !expected.path("projectCode").asText().equals(actual.path("projectCode").asText())) return false;
        if (expected.has("resourceRef")
                && !expected.path("resourceRef").asText().equals(actual.path("resourceRef").asText())) return false;
        if (expected.has("currentProjectionOrderRef") && !expected.path("currentProjectionOrderRef").asText()
                .equals(actual.path("currentProjectionOrderRef").asText())) return false;
        if (expected.has("aggregateVersion")
                && expected.path("aggregateVersion").asLong() != actual.path("aggregateVersion").asLong()) return false;
        if (expected.has("dataAbsent")
                && expected.path("dataAbsent").asBoolean() != actual.path("dataAbsent").asBoolean()) return false;
        if (expected.has("rejectionType")
                && !expected.path("rejectionType").asText().equals(actual.path("rejectionType").asText())) return false;
        if (expected.has("rejectionMessage") && !expected.path("rejectionMessage").asText()
                .equals(actual.path("rejectionMessage").asText())) return false;
        JsonNode delta = evidence.path("delta");
        if (!delta.isObject() || delta.size() != 12) return false;
        for (JsonNode value : delta) if (!value.isNumber() || value.asLong() != 0L) return false;
        return delta.equals(actual.path("delta"));
    }

    private static String requireProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("MISSING_REQUIRED_PROPERTY:" + name);
        return value;
    }

    private static void verifyFixedInputs(Path root) throws Exception {
        for (Map.Entry<String, String> entry : FIXED_INPUT_FILES.entrySet()) {
            Path file = root.resolve(entry.getKey());
            assertThat(file).as("fixed input file " + entry.getKey()).isRegularFile();
            assertThat(sha256(Files.readAllBytes(file))).as("fixed input SHA " + entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    private static void verifyFixedSubcaseIdentity() {
        assertThat(FIXED_SUBCASE_IDS).doesNotHaveDuplicates().hasSize(23);
        assertThat(SUBCASES.keySet()).containsExactlyInAnyOrderElementsOf(FIXED_SUBCASE_IDS);
        assertThat(SUBCASES.get("M1-ORD-014F-A-NONLOCAL-PROFILE"))
                .isEqualTo(new SubcaseDefinition("M1-S05", "M1-ORD-014F-NONLOCAL-OR-RELEASE",
                        "NONLOCAL_PROFILE"));
        assertThat(SUBCASES.get("M1-ORD-014F-B-RELEASE-HIT"))
                .isEqualTo(new SubcaseDefinition("M1-S05", "M1-ORD-014F-NONLOCAL-OR-RELEASE",
                        "RELEASE_HIT"));
        assertThat(SUBCASES.get("M1-ORD-014F-C-NO-TEST-TOKEN"))
                .isEqualTo(new SubcaseDefinition("M1-S05", "M1-ORD-014F-NONLOCAL-OR-RELEASE",
                        "NO_TRUSTED_TEST_TOKEN"));
    }

    private static void verifyConsumedWrapperAuthorization(Path authorizationFile, Path root) throws Exception {
        JsonNode authorization = new ObjectMapper().readTree(Files.readString(authorizationFile,
                StandardCharsets.UTF_8));
        assertThat(authorization.path("finalRunAuthorized").asBoolean()).isTrue();
        assertThat(authorization.path("authorizationConsumed").asBoolean()).isTrue();
        assertThat(authorization.path("runId").asText()).isEqualTo(executionRunId);
        assertThat(authorization.path("testSelector").asText()).isEqualTo(REQUIRED_TEST_SELECTOR);
        assertThat(authorization.path("expectedGeneratorSha256").asText()).isEqualTo(generatorSha);
        assertThat(authorization.path("stagingDirectory").asText()).isEqualTo(
                Path.of(requireProperty("m1.evidence.stagingDirectory")).toAbsolutePath().normalize().toString());
        Path wrapper = root.resolve(WRAPPER_PATH);
        assertThat(wrapper).isRegularFile();
        assertThat(authorization.path("wrapperSha256").asText())
                .isEqualTo(sha256(Files.readAllBytes(wrapper)));
        assertThat(authorization.path("fixedCommandSelector").asText())
                .isEqualTo("-Dtest=M1OrderSubcaseEvidenceTest");
    }

    private static String implementationAggregate() throws Exception {
        Path root = repositoryRoot();
        List<String> lines = new ArrayList<>();
        for (String path : IMPLEMENTATION_PATHS) {
            lines.add(path + "|" + sha256(Files.readAllBytes(root.resolve(path))));
        }
        return sha256(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("pom.xml")) && Files.isDirectory(candidate.resolve("apps"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("REPOSITORY_ROOT_NOT_FOUND");
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static long countOccurrences(String text, String marker) {
        long count = 0;
        int from = 0;
        while ((from = text.indexOf(marker, from)) >= 0) {
            count++;
            from += marker.length();
        }
        return count;
    }

    private static void writeJsonAtomically(ObjectMapper mapper, Path target, ObjectNode content) throws Exception {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(content) + "\n",
                StandardCharsets.UTF_8);
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private enum Variant {
        RECEIVER_REPLAY,
        EXPIRED_QUOTE,
        LEGACY_V4_NULL,
        SUPPORT_VERSION_DRIFT,
        CATALOG_VERSION_DRIFT,
        CROSS_SUBJECT,
        MISSING_QUOTE_REF,
        MISSING_COMMAND_ID,
        MISSING_IDEMPOTENCY_KEY,
        INVALID_CREATION_PRECONDITION,
        MISSING_SNAPSHOT_FIELD,
        MISSING_SESSION_VERSION,
        MISSING_AUTHORIZATION_SET_REF,
        STALE_SESSION_VERSION,
        WRONG_AUTHORIZATION_SET_REF,
        REVOKED_OR_EVIDENCE_MISMATCH,
        NO_TEST_TOKEN,
        CLIENT_AMOUNT_TAMPER,
        CLIENT_CURRENCY_TAMPER,
        CLIENT_PRODUCT_TAMPER,
        CLIENT_DENOMINATION_TAMPER
    }

    private record CountSnapshot(long orders, long orderBusinessKeys,
                                 OrderCreationSideEffectSnapshot effects) {}

    private record SubcaseDefinition(String scenarioId, String matrixSubcaseId, String parameterId) {}
}
