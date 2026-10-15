package com.jobtrackr.jobtrackrbackend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Builds the Set-Cookie header that carries the refresh token.
//  - HttpOnly: JavaScript can never read it, so an XSS bug cannot steal the long-lived token
//  - SameSite=Strict: the browser does not send it on requests started by another site (CSRF protection).
//    localhost:4200 and localhost:8080 are the same site (ports do not count), so it works locally.
//  - Path=/api/v1/auth: sent only to refresh/logout (and login/register), never with ordinary API calls
//  - Secure: from configuration, because browsers only send Secure cookies over HTTPS
@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {

    public static final String NAME = "jobtrackr_refresh";
    private static final String PATH = "/api/v1/auth";

    private final JwtProperties jwtProperties;

    public String create(String refreshToken) {
        return build(refreshToken, jwtProperties.refreshTokenTtl());
    }

    /** Max-Age=0 tells the browser to delete the cookie. */
    public String clear() {
        return build("", Duration.ZERO);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(jwtProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
