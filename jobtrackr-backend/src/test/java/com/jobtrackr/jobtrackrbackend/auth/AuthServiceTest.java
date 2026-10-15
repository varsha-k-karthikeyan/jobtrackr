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

    private static final AuthResponse TOKEN = new AuthResponse("signed.jwt.value", "Bearer", 900);

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenService tokenService;

    // Real BCrypt at the lowest cost factor: fast, but still proves hashing and matching work
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordEncoder, tokenService);
    }

    @Test
    void register_storesLowerCasedEmailAndBcryptHash_andReturnsToken() {
        when(userRepository.existsByEmail("varsha@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issueAccessToken(any(User.class))).thenReturn(TOKEN);

        AuthResponse response = service.register(new RegisterRequest(" Varsha@Example.com ", "S3cret@Pass", "Varsha"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("varsha@example.com");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("S3cret@Pass").startsWith("$2");
        assertThat(passwordEncoder.matches("S3cret@Pass", saved.getValue().getPasswordHash())).isTrue();
        assertThat(response).isEqualTo(TOKEN);
    }

    @Test
    void register_existingEmail_throwsConflictAndSavesNothing() {
        when(userRepository.existsByEmail("varsha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("varsha@example.com", "S3cret@Pass", null)))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(tokenService);
    }

    @Test
    void login_correctPassword_returnsToken() {
        User user = new User("varsha@example.com", passwordEncoder.encode("S3cret@Pass"), "Varsha");
        when(userRepository.findByEmail("varsha@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issueAccessToken(user)).thenReturn(TOKEN);

        assertThat(service.login(new LoginRequest("VARSHA@example.com", "S3cret@Pass"))).isEqualTo(TOKEN);
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        User user = new User("varsha@example.com", passwordEncoder.encode("S3cret@Pass"), "Varsha");
        when(userRepository.findByEmail("varsha@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("varsha@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(tokenService);
    }

    @Test
    void login_unknownEmail_throwsTheSameBadCredentials() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "S3cret@Pass")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }
}
