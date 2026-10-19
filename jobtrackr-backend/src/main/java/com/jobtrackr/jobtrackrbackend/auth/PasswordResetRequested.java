package com.jobtrackr.jobtrackrbackend.auth;

import java.time.Duration;

// Published by PasswordResetService once a reset token has been created. PasswordResetEmailListener turns
// it into an email, but only after the transaction commits, so a link is never sent for a token that was
// rolled back. Carries the raw link in memory only; it is never logged or stored.
public record PasswordResetRequested(
        Long userId,
        String email,
        String recipientName,
        String resetLink,
        Duration validFor
) {
}
