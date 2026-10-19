package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.auth.dto.ResetPasswordRequest;
import com.jobtrackr.jobtrackrbackend.common.exception.BadRequestException;
import com.jobtrackr.jobtrackrbackend.common.exception.ForbiddenException;
import com.jobtrackr.jobtrackrbackend.user.DemoAccount;
import com.jobtrackr.jobtrackrbackend.user.User;
import com.jobtrackr.jobtrackrbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Pure unit test: repositories and the event publisher are mocks, time is a fixed Clock
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository resetTokenRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private ApplicationEventPublisher events;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        PasswordResetProperties properties = new PasswordResetProperties("http://localhost:4200", "JobTrackr <test@x>",
                TTL, Duration.ofMinutes(1));
        service = new PasswordResetService(userRepository, resetTokenRepository, refreshTokenService, passwordEncoder,
                properties, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User user(String email) {
        User user = new User(email, "old-hash", "Varsha");
        user.setId(7L);
        return user;
    }

    // ---------- requestReset ----------

    @Test
    void requestReset_knownEmail_storesOnlyTheHash_andAnnouncesALinkToTheConfiguredApp() {
        User user = user("varsha@example.com");
        when(userRepository.findByEmail("varsha@example.com")).thenReturn(Optional.of(user));

        service.requestReset("  Varsha@Example.com ");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).save(saved.capture());
        ArgumentCaptor<PasswordResetRequested> event = ArgumentCaptor.forClass(PasswordResetRequested.class);
        verify(events).publishEvent(event.capture());

        String link = event.getValue().resetLink();
        assertThat(link).startsWith("http://localhost:4200/reset-password?token=");
        String token = link.substring(link.indexOf("token=") + 6);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(OpaqueTokens.hash(token)).isNotEqualTo(token);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(event.getValue().email()).isEqualTo("varsha@example.com");
    }

    @Test
    void requestReset_invalidatesEarlierLinks() {
        when(userRepository.findByEmail("varsha@example.com")).thenReturn(Optional.of(user("varsha@example.com")));

        service.requestReset("varsha@example.com");

        verify(resetTokenRepository).deleteAllForUser(7L);
    }

    @Test
    void requestReset_unknownEmail_doesNothingVisible() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@example.com");

        verifyNoInteractions(resetTokenRepository, events);
    }

    @Test
    void requestReset_demoAccount_doesNothing() {
        when(userRepository.findByEmail(DemoAccount.EMAIL)).thenReturn(Optional.of(user(DemoAccount.EMAIL)));

        service.requestReset(DemoAccount.EMAIL);

        verifyNoInteractions(resetTokenRepository, events);
    }

    @Test
    void requestReset_withinTheCooldown_sendsNoSecondEmail() {
        when(userRepository.findByEmail("varsha@example.com")).thenReturn(Optional.of(user("varsha@example.com")));
        when(resetTokenRepository.existsByUserIdAndCreatedAtAfter(7L, NOW.minus(Duration.ofMinutes(1)))).thenReturn(true);

        service.requestReset("varsha@example.com");

        verify(resetTokenRepository, never()).save(any());
        verifyNoInteractions(events);
    }

    // ---------- resetPassword ----------

    @Test
    void resetPassword_validLink_setsTheNewPassword_usesUpTheLink_andEndsEverySession() {
        User user = user("varsha@example.com");
        when(resetTokenRepository.findByTokenHash(OpaqueTokens.hash("the-token")))
                .thenReturn(Optional.of(new PasswordResetToken(user, "hash", NOW.minus(Duration.ofMinutes(5)), NOW.plus(TTL))));

        service.resetPassword(new ResetPasswordRequest("the-token", "N3w@Secret"));

        assertThat(passwordEncoder.matches("N3w@Secret", user.getPasswordHash())).isTrue();
        verify(resetTokenRepository).deleteAllForUser(7L);
        verify(refreshTokenService).endAllSessions(7L);
    }

    @Test
    void resetPassword_expiredLink_is400AndChangesNothing() {
        User user = user("varsha@example.com");
        when(resetTokenRepository.findByTokenHash(OpaqueTokens.hash("old-token")))
                .thenReturn(Optional.of(new PasswordResetToken(user, "hash", NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofMinutes(1)))));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("old-token", "N3w@Secret")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PasswordResetService.INVALID_LINK);
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(refreshTokenService, never()).endAllSessions(anyLong());
    }

    @Test
    void resetPassword_unknownOrUsedLink_is400() {
        when(resetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("made-up", "N3w@Secret")))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void resetPassword_demoAccount_isForbidden() {
        User demo = user(DemoAccount.EMAIL);
        when(resetTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(new PasswordResetToken(demo, "hash", NOW, NOW.plus(TTL))));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("token", "N3w@Secret")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(demo.getPasswordHash()).isEqualTo("old-hash");
    }

    // ---------- cleanup ----------

    @Test
    void deleteExpiredTokens_deletesEverythingPastItsExpiry() {
        when(resetTokenRepository.deleteExpiredBefore(NOW)).thenReturn(3);

        assertThat(service.deleteExpiredTokens()).isEqualTo(3);
    }
}
