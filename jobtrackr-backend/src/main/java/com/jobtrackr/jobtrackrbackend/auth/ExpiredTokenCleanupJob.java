package com.jobtrackr.jobtrackrbackend.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Nightly housekeeping: without it, every login and every refresh adds a row to refresh_tokens forever.
// Thin on purpose: the rule (what counts as deletable) lives in RefreshTokenService, where it is unit tested.
// Runs only when scheduling is on (common/config/SchedulingConfig); tests switch it off.
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "${jobtrackr.refresh-token-cleanup.cron}", zone = "${jobtrackr.refresh-token-cleanup.zone}")
    public void deleteExpiredTokens() {
        // Only a count is logged: no token hashes, no user ids
        int deleted = refreshTokenService.deleteExpiredTokens();
        log.info("Refresh-token cleanup deleted {} expired row(s)", deleted);
    }
}
