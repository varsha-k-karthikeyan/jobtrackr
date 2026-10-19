package com.jobtrackr.jobtrackrbackend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Random bearer secrets that the database knows only by their hash: refresh tokens and password-reset links.
// A fast SHA-256 is right here (unlike passwords, which need BCrypt): 256 random bits leave nothing to
// brute-force, and a deterministic hash lets the row be looked up by it.
final class OpaqueTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits

    private OpaqueTokens() {
    }

    /** 43 URL-safe characters: fits in a cookie or a link without any escaping. */
    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hex SHA-256: what is stored and looked up, never the token itself. */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required on every JVM", impossible);
        }
    }
}
