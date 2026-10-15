package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.auth.dto.AuthResponse;
import com.jobtrackr.jobtrackrbackend.auth.dto.LoginRequest;
import com.jobtrackr.jobtrackrbackend.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The only endpoints reachable without a token (see SecurityConfig.PUBLIC_PATHS).
// The access token travels in the JSON body; the refresh token only ever in the httpOnly cookie.
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    // Registering also logs the new user in: the response already contains an access token
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withSession(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withSession(HttpStatus.OK, authService.login(request));
    }

    // Called by the frontend when the access token has expired. No body: the browser attaches the cookie.
    // On failure the stale cookie is deleted as well, so the browser stops sending it.
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        try {
            return withSession(HttpStatus.OK, authService.refresh(refreshToken));
        } catch (BadCredentialsException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear())
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage()));
        }
    }

    // Revokes the refresh token on the server and deletes the cookie. Always 204, even without a cookie.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear()).build();
    }

    private ResponseEntity<AuthResponse> withSession(HttpStatus status, AuthTokens tokens) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(tokens.refreshToken()))
                .body(tokens.accessToken());
    }
}
