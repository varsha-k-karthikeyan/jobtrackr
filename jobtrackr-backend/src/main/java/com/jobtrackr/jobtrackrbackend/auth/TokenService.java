package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.auth.dto.AuthResponse;
import com.jobtrackr.jobtrackrbackend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

// Builds and signs the JWT access token. The matching verification is the JwtDecoder bean in SecurityConfig.
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String ISSUER = "jobtrackr";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public AuthResponse issueAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                // The subject is the user id; CurrentUserProvider reads it back on every request
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", jwtProperties.accessTokenTtl().toSeconds());
    }
}
