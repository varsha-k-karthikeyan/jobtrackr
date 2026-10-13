package com.jobtrackr.jobtrackrbackend.note.dto;

import jakarta.validation.constraints.NotBlank;

// Body of POST /applications/{id}/notes
public record NoteRequest(@NotBlank String body) {
}
