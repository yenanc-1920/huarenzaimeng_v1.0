package com.huarenzaimeng.api.adminauth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuthServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    @Test void bootstrapIsOneTimeAndCreatesServerOwnedSuperAdmin() {
        InMemoryAdminAuthStore store = new InMemoryAdminAuthStore();
        AdminAuthService auth = new AdminAuthService(store, true, "one-time-bootstrap-code", 8, CLOCK);

        assertThat(auth.initializationAvailable()).isTrue();
        auth.bootstrap("one-time-bootstrap-code", "yenanc", "南哥", "Correct-Horse-2026!Battery".toCharArray(), "REQ-1");

        assertThat(auth.initializationAvailable()).isFalse();
        AdminAuthService.LoginResult login = auth.login("YENANC", "Correct-Horse-2026!Battery".toCharArray(), "REQ-2");
        assertThat(login.user().roleCode()).isEqualTo("SUPER_ADMIN");
        assertThat(login.user().displayName()).isEqualTo("南哥");
        assertThat(auth.authenticate(login.token())).isPresent();
        assertThatThrownBy(() -> auth.bootstrap("one-time-bootstrap-code", "other", "其他", "Another-Secure-2026!Password".toCharArray(), "REQ-3"))
                .isInstanceOf(AdminAuthService.AuthFailure.class).hasMessage("BOOTSTRAP_UNAVAILABLE");
    }

    @Test void rejectsWeakPasswordWrongBootstrapCodeAndInvalidLogin() {
        InMemoryAdminAuthStore store = new InMemoryAdminAuthStore();
        AdminAuthService auth = new AdminAuthService(store, true, "one-time-bootstrap-code", 8, CLOCK);
        assertThatThrownBy(() -> auth.bootstrap("wrong", "yenanc", "南哥", "Correct-Horse-2026!Battery".toCharArray(), "REQ-1"))
                .isInstanceOf(AdminAuthService.AuthFailure.class).hasMessage("BOOTSTRAP_UNAVAILABLE");
        assertThatThrownBy(() -> auth.bootstrap("one-time-bootstrap-code", "yenanc", "南哥", "short".toCharArray(), "REQ-2"))
                .isInstanceOf(AdminAuthService.AuthFailure.class).hasMessage("WEAK_PASSWORD");
        assertThatThrownBy(() -> auth.login("yenanc", "not-the-password".toCharArray(), "REQ-3"))
                .isInstanceOf(AdminAuthService.AuthFailure.class).hasMessage("INVALID_CREDENTIALS");
    }

    @Test void logoutRevokesOpaqueSession() {
        InMemoryAdminAuthStore store = new InMemoryAdminAuthStore();
        AdminAuthService auth = new AdminAuthService(store, true, "one-time-bootstrap-code", 8, CLOCK);
        auth.bootstrap("one-time-bootstrap-code", "yenanc", "南哥", "Correct-Horse-2026!Battery".toCharArray(), "REQ-1");
        AdminAuthService.LoginResult login = auth.login("yenanc", "Correct-Horse-2026!Battery".toCharArray(), "REQ-2");
        auth.logout(login.token(), "REQ-3");
        assertThat(auth.authenticate(login.token())).isEmpty();
    }

    @Test void fiveFailuresTemporarilyLockAccountWithoutChangingPublicError() {
        InMemoryAdminAuthStore store = new InMemoryAdminAuthStore();
        AdminAuthService auth = new AdminAuthService(store, true, "one-time-bootstrap-code", 8, CLOCK);
        auth.bootstrap("one-time-bootstrap-code", "yenanc", "南哥", "Correct-Horse-2026!Battery".toCharArray(), "REQ-1");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> auth.login("yenanc", "not-the-password".toCharArray(), "REQ-X"))
                    .isInstanceOf(AdminAuthService.AuthFailure.class).hasMessage("INVALID_CREDENTIALS");
        }
        assertThatThrownBy(() -> auth.login("yenanc", "Correct-Horse-2026!Battery".toCharArray(), "REQ-LOCKED"))
                .isInstanceOf(AdminAuthService.AuthFailure.class).hasMessage("INVALID_CREDENTIALS");
    }

    @Test void timeBoundRecoveryResetsPasswordUnlocksAndRevokesSessions() {
        InMemoryAdminAuthStore store = new InMemoryAdminAuthStore();
        AdminAuthService auth = new AdminAuthService(store, true, "bootstrap", true, "recovery-code",
                Instant.parse("2026-08-09T13:00:00Z"), 8, CLOCK);
        auth.bootstrap("bootstrap", "yenanc", "南哥", "Correct-Horse-2026!Battery".toCharArray(), "REQ-1");
        AdminAuthService.LoginResult old = auth.login("yenanc", "Correct-Horse-2026!Battery".toCharArray(), "REQ-2");
        assertThat(auth.recoveryAvailable()).isTrue();
        auth.recover("recovery-code", "yenanc", "Changed-Horse-2026!Password".toCharArray(), "REQ-3");
        assertThat(auth.authenticate(old.token())).isEmpty();
        assertThatThrownBy(() -> auth.login("yenanc", "Correct-Horse-2026!Battery".toCharArray(), "REQ-4")).hasMessage("INVALID_CREDENTIALS");
        assertThat(auth.login("yenanc", "Changed-Horse-2026!Password".toCharArray(), "REQ-5").user().roleCode()).isEqualTo("SUPER_ADMIN");
    }
}
