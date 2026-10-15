package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.auth.dto.AuthResponse;
import com.jobtrackr.jobtrackrbackend.auth.dto.LoginRequest;
import com.jobtrackr.jobtrackrbackend.auth.dto.RegisterRequest;
import com.jobtrackr.jobtrackrbackend.common.exception.ConflictException;
import com.jobtrackr.jobtrackrbackend.user.User;
import com.jobtrackr.jobtrackrbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final AuthResponse ACCESS = new AuthResponse("signed.jwt.value", "Bearer", 900);
    private static final String REFRESH = "opaque-refresh-token";

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private RefreshTokenService refreshTokenService;

    // Real BCrypt at the lowest cost factor: fast, but still proves hashing and matching work
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordEncoder, tokenService, refreshTokenService);
    }

    @Test
    void register_storesLowerCasedEmailAndBcryptHash_andStartsASession() {
        when(userRepository.existsByEmail("varsha@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issueAccessToken(any(User.class))).thenReturn(ACCESS);
        when(refreshTokenService.issue(any(User.class))).thenReturn(REFRESH);

        AuthTokens tokens = service.register(new RegisterRequest(" Varsha@Example.com ", "S3cret@Pass", "Varsha"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("varsha@example.com");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("S3cret@Pass").startsWith("$2");
        assertThat(passwordEncoder.matches("S3cret@Pass", saved.getValue().getPasswordHash())).isTrue();
        assertThat(tokens).isEqualTo(new AuthTokens(ACCESS, REFRESH));
    }

    @Test
    void register_existingEmail_throwsConflictAndIssuesNothing() {
        when(userRepository.existsByEmail("varsha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("varsha@example.com", "S3cret@Pass", null)))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(tokenService, refreshTokenService);
    }

    @Test
    void login_correctPassword_returnsAccessAndRefreshToken() {
        User user = new User("varsha@example.com", passwordEncoder.encode("S3cret@Pass"), "Varsha");
        when(userRepository.findByEmail("varsha@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issueAccessToken(user)).thenReturn(ACCESS);
        when(refreshTokenService.issue(user)).thenReturn(REFRESH);

        assertThat(service.login(new LoginRequest("VARSHA@example.com", "S3cret@Pass")))
                .isEqualTo(new AuthTokens(ACCESS, REFRESH));
    }

    @Test
    void login_wrongPassword_throwsBadCredentialsAndIssuesNothing() {
        User user = new User("varsha@example.com", passwordEncoder.encode("S3cret@Pass"), "Varsha");
        when(userRepository.findByEmail("varsha@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("varsha@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(tokenService, refreshTokenService);
    }

    @Test
    void login_unknownEmail_throwsTheSameBadCredentials() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "S3cret@Pass")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void refresh_rotatesTheTokenAndIssuesANewAccessToken() {
        User user = new User("varsha@example.com", "hash", "Varsha");
        when(refreshTokenService.rotate(REFRESH)).thenReturn(new RefreshTokenService.Rotation(user, "next-token"));
        when(tokenService.issueAccessToken(user)).thenReturn(ACCESS);

        assertThat(service.refresh(REFRESH)).isEqualTo(new AuthTokens(ACCESS, "next-token"));
    }

    @Test
    void refresh_withoutCookie_throwsBadCredentials() {
        assertThatThrownBy(() -> service.refresh(null)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.refresh(" ")).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(refreshTokenService, tokenService);
    }

    @Test
    void logout_revokesTheToken_andIgnoresAMissingOne() {
        service.logout(REFRESH);
        verify(refreshTokenService).revoke(REFRESH);

        service.logout(null);
        verify(refreshTokenService, never()).revoke(null);
    }
}
