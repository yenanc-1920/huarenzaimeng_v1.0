package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class P021HostPreflightAuthorizationContractTest {
    private static final Instant FROM = Instant.parse("2026-08-03T12:00:00Z");

    @Test void acceptsExactlyFifteenMinutesAndBothClosedIntervalBoundaries() {
        Instant until=FROM.plusSeconds(900);
        assertThatCode(()->P021ControlledEvidenceJavaHost.validatePreflightAuthorizationWindow(FROM,until,FROM)).doesNotThrowAnyException();
        assertThatCode(()->P021ControlledEvidenceJavaHost.validatePreflightAuthorizationWindow(FROM,until,until)).doesNotThrowAnyException();
    }

    @Test void rejectsFourteenAndSixteenMinuteWindows() {
        assertThatThrownBy(()->P021ControlledEvidenceJavaHost.validatePreflightAuthorizationWindow(FROM,FROM.plusSeconds(840),FROM)).hasMessage("PREFLIGHT_AUTHORIZATION_WINDOW");
        assertThatThrownBy(()->P021ControlledEvidenceJavaHost.validatePreflightAuthorizationWindow(FROM,FROM.plusSeconds(960),FROM)).hasMessage("PREFLIGHT_AUTHORIZATION_WINDOW");
    }

    @Test void rejectsCurrentTimeOutsideTheClosedInterval() {
        Instant until=FROM.plusSeconds(900);
        assertThatThrownBy(()->P021ControlledEvidenceJavaHost.validatePreflightAuthorizationWindow(FROM,until,FROM.minusNanos(1))).hasMessage("PREFLIGHT_AUTHORIZATION_WINDOW");
        assertThatThrownBy(()->P021ControlledEvidenceJavaHost.validatePreflightAuthorizationWindow(FROM,until,until.plusNanos(1))).hasMessage("PREFLIGHT_AUTHORIZATION_WINDOW");
    }

    @Test void acceptsFutureFormattedRunIdAndPermanentlyDeniesFailedRuns() {
        assertThatCode(()->P021ControlledEvidenceJavaHost.validatePreflightRunId("P021-TECH-HOST-PREFLIGHT-FUTURE-SAMPLE-0001")).doesNotThrowAnyException();
        assertThatThrownBy(()->P021ControlledEvidenceJavaHost.validatePreflightRunId("P021-TECH-HOST-PREFLIGHT-20260803-001")).hasMessage("PREFLIGHT_RUN_ID_INVALID_OR_DENIED");
        assertThatThrownBy(()->P021ControlledEvidenceJavaHost.validatePreflightRunId("P021-TECH-HOST-PREFLIGHT-20260803-002")).hasMessage("PREFLIGHT_RUN_ID_INVALID_OR_DENIED");
        assertThatThrownBy(()->P021ControlledEvidenceJavaHost.validatePreflightRunId("P021-TECH-HOST-PREFLIGHT-20260803-003")).hasMessage("PREFLIGHT_RUN_ID_INVALID_OR_DENIED");
    }

    @Test void innerCommandIsStrictlyOfflineAndUsesOnlyTheFrozenProjectRepository() {
        var command=P021ControlledEvidenceJavaHost.preflightInnerCommand();
        org.assertj.core.api.Assertions.assertThat(command).contains("-o","-Dmaven.repo.local="+P021ControlledEvidenceJavaHost.PREFLIGHT_OFFLINE_REPOSITORY.toAbsolutePath().normalize());
        org.assertj.core.api.Assertions.assertThat(String.join("\n",command)).doesNotContain("user.home","repo1.maven.org","central");
    }
}
