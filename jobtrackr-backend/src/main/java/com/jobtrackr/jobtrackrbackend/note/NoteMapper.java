package com.jobtrackr.jobtrackrbackend.note;

import com.jobtrackr.jobtrackrbackend.application.Application;
import com.jobtrackr.jobtrackrbackend.note.dto.NoteRequest;
import com.jobtrackr.jobtrackrbackend.note.dto.NoteResponse;
import org.springframework.stereotype.Component;

// Entity <-> DTO conversion for notes
@Component
public class NoteMapper {

    public NoteResponse toResponse(Note note) {
        return new NoteResponse(note.getId(), note.getBody(), note.getCreatedAt());
    }

    public Note toEntity(NoteRequest request, Application application) {
        return new Note(application, request.body());
    }
}
