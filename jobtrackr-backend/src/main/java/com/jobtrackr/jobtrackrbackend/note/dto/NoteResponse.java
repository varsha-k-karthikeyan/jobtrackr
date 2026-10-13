package com.jobtrackr.jobtrackrbackend.note.dto;

import java.time.Instant;

public record NoteResponse(Long id, String body, Instant createdAt) {
}
