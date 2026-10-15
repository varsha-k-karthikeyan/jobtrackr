package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.auth.dto.AuthResponse;
import com.jobtrackr.jobtrackrbackend.auth.dto.ChangePasswordRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Password change for the signed-in user. Under /me, not /auth: /auth/** is public, and this endpoint needs
// a valid access token. It lives in the auth package because it needs BCrypt and the session machinery.
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class PasswordController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    // 200 with a NEW access token and a new refresh cookie: every earlier session was just revoked, this
    // browser's included, so without them the caller would be logged out at its next refresh.
    // The browser only stores a Set-Cookie from another origin when the call is made with credentials.
    @PutMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AuthTokens tokens = authService.changePassword(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(tokens.refreshToken()))
                .body(tokens.accessToken());
    }
}
