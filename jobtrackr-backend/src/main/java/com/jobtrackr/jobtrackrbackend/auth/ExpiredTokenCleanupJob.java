package com.jobtrackr.jobtrackrbackend.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Nightly housekeeping: without it, every login and refresh, and every unused "forgot password" link, would
// leave a row behind forever. Thin on purpose: what counts as deletable lives in RefreshTokenService and
// PasswordResetService, where it is unit tested.
// Runs only when scheduling is on (common/config/SchedulingConfig); tests switch it off.
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredTokenCleanupJob {

    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    @Scheduled(cron = "${jobtrackr.token-cleanup.cron}", zone = "${jobtrackr.token-cleanup.zone}")
    public void deleteExpiredTokens() {
        // Only counts are logged: no token hashes, no user ids
        int refreshTokens = refreshTokenService.deleteExpiredTokens();
        int resetLinks = passwordResetService.deleteExpiredTokens();
        log.info("Token cleanup deleted {} expired refresh token(s) and {} expired reset link(s)", refreshTokens, resetLinks);
    }
}
