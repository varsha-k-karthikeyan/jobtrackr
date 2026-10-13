package com.jobtrackr.jobtrackrbackend.note;

import com.jobtrackr.jobtrackrbackend.application.Application;
import com.jobtrackr.jobtrackrbackend.application.ApplicationRepository;
import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import com.jobtrackr.jobtrackrbackend.common.exception.NotFoundException;
import com.jobtrackr.jobtrackrbackend.note.dto.NoteRequest;
import com.jobtrackr.jobtrackrbackend.note.dto.NoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private NoteRepository noteRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private CurrentUserProvider currentUser;

    private NoteService service;

    @BeforeEach
    void setUp() {
        service = new NoteService(noteRepository, applicationRepository, new NoteMapper(), currentUser);
        when(currentUser.getUserId()).thenReturn(USER_ID);
    }

    @Test
    void addNote_savesNoteAgainstOwnedApplication() {
        Application application = mock(Application.class);
        when(applicationRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(application));

        NoteResponse response = service.addNote(5L, new NoteRequest("Phone screen booked"));

        ArgumentCaptor<Note> saved = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(saved.capture());
        assertThat(saved.getValue().getApplication()).isSameAs(application);
        assertThat(response.body()).isEqualTo("Phone screen booked");
    }

    @Test
    void addNote_unknownOrForeignApplication_throwsNotFound() {
        when(applicationRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addNote(99L, new NoteRequest("x")))
                .isInstanceOf(NotFoundException.class);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void deleteNote_deletesOwnedNote() {
        Note note = new Note(mock(Application.class), "old note");
        when(noteRepository.findByIdAndApplicationUserId(7L, USER_ID)).thenReturn(Optional.of(note));

        service.deleteNote(7L);

        verify(noteRepository).delete(note);
    }

    @Test
    void deleteNote_unknownOrForeignNote_throwsNotFound() {
        when(noteRepository.findByIdAndApplicationUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteNote(99L)).isInstanceOf(NotFoundException.class);
        verify(noteRepository, never()).delete(any(Note.class));
    }
}
