package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.auth.dto.LoginRequest;
import com.jobtrackr.jobtrackrbackend.auth.dto.RegisterRequest;
import com.jobtrackr.jobtrackrbackend.common.exception.ConflictException;
import com.jobtrackr.jobtrackrbackend.user.User;
import com.jobtrackr.jobtrackrbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

// Registration, login, refresh and logout. Knows nothing about HTTP: it returns tokens, and the controller
// decides that the access token goes in the body and the refresh token in a cookie.
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthTokens register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        // Only the BCrypt hash is stored; the plain password never reaches the database
        User user = userRepository.save(
                new User(email, passwordEncoder.encode(request.password()), request.fullName()));
        return startSession(user);
    }

    // Not read-only any more: a successful login stores a new refresh token
    @Transactional
    public AuthTokens login(LoginRequest request) {
        // Same error for "no such email" and "wrong password", so the response does not reveal which emails exist
        User user = userRepository.findByEmail(normalize(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return startSession(user);
    }

    /**
     * New access token + rotated refresh token. noRollbackFor: if the refresh token turns out to be a reused
     * one, RefreshTokenService revokes the user's other tokens and then throws; that revocation must commit.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("Your session has expired. Please log in again.");
        }
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        return new AuthTokens(tokenService.issueAccessToken(rotation.user()), rotation.refreshToken());
    }

    /** Ends this session on the server. Missing or unknown tokens are fine: logging out always succeeds. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
    }

    private AuthTokens startSession(User user) {
        return new AuthTokens(tokenService.issueAccessToken(user), refreshTokenService.issue(user));
    }

    // Emails are stored lower-case so Demo@x.com and demo@x.com are the same account
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
