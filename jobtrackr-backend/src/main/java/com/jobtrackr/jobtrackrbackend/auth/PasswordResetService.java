package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.auth.dto.ResetPasswordRequest;
import com.jobtrackr.jobtrackrbackend.common.exception.BadRequestException;
import com.jobtrackr.jobtrackrbackend.common.exception.ForbiddenException;
import com.jobtrackr.jobtrackrbackend.user.DemoAccount;
import com.jobtrackr.jobtrackrbackend.user.User;
import com.jobtrackr.jobtrackrbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

// "Forgot password": emails a single-use, short-lived link, then sets a new password from it.
// Nothing here reveals whether an email address is registered: requestReset behaves the same for every
// address as far as the caller can tell, and the email goes out after the request has already returned.
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final String INVALID_LINK = "This reset link is invalid or has expired. Request a new one.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Creates a link and asks for it to be emailed, unless the address is unknown, belongs to the public
     * demo account, or was sent a link within the cooldown. In all of those cases it silently does nothing,
     * so the caller gets the same answer either way.
     */
    @Transactional
    public void requestReset(String email) {
        Optional<User> found = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        if (found.isEmpty() || DemoAccount.isDemo(found.get())) {
            return;
        }
        User user = found.get();
        Instant now = clock.instant();
        if (resetTokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), now.minus(properties.resendCooldown()))) {
            return;
        }
        // Only the newest link works: requesting again invalidates every earlier one
        resetTokenRepository.deleteAllForUser(user.getId());
        String token = OpaqueTokens.generate();
        resetTokenRepository.save(new PasswordResetToken(user, OpaqueTokens.hash(token), now, now.plus(properties.tokenTtl())));
        events.publishEvent(new PasswordResetRequested(user.getId(), user.getEmail(), user.getFullName(),
                resetLink(token), properties.tokenTtl()));
    }

    /**
     * Sets the new password if the link is valid, then deletes it (single use) and ends every session of
     * the user, so whoever might have been signed in with the old password is out. The user then logs in.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(OpaqueTokens.hash(request.token()))
                .filter(found -> found.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new BadRequestException(INVALID_LINK));
        User user = resetToken.getUser();
        if (DemoAccount.isDemo(user)) {
            // Never issued for the demo account (see requestReset); refused here as well, in case one exists
            throw new ForbiddenException("The demo account's password can't be changed.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        resetTokenRepository.deleteAllForUser(user.getId());
        refreshTokenService.endAllSessions(user.getId());
    }

    /** Called by the nightly cleanup job. Unused links are worthless once expired, so no retention margin. */
    @Transactional
    public int deleteExpiredTokens() {
        return resetTokenRepository.deleteExpiredBefore(clock.instant());
    }

    // The token is URL-safe Base64, so it needs no escaping in a query string
    private String resetLink(String token) {
        String base = properties.appUrl().endsWith("/") ? properties.appUrl() : properties.appUrl() + "/";
        return base + "reset-password?token=" + token;
    }
}
