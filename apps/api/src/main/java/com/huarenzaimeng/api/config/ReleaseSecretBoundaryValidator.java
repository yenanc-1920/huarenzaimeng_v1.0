package com.huarenzaimeng.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("release-mysql")
final class ReleaseSecretBoundaryValidator {
    static final String TEST_TOKEN_PROPERTY = "hz.test-access-token";
    static final String CONTENT_TOKEN_PROPERTY = "hz.project-auth.content-token";
    ReleaseSecretBoundaryValidator(@Value("${hz.test-access-token}") String testAccessToken,
                                   @Value("${hz.project-auth.content-token}") String contentToken) {
        requireAbsent(TEST_TOKEN_PROPERTY, testAccessToken);
        requireAbsent(CONTENT_TOKEN_PROPERTY, contentToken);
    }

    private static void requireAbsent(String propertyName, String value) {
        if (value != null && !value.isBlank()) throw invalid(propertyName, "TEST_SECRET_FORBIDDEN_IN_RELEASE");
    }

    private static IllegalStateException invalid(String propertyNames, String reason) {
        return new IllegalStateException("Invalid secret configuration: " + propertyNames + " reason=" + reason);
    }
}
