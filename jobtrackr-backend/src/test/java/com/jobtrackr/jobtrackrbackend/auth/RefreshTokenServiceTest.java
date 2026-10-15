package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Time is a fixed Clock, so expiry and the reuse grace period are tested exactly
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final Duration TTL = Duration.ofDays(14);

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("x".repeat(32), Duration.ofMinutes(15), TTL, false);
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        user = mock(User.class);
    }

    @Test
    void issue_storesOnlyTheHash_withTheConfiguredExpiry() {
        String token = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(token).hasSizeGreaterThanOrEqualTo(43); // 32 random bytes, base64url
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(token)).doesNotContain(token);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(saved.getValue().getRevokedAt()).isNull();
    }

    @Test
    void issue_producesADifferentTokenEveryTime() {
        assertThat(service.issue(user)).isNotEqualTo(service.issue(user));
    }

    @Test
    void rotate_validToken_revokesItAndIssuesANewOne() {
        RefreshToken stored = stored("old", NOW.plus(Duration.ofDays(1)), null);

        RefreshTokenService.Rotation rotation = service.rotate("old");

        assertThat(stored.getRevokedAt()).isEqualTo(NOW);
        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.refreshToken()).isNotEqualTo("old");
        verify(repository).save(any(RefreshToken.class)); // the replacement
    }

    @Test
    void rotate_unknownToken_isRejected() {
        when(repository.findByTokenHash(RefreshTokenService.hash("nope"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rotate_expiredToken_isRejectedAndNotRevokedAgain() {
        RefreshToken stored = stored("old", NOW.minusSeconds(1), null);

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(BadCredentialsException.class);
        assertThat(stored.getRevokedAt()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_tokenReusedLongAfterRotation_revokesEverySessionOfTheUser() {
        when(user.getId()).thenReturn(7L);
        stored("old", NOW.plus(Duration.ofDays(1)), NOW.minus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(BadCredentialsException.class);
        verify(repository).revokeAllActiveForUser(7L, NOW);
    }

    @Test
    void rotate_tokenReusedWithinTheGracePeriod_isRejectedWithoutRevokingOtherSessions() {
        // e.g. two tabs refreshing at the same moment with the same cookie
        stored("old", NOW.plus(Duration.ofDays(1)), NOW.minusSeconds(5));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(BadCredentialsException.class);
        verify(repository, never()).revokeAllActiveForUser(anyLong(), any());
    }

    @Test
    void revoke_marksAnActiveTokenRevoked() {
        RefreshToken stored = stored("old", NOW.plus(Duration.ofDays(1)), null);

        service.revoke("old");

        assertThat(stored.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void endAllSessions_deletesTheRowsInsteadOfRevokingThem() {
        when(repository.deleteAllForUser(7L)).thenReturn(2);

        assertThat(service.endAllSessions(7L)).isEqualTo(2);
        verify(repository, never()).revokeAllActiveForUser(anyLong(), any());
    }

    @Test
    void deleteExpiredTokens_deletesOnlyRowsExpiredMoreThanSevenDaysAgo() {
        when(repository.deleteExpiredBefore(any())).thenReturn(3);

        int deleted = service.deleteExpiredTokens();

        assertThat(deleted).isEqualTo(3);
        verify(repository).deleteExpiredBefore(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void hash_isAStableHexSha256() {
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    private RefreshToken stored(String token, Instant expiresAt, Instant revokedAt) {
        RefreshToken stored = new RefreshToken(user, RefreshTokenService.hash(token), NOW.minus(Duration.ofDays(1)), expiresAt);
        stored.setRevokedAt(revokedAt);
        when(repository.findByTokenHash(RefreshTokenService.hash(token))).thenReturn(Optional.of(stored));
        return stored;
    }
}
