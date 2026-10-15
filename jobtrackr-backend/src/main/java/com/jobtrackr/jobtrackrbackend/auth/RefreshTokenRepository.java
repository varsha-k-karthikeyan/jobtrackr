package com.jobtrackr.jobtrackrbackend.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // SELECT ... FOR UPDATE: two requests presenting the same token are serialised, so only one of them
    // can rotate it; the second then sees it as already revoked.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Used when a long-revoked token is presented again (likely stolen): ends every session of that user
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllActiveForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
