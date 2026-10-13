package com.jobtrackr.jobtrackrbackend.note;

import com.jobtrackr.jobtrackrbackend.application.Application;
import com.jobtrackr.jobtrackrbackend.application.ApplicationRepository;
import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import com.jobtrackr.jobtrackrbackend.common.exception.NotFoundException;
import com.jobtrackr.jobtrackrbackend.note.dto.NoteRequest;
import com.jobtrackr.jobtrackrbackend.note.dto.NoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Notes belong to an application; every operation first proves the caller owns that application
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final ApplicationRepository applicationRepository;
    private final NoteMapper noteMapper;
    private final CurrentUserProvider currentUser;

    @Transactional
    public NoteResponse addNote(Long applicationId, NoteRequest request) {
        // A note can only be added to an application the caller owns
        Application application = applicationRepository.findByIdAndUserId(applicationId, currentUser.getUserId())
                .orElseThrow(() -> new NotFoundException("Application " + applicationId + " not found"));
        Note note = noteMapper.toEntity(request, application);
        noteRepository.save(note);
        return noteMapper.toResponse(note);
    }

    @Transactional
    public void deleteNote(Long noteId) {
        Note note = noteRepository.findByIdAndApplicationUserId(noteId, currentUser.getUserId())
                .orElseThrow(() -> new NotFoundException("Note " + noteId + " not found"));
        noteRepository.delete(note);
    }
}
