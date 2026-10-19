package com.jobtrackr.jobtrackrbackend.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Sends the reset email:
// - AFTER_COMMIT: only once the token is really saved; a rolled-back request sends nothing.
// - @Async: on another thread, so /auth/forgot-password returns just as fast for a registered address
//   (which sends an email) as for an unknown one (which does not). Timing does not reveal who has an account.
// A failure cannot reach the caller any more, so it is logged: user id and exception type only, never the
// address, the link or the token.
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetEmailListener {

    private final PasswordResetMailer mailer;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequested request) {
        try {
            mailer.send(request);
        } catch (Exception exception) {
            log.warn("Password-reset email for user {} could not be sent: {}", request.userId(), exception.getClass().getSimpleName());
        }
    }
}
