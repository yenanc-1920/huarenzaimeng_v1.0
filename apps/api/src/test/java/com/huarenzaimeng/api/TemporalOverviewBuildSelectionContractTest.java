package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalOverviewBuildSelectionContractTest {
    private static final Map<String, String> FROZEN_INPUTS = Map.of(
            "项目管理/正式交付/D3-技术实现基线/D3-04-数据账务与外部适配方案.md",
            "EED0FD0B2890FC1C3D830EC8A13EE7C7FB10B14F2B0E5AA9C0384B2A723532FF");

    @Test
    void fixed_contract_inputs_match_authorized_sha256() throws Exception {
        Path root = Path.of("..", "..");
        for (Map.Entry<String, String> entry : FROZEN_INPUTS.entrySet()) {
            Path governanceInput = root.resolve(entry.getKey());
            Assumptions.assumeTrue(Files.isRegularFile(governanceInput),
                    "LOCAL_GOVERNANCE_INPUT_NOT_PRESENT");
            byte[] bytes = Files.readAllBytes(governanceInput);
            String actual = HexFormat.of().withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertThat(actual).as(entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    @Test
    void endpoint_is_registered_only_for_explicit_mock_or_test_local_synthetic_mode() throws Exception {
        ConditionalOnProperty condition = TemporalOverviewController.class.getAnnotation(ConditionalOnProperty.class);
        Profile profile = TemporalOverviewController.class.getAnnotation(Profile.class);
        assertThat(condition.name()).containsExactly("hz.temporal-overview.mode");
        assertThat(condition.havingValue()).isEqualTo("local-synthetic");
        assertThat(condition.matchIfMissing()).isFalse();
        assertThat(profile.value()).containsExactlyInAnyOrder("mock", "test");

        String common = Files.readString(Path.of("src/main/resources/application.yml"));
        String mock = Files.readString(Path.of("src/main/resources/application-mock.yml"));
        String release = Files.readString(Path.of("src/main/resources/application-release-mysql.yml"));
        assertThat(common).contains("temporal-overview:\n    mode: disabled");
        assertThat(mock).contains("temporal-overview:\n    mode: local-synthetic");
        assertThat(release).contains("temporal-overview:\n    mode: disabled");
    }
}
