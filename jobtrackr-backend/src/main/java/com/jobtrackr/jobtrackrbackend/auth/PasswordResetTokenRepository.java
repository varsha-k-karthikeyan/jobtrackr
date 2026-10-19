package com.jobtrackr.jobtrackrbackend.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

// Queries take the user id explicitly: the reset flow runs before anyone is signed in, so there is no
// CurrentUserProvider; the user is identified by the email (request) or the token (reset) instead.
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // SELECT ... FOR UPDATE: two clicks on the same link are serialised, so only one can use it
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Resend cooldown: was a link created for this user after the given instant?
    boolean existsByUserIdAndCreatedAtAfter(Long userId, Instant after);

    // A new link replaces any earlier one, and a successful reset removes them all
    @Modifying
    @Query("delete from PasswordResetToken t where t.user.id = :userId")
    int deleteAllForUser(@Param("userId") Long userId);

    // Housekeeping for ExpiredTokenCleanupJob (no user: see the note on RefreshTokenRepository)
    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
