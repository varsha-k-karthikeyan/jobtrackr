package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.auth.dto.AuthResponse;
import com.jobtrackr.jobtrackrbackend.user.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Real HS256 signing and verification (the same Nimbus classes SecurityConfig uses), no Spring context.
// "Now" is pinned to the current second, because the decoder checks expiry against the real clock.
class TokenServiceTest {

    private static final String SECRET = "unit-test-signing-key-0123456789-abcdefgh";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final TokenService tokenService = new TokenService(
            new NimbusJwtEncoder(new ImmutableSecret<>(key(SECRET))),
            new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(14), false),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void issuesA15MinuteHs256Token_whoseSubjectIsTheUserId() {
        AuthResponse response = tokenService.issueAccessToken(user());

        Jwt jwt = decoder(SECRET).decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("varsha@example.com");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("jobtrackr");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void aTokenSignedWithAnotherKey_isRejected() {
        String token = tokenService.issueAccessToken(user()).accessToken();

        assertThatThrownBy(() -> decoder("another-key-that-is-also-32-chars-long!!").decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenWhosePayloadWasEdited_isRejected() {
        String[] parts = tokenService.issueAccessToken(user()).accessToken().split("\\.");
        // Swap in another user's id but keep the original signature
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"iss\":\"jobtrackr\"}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> decoder(SECRET).decode(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }

    private static User user() {
        User user = new User("varsha@example.com", "hash", "Varsha");
        user.setId(7L);
        return user;
    }

    private static JwtDecoder decoder(String secret) {
        return NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
