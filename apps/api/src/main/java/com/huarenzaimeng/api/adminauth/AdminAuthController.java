package com.huarenzaimeng.api.adminauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/admin-auth/v1")
final class AdminAuthController {
    static final String COOKIE = "HZ_ADMIN_SESSION";
    private final AdminAuthService auth;
    AdminAuthController(AdminAuthService auth) { this.auth = auth; }

    @GetMapping("/initialization") Map<String, Object> initialization() {
        return Map.of("status", auth.initializationAvailable() ? "AVAILABLE" : "CLOSED");
    }

    @GetMapping("/recovery") Map<String, Object> recovery() {
        return Map.of("status", auth.recoveryAvailable() ? "AVAILABLE" : "CLOSED");
    }

    @PostMapping("/bootstrap") ResponseEntity<?> bootstrap(@RequestHeader("X-Admin-Bootstrap-Token") String token,
                                                            @Valid @RequestBody BootstrapRequest body,
                                                            HttpServletRequest request) {
        auth.bootstrap(token, body.username(), body.displayName(), body.password().toCharArray(), request.getHeader("X-Request-Id"));
        return ResponseEntity.status(201).body(Map.of("status", "CREATED"));
    }

    @PostMapping("/login") Map<String, Object> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        AdminAuthService.LoginResult result = auth.login(body.username(), body.password().toCharArray(), request.getHeader("X-Request-Id"));
        Cookie cookie = new Cookie(COOKIE, result.token());
        cookie.setHttpOnly(true); cookie.setSecure(true); cookie.setPath("/");
        cookie.setAttribute("SameSite", "Strict");
        cookie.setMaxAge((int) Duration.between(java.time.Instant.now(), result.expiresAt()).toSeconds());
        response.addCookie(cookie);
        return Map.of("status", "AUTHENTICATED", "user", Map.of("displayName", result.user().displayName(), "role", result.user().roleCode()));
    }

    @PostMapping("/recovery") Map<String, Object> recovery(@RequestHeader("X-Admin-Recovery-Token") String token,
                                                           @Valid @RequestBody RecoveryRequest body,
                                                           HttpServletRequest request) {
        auth.recover(token, body.username(), body.password().toCharArray(), request.getHeader("X-Request-Id"));
        return Map.of("status", "PASSWORD_RESET");
    }

    @PostMapping("/logout") ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String token,
                                                         HttpServletRequest request, HttpServletResponse response) {
        auth.logout(token, request.getHeader("X-Request-Id"));
        Cookie cookie = new Cookie(COOKIE, ""); cookie.setHttpOnly(true); cookie.setSecure(true); cookie.setPath("/");
        cookie.setAttribute("SameSite", "Strict"); cookie.setMaxAge(0); response.addCookie(cookie);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AdminAuthService.AuthFailure.class) ResponseEntity<?> rejected(AdminAuthService.AuthFailure error) {
        return ResponseEntity.status(error.getMessage().equals("INVALID_CREDENTIALS") ? 401 : 403)
                .body(Map.of("status", "REJECTED", "code", error.getMessage()));
    }

    record BootstrapRequest(@NotBlank String username, @NotBlank String displayName, @NotBlank String password) {}
    record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    record RecoveryRequest(@NotBlank String username, @NotBlank String password) {}
}
