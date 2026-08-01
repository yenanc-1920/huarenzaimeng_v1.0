package com.huarenzaimeng.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Profile("release-mysql")
final class ReleaseSecretBoundaryValidator {
    static final String TEST_TOKEN_PROPERTY = "hz.test-access-token";
    static final String CONTENT_TOKEN_PROPERTY = "hz.project-auth.content-token";
    private static final int MINIMUM_LENGTH = 32;
    private static final int MINIMUM_CHARACTER_CLASSES = 3;

    ReleaseSecretBoundaryValidator(@Value("${hz.test-access-token}") String testAccessToken,
                                   @Value("${hz.project-auth.content-token}") String contentToken) {
        requireHighEntropyProxy(TEST_TOKEN_PROPERTY, testAccessToken);
        requireHighEntropyProxy(CONTENT_TOKEN_PROPERTY, contentToken);
        if (MessageDigest.isEqual(testAccessToken.getBytes(StandardCharsets.UTF_8),
                contentToken.getBytes(StandardCharsets.UTF_8))) {
            throw invalid(TEST_TOKEN_PROPERTY + "," + CONTENT_TOKEN_PROPERTY, "MUST_BE_DISTINCT");
        }
    }

    private static void requireHighEntropyProxy(String propertyName, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(propertyName, "MISSING");
        }
        if (value.length() < MINIMUM_LENGTH) {
            throw invalid(propertyName, "BELOW_MINIMUM_LENGTH");
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean symbol = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                throw invalid(propertyName, "CONTAINS_WHITESPACE");
            }
            upper |= Character.isUpperCase(current);
            lower |= Character.isLowerCase(current);
            digit |= Character.isDigit(current);
            symbol |= !Character.isLetterOrDigit(current);
        }
        int classes = (upper ? 1 : 0) + (lower ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);
        if (classes < MINIMUM_CHARACTER_CLASSES) {
            throw invalid(propertyName, "INSUFFICIENT_COMPLEXITY");
        }
    }

    private static IllegalStateException invalid(String propertyNames, String reason) {
        return new IllegalStateException("Invalid secret configuration: " + propertyNames + " reason=" + reason);
    }
}
