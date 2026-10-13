package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.auth.dto.AuthResponse;
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

// Registration and login. Knows nothing about HTTP or JWT internals: it checks credentials and asks
// TokenService for a token.
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        // Only the BCrypt hash is stored; the plain password never reaches the database
        User user = userRepository.save(
                new User(email, passwordEncoder.encode(request.password()), request.fullName()));
        return tokenService.issueAccessToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Same error for "no such email" and "wrong password", so the response does not reveal which emails exist
        User user = userRepository.findByEmail(normalize(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return tokenService.issueAccessToken(user);
    }

    // Emails are stored lower-case so Demo@x.com and demo@x.com are the same account
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
