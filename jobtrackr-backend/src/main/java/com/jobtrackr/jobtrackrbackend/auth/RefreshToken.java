package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// One issued refresh token. Maps "refresh_tokens" (V5). Holds only the SHA-256 hash of the token;
// the token itself is sent to the browser once, in an httpOnly cookie, and never stored.
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    // Set from the injected Clock (not @CreationTimestamp), so tests control time
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // null while the token is usable
    @Column(name = "revoked_at")
    private Instant revokedAt;

    public RefreshToken(User user, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
