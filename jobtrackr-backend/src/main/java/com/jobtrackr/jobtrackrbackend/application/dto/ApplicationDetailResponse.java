package com.jobtrackr.jobtrackrbackend.application.dto;

import com.jobtrackr.jobtrackrbackend.note.dto.NoteResponse;

import java.util.List;

// GET /applications/{id}: the card plus its notes (newest first)
public record ApplicationDetailResponse(
        ApplicationResponse application,
        List<NoteResponse> notes
) {
}
