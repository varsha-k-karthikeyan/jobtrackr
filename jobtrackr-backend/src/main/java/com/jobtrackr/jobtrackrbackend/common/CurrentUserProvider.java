package com.jobtrackr.jobtrackrbackend.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

// The only place that knows who the caller is. Services ask this bean and never read the token themselves.
@Component
public class CurrentUserProvider {

    // The Bearer token filter has already verified the JWT and stored it in the SecurityContext
    // for this request; the token's subject ("sub") is the user id set at login.
    public Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return Long.valueOf(jwt.getToken().getSubject());
        }
        throw new IllegalStateException("No authenticated user in the security context");
    }
}
