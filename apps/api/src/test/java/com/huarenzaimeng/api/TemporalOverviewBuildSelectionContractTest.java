package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
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
            "项目管理/正式交付/D2-体验与UI设计/D2体验与UI设计版本清单.md",
            "A87E8D7BFE6D8AF421BF87421849D6065DE96EB160A37F2DCCD2AF2EA59A8E82",
            "项目管理/正式交付/D3-技术实现基线/D3-03-领域状态与项目API协议.md",
            "4976103492A1E0B906BEDEB44B86C15BA8B5ED5586735389D237CC630CCD5339",
            "项目管理/正式交付/D3-技术实现基线/D3-04-数据账务与外部适配方案.md",
            "1590FA192D2FA2B71B40AAF80794F5D6BED7E5CBE1A6B0497C816EBAFA384015",
            "项目管理/正式交付/D3-技术实现基线/D3-05-安全可靠性与开发门禁.md",
            "328F45D54215DA54C601CAA0F8FD1A24B3B1D63E16BA60D19A571FFF08DF850E",
            "项目管理/正式交付/D3-技术实现基线/D3技术基线版本清单.md",
            "492D687679228FFA2107C4BC407C156820EED05DF3A6E4950C7599E2AC0BAF87");

    @Test
    void fixed_contract_inputs_match_authorized_sha256() throws Exception {
        Path root = Path.of("..", "..");
        for (Map.Entry<String, String> entry : FROZEN_INPUTS.entrySet()) {
            byte[] bytes = Files.readAllBytes(root.resolve(entry.getKey()));
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
