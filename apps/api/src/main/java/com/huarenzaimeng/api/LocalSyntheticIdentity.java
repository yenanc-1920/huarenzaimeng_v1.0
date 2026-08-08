package com.huarenzaimeng.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic LOCAL_SYNTHETIC identity; it is not a real authentication identity. */
public record LocalSyntheticIdentity(String projectSubjectRef, String sessionRef) {
    public static LocalSyntheticIdentity fromToken(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("test token is required");
        return fromDigest(digest(token));
    }

    public static LocalSyntheticIdentity fromDigest(byte[] tokenDigest) {
        String opaque = HexFormat.of().withUpperCase().formatHex(tokenDigest, 0, 12);
        return new LocalSyntheticIdentity("SYN-SUBJECT-" + opaque, "SYN-SESSION-" + opaque);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
