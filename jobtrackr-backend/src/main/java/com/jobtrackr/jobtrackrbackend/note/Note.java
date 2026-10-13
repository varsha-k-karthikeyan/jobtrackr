package com.jobtrackr.jobtrackrbackend.note;

import com.jobtrackr.jobtrackrbackend.application.Application;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

// A free-text note on one application. Maps the "notes" table.
// Ownership is derived through the parent: note -> application -> user. Deleting an application deletes
// its notes in the database (ON DELETE CASCADE), so no JPA cascade is configured.
@Entity
@Table(name = "notes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private Application application;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Note(Application application, String body) {
        this.application = application;
        this.body = body;
    }
}
