package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

// Issues, rotates and revokes refresh tokens. Tokens are random and opaque (not JWTs): the database is the
// source of truth, so a token can be revoked at any moment, which a self-contained JWT cannot offer.
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    /**
     * A token revoked less than this long ago is treated as a race, not theft: two tabs (or two requests)
     * refreshing with the same cookie at the same moment. Older reuse revokes all of the user's tokens.
     */
    static final Duration REUSE_GRACE_PERIOD = Duration.ofSeconds(30);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    /** The result of a successful rotation: who the token belonged to, and their new token. */
    public record Rotation(User user, String refreshToken) {
    }

    /** Creates a token for the user and stores its hash. Returns the raw token, which is never stored. */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        refreshTokenRepository.save(new RefreshToken(user, hash(token), now, now.plus(jwtProperties.refreshTokenTtl())));
        return token;
    }

    /**
     * Exchanges a valid token for a new one and revokes the old one. Every failure is the same 401.
     * noRollbackFor: when reuse is detected, the "revoke everything" update must be committed even though
     * the method then throws.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Rotation rotate(String token) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(token)).orElseThrow(RefreshTokenService::invalid);
        Instant now = clock.instant();

        if (current.getRevokedAt() != null) {
            if (current.getRevokedAt().isBefore(now.minus(REUSE_GRACE_PERIOD))) {
                // An old, already-used token came back: someone else may hold a copy. End all sessions.
                refreshTokenRepository.revokeAllActiveForUser(current.getUser().getId(), now);
            }
            throw invalid();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw invalid();
        }

        current.setRevokedAt(now);
        return new Rotation(current.getUser(), issue(current.getUser()));
    }

    /** Logout: the token stops working immediately. Unknown or already revoked tokens are ignored. */
    @Transactional
    public void revoke(String token) {
        refreshTokenRepository.findByTokenHash(hash(token))
                .filter(stored -> stored.getRevokedAt() == null)
                .ifPresent(stored -> stored.setRevokedAt(clock.instant()));
    }

    /** Hex SHA-256. Deterministic (unlike BCrypt), so the row can be found by it. */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required on every JVM", impossible);
        }
    }

    private static BadCredentialsException invalid() {
        return new BadCredentialsException("Your session has expired. Please log in again.");
    }
}
