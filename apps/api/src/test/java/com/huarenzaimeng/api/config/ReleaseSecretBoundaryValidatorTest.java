package com.huarenzaimeng.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseSecretBoundaryValidatorTest {
    private static final String TEST_TOKEN = "T3st-Api-Access_7wQ9pL2xN8vK4mR6sZ1";
    private static final String CONTENT_TOKEN = "C0ntent-Admin_8zR4kM7pV2qL9xN5tY6";

    @Test
    void acceptsDistinctValuesThatMeetTheHighEntropyProxy() {
        assertThatCode(() -> new ReleaseSecretBoundaryValidator(TEST_TOKEN, CONTENT_TOKEN))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingValueWithoutIncludingAnySecret() {
        assertThatThrownBy(() -> new ReleaseSecretBoundaryValidator("", CONTENT_TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ReleaseSecretBoundaryValidator.TEST_TOKEN_PROPERTY)
                .hasMessageContaining("MISSING")
                .hasMessageNotContaining(CONTENT_TOKEN);
    }

    @Test
    void rejectsLowEntropyProxyValueWithoutIncludingIt() {
        String lowEntropy = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        assertThatThrownBy(() -> new ReleaseSecretBoundaryValidator(lowEntropy, CONTENT_TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ReleaseSecretBoundaryValidator.TEST_TOKEN_PROPERTY)
                .hasMessageContaining("INSUFFICIENT_COMPLEXITY")
                .hasMessageNotContaining(lowEntropy)
                .hasMessageNotContaining(CONTENT_TOKEN);
    }

    @Test
    void rejectsEqualValuesWithoutIncludingThem() {
        assertThatThrownBy(() -> new ReleaseSecretBoundaryValidator(TEST_TOKEN, TEST_TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ReleaseSecretBoundaryValidator.TEST_TOKEN_PROPERTY)
                .hasMessageContaining(ReleaseSecretBoundaryValidator.CONTENT_TOKEN_PROPERTY)
                .hasMessageContaining("MUST_BE_DISTINCT")
                .hasMessageNotContaining(TEST_TOKEN);
    }
}
