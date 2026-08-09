package com.huarenzaimeng.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminReadContractTest {
    @Test void mapperIsBoundedReadOnlyAndNeverSelectsRawPhoneOrAuthSecrets() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/huarenzaimeng/api/AdminReadMapper.java"));
        assertThat(source).contains("LIMIT 200", "q.phone_masked")
                .doesNotContain("phone_raw", "password_hash", "token_digest", "INSERT ", "UPDATE ", "DELETE ");
    }

    @Test void projectionsSerializeWithExactFrontendKeys() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var projection = new AdminReadService.AdminProjection("ADMIN_READ_V1", "ORDERS-1", "A140", "CS",
                List.of(new AdminReadService.A140SupportItem("O-1", "01•• •••• 78", "等待付款", "CNY 12.80", "2小时内更新")));
        var root = json.readTree(json.writeValueAsBytes(projection));
        assertThat(root.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("schemaVersion", "projectionVersion", "pageId", "role", "items");
        assertThat(root.get("items").get(0).properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("orderRef", "maskedPhone", "userStatusLabel", "totalLabel", "updatedLabel");
    }
}
