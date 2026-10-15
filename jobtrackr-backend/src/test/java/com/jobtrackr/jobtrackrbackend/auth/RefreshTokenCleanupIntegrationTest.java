package com.jobtrackr.jobtrackrbackend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The real DELETE against PostgreSQL, called the way the nightly job calls it. The DELETE also sees other
// users' rows, so the assertions only look at this test's own user; the rollback undoes all of it.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenCleanupIntegrationTest {

    @Autowired
    private RefreshTokenCleanupJob cleanupJob;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deletesLongExpiredRows_butKeepsRecentlyExpiredAndRevokedActiveOnes() {
        long userId = jdbc.queryForObject("insert into users (email, password_hash, full_name) values (?, 'x', 'Cleanup Test') returning id",
                Long.class, "cleanup-" + UUID.randomUUID() + "@test.dev");
        Instant now = Instant.now();
        insertToken(userId, "expired-long-ago", now.minus(Duration.ofDays(10)), null);
        insertToken(userId, "expired-recently", now.minus(Duration.ofDays(2)), null);
        insertToken(userId, "active", now.plus(Duration.ofDays(5)), null);
        // Already used but not expired: the evidence reuse detection needs, so it must survive
        insertToken(userId, "revoked-not-expired", now.plus(Duration.ofDays(5)), now.minus(Duration.ofHours(1)));

        cleanupJob.deleteExpiredTokens();

        assertThat(jdbc.queryForList("select token_hash from refresh_tokens where user_id = ? order by token_hash", String.class, userId))
                .containsExactly("active", "expired-recently", "revoked-not-expired");
    }

    // token_hash holds a readable label instead of a real hash: only expires_at matters to the cleanup
    private void insertToken(long userId, String label, Instant expiresAt, Instant revokedAt) {
        jdbc.update("insert into refresh_tokens (user_id, token_hash, expires_at, revoked_at) values (?, ?, ?, ?)",
                userId, label, Timestamp.from(expiresAt), revokedAt == null ? null : Timestamp.from(revokedAt));
    }
}
