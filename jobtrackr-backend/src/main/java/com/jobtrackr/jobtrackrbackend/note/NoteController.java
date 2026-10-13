package com.jobtrackr.jobtrackrbackend.note;

import com.jobtrackr.jobtrackrbackend.note.dto.NoteRequest;
import com.jobtrackr.jobtrackrbackend.note.dto.NoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Notes are created under their application but deleted by their own id
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping("/applications/{applicationId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse addNote(@PathVariable Long applicationId, @Valid @RequestBody NoteRequest request) {
        return noteService.addNote(applicationId, request);
    }

    @DeleteMapping("/notes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNote(@PathVariable Long id) {
        noteService.deleteNote(id);
    }
}
