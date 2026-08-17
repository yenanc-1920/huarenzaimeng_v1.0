package com.huarenzaimeng.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseSecretBoundaryValidatorTest {
    @Test void acceptsClosedReleaseCapabilities() {
        assertThatCode(() -> new ReleaseSecretBoundaryValidator(releaseEnvironment()))
                .doesNotThrowAnyException();
    }

    @Test void acceptsExactIsolatedDevelopmentProfilePair() {
        MockEnvironment environment = releaseEnvironment()
                .withProperty("hz.dev-function-release.enabled", "true")
                .withProperty("hz.v1-dev-data.enabled", "true");
        environment.setActiveProfiles("release-mysql", "local-mysql");
        assertThatCode(() -> new ReleaseSecretBoundaryValidator(environment))
                .doesNotThrowAnyException();
    }

    @Test void rejectsDevelopmentProfilePairWithoutBothDevelopmentGates() {
        MockEnvironment environment = releaseEnvironment()
                .withProperty("hz.dev-function-release.enabled", "true")
                .withProperty("hz.v1-dev-data.enabled", "false");
        environment.setActiveProfiles("release-mysql", "local-mysql");
        assertRejected(environment, "spring.profiles.active", "RELEASE_PROFILE_MUST_NOT_MIX_WITH_TEST_PROFILES");
    }

    @Test void acceptsExactIsolatedTestProfilePairWithoutDevelopmentData() {
        MockEnvironment environment = releaseEnvironment()
                .withProperty("hz.environment.name", "test")
                .withProperty("hz.environment.migration-enabled", "true")
                .withProperty("hz.environment.function-release-enabled", "true")
                .withProperty("hz.v1-dev-data.enabled", "false");
        environment.setActiveProfiles("release-mysql", "test-mysql");
        assertThatCode(() -> new ReleaseSecretBoundaryValidator(environment))
                .doesNotThrowAnyException();
    }

    @Test void acceptsExactIsolatedStageProfilePairWithoutDevelopmentData() {
        MockEnvironment environment = releaseEnvironment()
                .withProperty("hz.environment.name", "stage")
                .withProperty("hz.environment.migration-enabled", "true")
                .withProperty("hz.environment.function-release-enabled", "true")
                .withProperty("hz.v1-dev-data.enabled", "false");
        environment.setActiveProfiles("release-mysql", "stage-mysql");
        assertThatCode(() -> new ReleaseSecretBoundaryValidator(environment))
                .doesNotThrowAnyException();
    }

    @Test void rejectsStageProfileWhenEnvironmentIdentityIsTest() {
        MockEnvironment environment = releaseEnvironment()
                .withProperty("hz.environment.name", "test")
                .withProperty("hz.environment.migration-enabled", "true")
                .withProperty("hz.environment.function-release-enabled", "true")
                .withProperty("hz.v1-dev-data.enabled", "false");
        environment.setActiveProfiles("release-mysql", "stage-mysql");
        assertRejected(environment, "spring.profiles.active", "RELEASE_PROFILE_MUST_NOT_MIX_WITH_TEST_PROFILES");
    }

    @Test void rejectsDevelopmentProfilePairWithAnyThirdProfile() {
        MockEnvironment environment = releaseEnvironment()
                .withProperty("hz.dev-function-release.enabled", "true")
                .withProperty("hz.v1-dev-data.enabled", "true");
        environment.setActiveProfiles("release-mysql", "local-mysql", "mock");
        assertRejected(environment, "spring.profiles.active", "RELEASE_PROFILE_MUST_NOT_MIX_WITH_TEST_PROFILES");
    }

    @Test void rejectsMixedMockProfile() {
        MockEnvironment environment = releaseEnvironment();
        environment.setActiveProfiles("release-mysql", "mock");
        assertRejected(environment, "spring.profiles.active", "RELEASE_PROFILE_MUST_NOT_MIX_WITH_TEST_PROFILES");
    }

    @Test void rejectsAnyAdditionalProfile() {
        MockEnvironment environment = releaseEnvironment();
        environment.setActiveProfiles("release-mysql", "developer-tools");
        assertRejected(environment, "spring.profiles.active", "RELEASE_PROFILE_MUST_NOT_MIX_WITH_TEST_PROFILES");
    }

    @Test void rejectsNonMysqlPersistence() {
        MockEnvironment environment = releaseEnvironment().withProperty("hz.persistence.mode", "in-memory");
        assertRejected(environment, "hz.persistence.mode", "RELEASE_CAPABILITY_FORBIDDEN");
    }

    @Test void rejectsEverySyntheticOrTestMode() {
        assertModeRejected("hz.p014.mode", "local-synthetic");
        assertModeRejected("hz.p014.fixture-mode", "explicit-local-synthetic-sample");
        assertModeRejected("hz.p021.mode", "test-readonly");
        assertModeRejected("hz.p021.fixture-mode", "explicit-local-synthetic-sample");
        assertModeRejected("hz.a110.mode", "local-synthetic");
        assertModeRejected("hz.life-content.mode", "local-synthetic");
        assertModeRejected("hz.temporal-overview.mode", "local-synthetic");
        assertModeRejected("hz.admin-auth.bootstrap-enabled", "true");
        assertModeRejected("hz.it-session.buyer-session-version", "1");
    }

    @Test void rejectsTestAccessTokenWithoutIncludingIt() {
        String forbidden = "synthetic-test-token-must-not-reach-release";
        MockEnvironment environment = releaseEnvironment().withProperty(ReleaseSecretBoundaryValidator.TEST_TOKEN_PROPERTY, forbidden);
        assertThatThrownBy(() -> new ReleaseSecretBoundaryValidator(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ReleaseSecretBoundaryValidator.TEST_TOKEN_PROPERTY)
                .hasMessageContaining("TEST_SECRET_FORBIDDEN_IN_RELEASE")
                .hasMessageNotContaining(forbidden);
    }

    @Test void rejectsBootstrapTokenEvenWhenBootstrapIsDisabled() {
        String forbidden = "retired-bootstrap-token";
        MockEnvironment environment = releaseEnvironment().withProperty("hz.admin-auth.bootstrap-token", forbidden);
        assertThatThrownBy(() -> new ReleaseSecretBoundaryValidator(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hz.admin-auth.bootstrap-token")
                .hasMessageNotContaining(forbidden);
    }

    @Test void rejectsIntegrationIdentityWithoutIncludingIt() {
        String forbidden = "it-subject-must-not-reach-release";
        MockEnvironment environment = releaseEnvironment().withProperty("hz.it-session.buyer-subject-ref", forbidden);
        assertThatThrownBy(() -> new ReleaseSecretBoundaryValidator(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hz.it-session.buyer-subject-ref")
                .hasMessageNotContaining(forbidden);
    }

    private static void assertModeRejected(String property, String value) {
        assertRejected(releaseEnvironment().withProperty(property, value), property, "RELEASE_CAPABILITY_FORBIDDEN");
    }

    private static void assertRejected(MockEnvironment environment, String property, String reason) {
        assertThatThrownBy(() -> new ReleaseSecretBoundaryValidator(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(property)
                .hasMessageContaining(reason);
    }

    private static MockEnvironment releaseEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("hz.persistence.mode", "mysql")
                .withProperty("hz.p014.mode", "disabled")
                .withProperty("hz.p014.fixture-mode", "no-default")
                .withProperty("hz.p021.mode", "disabled")
                .withProperty("hz.p021.fixture-mode", "no-default")
                .withProperty("hz.a110.mode", "disabled")
                .withProperty("hz.life-content.mode", "disabled")
                .withProperty("hz.temporal-overview.mode", "disabled")
                .withProperty("hz.admin-auth.bootstrap-enabled", "false")
                .withProperty("hz.it-session.buyer-session-version", "0");
        environment.setActiveProfiles("release-mysql");
        return environment;
    }
}
