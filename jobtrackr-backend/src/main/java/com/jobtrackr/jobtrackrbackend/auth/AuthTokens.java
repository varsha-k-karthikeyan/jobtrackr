package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.auth.dto.AuthResponse;

// What AuthService hands back after login, registration or refresh. Internal, not a response DTO:
// the controller puts accessToken in the JSON body and refreshToken in an httpOnly cookie.
public record AuthTokens(AuthResponse accessToken, String refreshToken) {
}
