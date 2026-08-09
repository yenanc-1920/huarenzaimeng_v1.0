package com.huarenzaimeng.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseSecretBoundaryValidatorTest {
    @Test void acceptsAbsentTestOnlySecrets() {
        assertThatCode(() -> new ReleaseSecretBoundaryValidator("", "")).doesNotThrowAnyException();
    }

    @Test void rejectsTestAccessTokenWithoutIncludingIt() {
        String forbidden = "synthetic-test-token-must-not-reach-release";
        assertThatThrownBy(() -> new ReleaseSecretBoundaryValidator(forbidden, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ReleaseSecretBoundaryValidator.TEST_TOKEN_PROPERTY)
                .hasMessageContaining("TEST_SECRET_FORBIDDEN_IN_RELEASE")
                .hasMessageNotContaining(forbidden);
    }

    @Test void rejectsContentAdminTokenWithoutIncludingIt() {
        String forbidden = "synthetic-content-token-must-not-reach-release";
        assertThatThrownBy(() -> new ReleaseSecretBoundaryValidator("", forbidden))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ReleaseSecretBoundaryValidator.CONTENT_TOKEN_PROPERTY)
                .hasMessageNotContaining(forbidden);
    }
}
