package com.huarenzaimeng.api.adminauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
final class AdminAuthService {
    private final AdminAuthStore store;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final byte[] bootstrapDigest;
    private final boolean bootstrapEnabled;
    private final byte[] recoveryDigest;
    private final boolean recoveryEnabled;
    private final Instant recoveryUntil;
    private final Duration sessionTtl;
    private final String dummyPasswordHash = new BCryptPasswordEncoder(12).encode("not-a-real-password-value");

    @Autowired
    AdminAuthService(AdminAuthStore store,
                     @Value("${hz.admin-auth.bootstrap-enabled:false}") boolean bootstrapEnabled,
                     @Value("${hz.admin-auth.bootstrap-token:}") String bootstrapToken,
                     @Value("${hz.admin-auth.recovery-enabled:false}") boolean recoveryEnabled,
                     @Value("${hz.admin-auth.recovery-token:}") String recoveryToken,
                     @Value("${hz.admin-auth.recovery-until:}") String recoveryUntil,
                     @Value("${hz.admin-auth.session-hours:8}") long sessionHours) {
        this(store, bootstrapEnabled, bootstrapToken, recoveryEnabled, recoveryToken, parseInstant(recoveryUntil), sessionHours, Clock.systemUTC());
    }

    AdminAuthService(AdminAuthStore store, boolean bootstrapEnabled, String bootstrapToken, long sessionHours, Clock clock) {
        this(store, bootstrapEnabled, bootstrapToken, false, "", Instant.EPOCH, sessionHours, clock);
    }

    AdminAuthService(AdminAuthStore store, boolean bootstrapEnabled, String bootstrapToken, boolean recoveryEnabled,
                     String recoveryToken, Instant recoveryUntil, long sessionHours, Clock clock) {
        this.store = store;
        this.bootstrapEnabled = bootstrapEnabled;
        this.bootstrapDigest = bootstrapToken.isBlank() ? null : sha256Bytes(bootstrapToken);
        this.recoveryEnabled = recoveryEnabled;
        this.recoveryDigest = recoveryToken.isBlank() ? null : sha256Bytes(recoveryToken);
        this.recoveryUntil = recoveryUntil;
        this.sessionTtl = Duration.ofHours(Math.max(1, Math.min(sessionHours, 24)));
        this.clock = clock;
    }

    boolean initializationAvailable() { return bootstrapEnabled && bootstrapDigest != null && store.userCount() == 0; }
    boolean recoveryAvailable() { return recoveryEnabled && recoveryDigest != null && recoveryUntil.isAfter(clock.instant()) && store.userCount() > 0; }

    void bootstrap(String suppliedToken, String username, String displayName, char[] password, String requestId) {
        Instant now = clock.instant();
        String normalized = normalizeUsername(username);
        if (!initializationAvailable() || suppliedToken == null || !MessageDigest.isEqual(bootstrapDigest, sha256Bytes(suppliedToken))) {
            store.appendAudit(audit("ADMIN_BOOTSTRAP", null, normalized, "REJECTED", requestId, now));
            throw new AuthFailure("BOOTSTRAP_UNAVAILABLE");
        }
        validateDisplayName(displayName);
        validatePassword(password, normalized);
        String userId = UUID.randomUUID().toString();
        try {
            String hash = passwords.encode(new String(password));
            try {
                store.createInitialAdmin(new AdminAuthStore.User(userId, normalized, displayName.trim(), hash, "SUPER_ADMIN", "ACTIVE", 0, null, now),
                        audit("ADMIN_BOOTSTRAP", userId, normalized, "SUCCEEDED", requestId, now));
            } catch (IllegalStateException race) {
                throw new AuthFailure("BOOTSTRAP_UNAVAILABLE");
            }
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    LoginResult login(String username, char[] password, String requestId) {
        Instant now = clock.instant();
        String normalized = normalizeUsername(username);
        Optional<AdminAuthStore.User> candidate = store.findActiveUser(normalized);
        boolean matches;
        try { matches = passwords.matches(new String(password), candidate.map(AdminAuthStore.User::passwordHash).orElse(dummyPasswordHash)); }
        finally { java.util.Arrays.fill(password, '\0'); }
        if (candidate.isPresent() && candidate.get().lockedUntil() != null && candidate.get().lockedUntil().isAfter(now)) {
            store.appendAudit(audit("ADMIN_LOGIN", candidate.get().userId(), normalized, "REJECTED", requestId, now));
            throw new AuthFailure("INVALID_CREDENTIALS");
        }
        if (!matches) {
            if (candidate.isPresent()) {
                AdminAuthStore.User user = candidate.get();
                int failed = Math.min(user.failedLoginCount() + 1, 5);
                Instant lockedUntil = failed >= 5 ? now.plus(Duration.ofMinutes(15)) : null;
                store.recordLoginFailure(user.userId(), failed, lockedUntil,
                        audit("ADMIN_LOGIN", user.userId(), normalized, "REJECTED", requestId, now));
            } else store.appendAudit(audit("ADMIN_LOGIN", null, normalized, "REJECTED", requestId, now));
            throw new AuthFailure("INVALID_CREDENTIALS");
        }
        AdminAuthStore.User user = candidate.orElseThrow();
        byte[] tokenBytes = new byte[32]; random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = now.plus(sessionTtl);
        store.recordLoginSuccess(user.userId(), audit("ADMIN_LOGIN", user.userId(), normalized, "SUCCEEDED", requestId, now));
        store.createSession(new AdminAuthStore.Session(UUID.randomUUID().toString(), user.userId(), sha256Hex(token), now, expiresAt),
                audit("ADMIN_SESSION_CREATED", user.userId(), normalized, "SUCCEEDED", requestId, now));
        return new LoginResult(token, expiresAt, new AdminAuthStore.AuthenticatedUser(user.userId(), user.username(), user.displayName(), user.roleCode()));
    }

    void recover(String suppliedToken, String username, char[] password, String requestId) {
        Instant now = clock.instant();
        String normalized = normalizeUsername(username);
        Optional<AdminAuthStore.User> candidate = store.findActiveUser(normalized);
        if (!recoveryAvailable() || suppliedToken == null || !MessageDigest.isEqual(recoveryDigest, sha256Bytes(suppliedToken)) || candidate.isEmpty()) {
            store.appendAudit(audit("ADMIN_PASSWORD_RECOVERY", candidate.map(AdminAuthStore.User::userId).orElse(null), normalized, "REJECTED", requestId, now));
            java.util.Arrays.fill(password, '\0');
            throw new AuthFailure("RECOVERY_UNAVAILABLE");
        }
        try {
            validatePassword(password, normalized);
            store.resetPassword(candidate.get().userId(), passwords.encode(new String(password)), now,
                    audit("ADMIN_PASSWORD_RECOVERY", candidate.get().userId(), normalized, "SUCCEEDED", requestId, now));
        } finally { java.util.Arrays.fill(password, '\0'); }
    }

    Optional<AdminAuthStore.AuthenticatedUser> authenticate(String token) {
        return token == null || token.isBlank() ? Optional.empty() : store.findSession(sha256Hex(token), clock.instant());
    }

    void logout(String token, String requestId) {
        if (token == null || token.isBlank()) return;
        Instant now = clock.instant();
        store.revokeSession(sha256Hex(token), now, audit("ADMIN_LOGOUT", null, null, "SUCCEEDED", requestId, now));
    }

    private static String normalizeUsername(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9._-]{3,31}")) throw new AuthFailure("INVALID_USERNAME");
        return normalized;
    }
    private static void validateDisplayName(String value) {
        if (value == null || value.trim().length() < 2 || value.trim().length() > 40) throw new AuthFailure("INVALID_DISPLAY_NAME");
    }
    private static void validatePassword(char[] value, String username) {
        if (value == null || value.length < 16 || value.length > 128
                || new String(value).getBytes(StandardCharsets.UTF_8).length > 72) throw new AuthFailure("WEAK_PASSWORD");
        String raw = new String(value);
        if (raw.toLowerCase(Locale.ROOT).contains(username) || raw.chars().distinct().count() < 8) throw new AuthFailure("WEAK_PASSWORD");
    }
    private static AdminAuthStore.Audit audit(String event, String userId, String username, String result, String requestId, Instant now) {
        return new AdminAuthStore.Audit(UUID.randomUUID().toString(), event, userId, username == null ? null : sha256Hex(username), result,
                requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.substring(0, Math.min(80, requestId.length())), now);
    }
    private static byte[] sha256Bytes(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static Instant parseInstant(String value) {
        try { return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value.trim()); }
        catch (RuntimeException ignored) { return Instant.EPOCH; }
    }
    private static String sha256Hex(String value) { return java.util.HexFormat.of().formatHex(sha256Bytes(value)); }

    record LoginResult(String token, Instant expiresAt, AdminAuthStore.AuthenticatedUser user) {}
    static final class AuthFailure extends RuntimeException { AuthFailure(String code) { super(code); } }
}
