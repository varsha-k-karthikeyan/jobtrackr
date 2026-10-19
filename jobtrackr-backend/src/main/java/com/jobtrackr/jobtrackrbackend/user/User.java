package com.jobtrackr.jobtrackrbackend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

// An account. Maps the "users" table ("user" is a reserved word in PostgreSQL, hence the plural).
// email is stored lower-case (AuthService normalises it). passwordHash is a BCrypt hash and must never
// be copied into a response DTO.
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA needs a no-arg constructor; protected keeps it out of app code
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // BIGSERIAL: the database assigns the id
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", length = 120)
    private String fullName;

    // The user's switch for all reminder emails (V12); true for new accounts, like the column default
    @Column(name = "email_reminders_enabled", nullable = false)
    private boolean emailRemindersEnabled = true;

    // Day the morning reminder digest was handled (V11); written only by a bulk update in UserRepository
    @Column(name = "digest_sent_on")
    private LocalDate digestSentOn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }
}
