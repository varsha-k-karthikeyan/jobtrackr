package com.jobtrackr.jobtrackrbackend.note;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Notes have no user_id column; ownership is always checked through the parent application
public interface NoteRepository extends JpaRepository<Note, Long> {

    // Newest first, for the application detail view
    List<Note> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    // Ownership check through the parent: note -> application -> user
    Optional<Note> findByIdAndApplicationUserId(Long id, Long userId);
}
