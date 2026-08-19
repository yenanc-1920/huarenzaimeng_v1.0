package com.huarenzaimeng.api.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Component
@Profile("release-mysql")
final class ReleaseSecretBoundaryValidator {
    static final String TEST_TOKEN_PROPERTY = "hz.test-access-token";
    static final String CONTENT_TOKEN_PROPERTY = "hz.project-auth.content-token";

    private static final List<String> TEST_IDENTITY_PROPERTIES = List.of(
            TEST_TOKEN_PROPERTY,
            CONTENT_TOKEN_PROPERTY,
            "hz.it-session.buyer-token",
            "hz.it-session.cs-token",
            "hz.it-session.fin-token",
            "hz.it-session.buyer-subject-ref",
            "hz.it-session.buyer-session-ref",
            "hz.it-session.buyer-authorization-set-ref",
            "hz.it-session.buyer-authorization-evidence-version",
            "hz.it-session.buyer-authorized-order-refs");

    ReleaseSecretBoundaryValidator(Environment environment) {
        requireApprovedReleaseProfiles(environment);
        requireValue(environment, "hz.persistence.mode", "mysql");
        requireValue(environment, "hz.p014.mode", "disabled");
        requireValue(environment, "hz.p014.fixture-mode", "no-default");
        requireValue(environment, "hz.p021.mode", "disabled");
        requireValue(environment, "hz.p021.fixture-mode", "no-default");
        requireValue(environment, "hz.a110.mode", "disabled");
        requireValue(environment, "hz.life-content.mode", "disabled");
        requireValue(environment, "hz.temporal-overview.mode", "disabled");
        requireValue(environment, "hz.admin-auth.bootstrap-enabled", "false");
        requireValue(environment, "hz.it-session.buyer-session-version", "0");
        requireAbsent("hz.admin-auth.bootstrap-token", environment.getProperty("hz.admin-auth.bootstrap-token"));
        TEST_IDENTITY_PROPERTIES.forEach(property -> requireAbsent(property, environment.getProperty(property)));
    }

    private static void requireApprovedReleaseProfiles(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        List<String> profiles = Arrays.stream(activeProfiles).map(String::trim).filter(value -> !value.isEmpty()).toList();
        Set<String> profileSet = Set.copyOf(profiles);
        boolean releaseOnly = profiles.size() == 1 && profileSet.equals(Set.of("release-mysql"));
        boolean isolatedDevelopment = profiles.size() == 2
                && profileSet.equals(Set.of("release-mysql", "local-mysql"))
                && "dev".equals(environment.getProperty("hz.environment.name"))
                && environment.getProperty("hz.environment.migration-enabled", Boolean.class, false)
                && environment.getProperty("hz.v1-dev-data.enabled", Boolean.class, false);
        boolean isolatedTest = profiles.size() == 2
                && profileSet.equals(Set.of("release-mysql", "test-mysql"))
                && "test".equals(environment.getProperty("hz.environment.name"))
                && environment.getProperty("hz.environment.migration-enabled", Boolean.class, false)
                && !environment.getProperty("hz.v1-dev-data.enabled", Boolean.class, false);
        boolean isolatedStage = profiles.size() == 2
                && profileSet.equals(Set.of("release-mysql", "stage-mysql"))
                && "stage".equals(environment.getProperty("hz.environment.name"))
                && environment.getProperty("hz.environment.migration-enabled", Boolean.class, false)
                && !environment.getProperty("hz.v1-dev-data.enabled", Boolean.class, false);
        boolean isolatedProd = profiles.size() == 2
                && profileSet.equals(Set.of("release-mysql", "prod-mysql"))
                && "prod".equals(environment.getProperty("hz.environment.name"))
                && environment.getProperty("hz.environment.migration-enabled", Boolean.class, false)
                && !environment.getProperty("hz.v1-dev-data.enabled", Boolean.class, false);
        if (!releaseOnly && !isolatedDevelopment && !isolatedTest && !isolatedStage && !isolatedProd) {
            throw invalid("spring.profiles.active", "RELEASE_PROFILE_MUST_NOT_MIX_WITH_TEST_PROFILES");
        }
    }

    private static void requireValue(Environment environment, String propertyName, String expected) {
        String actual = environment.getProperty(propertyName);
        if (actual == null || !actual.trim().equalsIgnoreCase(expected)) {
            throw invalid(propertyName, "RELEASE_CAPABILITY_FORBIDDEN");
        }
    }

    private static void requireAbsent(String propertyName, String value) {
        if (value != null && !value.isBlank()) throw invalid(propertyName, "TEST_SECRET_FORBIDDEN_IN_RELEASE");
    }

    private static IllegalStateException invalid(String propertyNames, String reason) {
        return new IllegalStateException("Invalid release configuration: " + propertyNames + " reason=" + reason);
    }
}
