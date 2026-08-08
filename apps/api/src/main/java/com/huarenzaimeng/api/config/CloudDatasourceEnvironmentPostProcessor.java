package com.huarenzaimeng.api.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

public final class CloudDatasourceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty("HZ_DATASOURCE_URL");
        if (raw == null || raw.isBlank()) {
            return;
        }

        String jdbcUrl = unquote(raw.strip());
        if (!jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException(
                    "HZ_DATASOURCE_URL must start with jdbc:mysql:// (value omitted, length=" + jdbcUrl.length() + ")");
        }

        environment.getPropertySources().addFirst(new MapPropertySource(
                "huarenCloudDatasourceOverride",
                Map.of(
                        "spring.datasource.url", jdbcUrl,
                        "spring.flyway.url", jdbcUrl)));
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1).strip();
            }
        }
        return value;
    }
}
