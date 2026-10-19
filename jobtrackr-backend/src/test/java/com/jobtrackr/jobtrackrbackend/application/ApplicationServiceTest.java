package com.jobtrackr.jobtrackrbackend.application;

import com.jobtrackr.jobtrackrbackend.application.dto.ApplicationDetailResponse;
import com.jobtrackr.jobtrackrbackend.application.dto.ApplicationListFilter;
import com.jobtrackr.jobtrackrbackend.application.dto.ApplicationResponse;
import com.jobtrackr.jobtrackrbackend.application.dto.CreateApplicationRequest;
import com.jobtrackr.jobtrackrbackend.application.dto.MoveApplicationRequest;
import com.jobtrackr.jobtrackrbackend.application.dto.UpdateApplicationRequest;
import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import com.jobtrackr.jobtrackrbackend.common.PageResponse;
import com.jobtrackr.jobtrackrbackend.common.exception.NotFoundException;
import com.jobtrackr.jobtrackrbackend.note.Note;
import com.jobtrackr.jobtrackrbackend.note.NoteMapper;
import com.jobtrackr.jobtrackrbackend.note.NoteRepository;
import com.jobtrackr.jobtrackrbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.jobtrackr.jobtrackrbackend.application.ApplicationStatus.APPLIED;
import static com.jobtrackr.jobtrackrbackend.application.ApplicationStatus.INTERVIEW;
import static com.jobtrackr.jobtrackrbackend.application.ApplicationStatus.OFFER;
import static com.jobtrackr.jobtrackrbackend.application.ApplicationStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// Pure unit test: repositories are mocked, no Spring context and no database
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final int END = Integer.MAX_VALUE;

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserProvider currentUser;

    private ApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applicationRepository, noteRepository, userRepository,
                new ApplicationMapper(), new NoteMapper(), currentUser);
        // lenient: a few tests fail before the service asks who the caller is
        lenient().when(currentUser.getUserId()).thenReturn(USER_ID);
    }

    // ---------- board / list / get ----------

    @Test
    void getBoard_groupsByStatusAndIncludesEmptyColumns() {
        when(applicationRepository.findByUserIdOrderByStatusAscPositionAsc(USER_ID)).thenReturn(List.of(
                card(1L, APPLIED, 0), card(2L, APPLIED, 1), card(3L, OFFER, 0)));

        Map<ApplicationStatus, List<ApplicationResponse>> board = service.getBoard();

        assertThat(board).containsOnlyKeys(APPLIED, INTERVIEW, OFFER, REJECTED);
        assertThat(board.get(APPLIED)).extracting(ApplicationResponse::id).containsExactly(1L, 2L);
        assertThat(board.get(OFFER)).extracting(ApplicationResponse::id).containsExactly(3L);
        assertThat(board.get(INTERVIEW)).isEmpty();
        assertThat(board.get(REJECTED)).isEmpty();
    }

    @Test
    void list_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "appliedOn"));
        when(applicationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(card(1L, APPLIED, 0)), pageable, 11));

        PageResponse<ApplicationResponse> page = service.list(new ApplicationListFilter(APPLIED, "acme", null, null), pageable);

        assertThat(page.content()).extracting(ApplicationResponse::id).containsExactly(1L);
        assertThat(page.totalElements()).isEqualTo(11);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void list_rejectsUnknownSortField() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("user.passwordHash"));

        assertThatThrownBy(() -> service.list(noFilter(), pageable))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot sort by");
        verifyNoMoreInteractions(applicationRepository);
    }

    @Test
    void list_rejectsSalaryFromGreaterThanSalaryTo() {
        ApplicationListFilter filter = new ApplicationListFilter(null, null, new BigDecimal("30"), new BigDecimal("20"));

        assertThatThrownBy(() -> service.list(filter, PageRequest.of(0, 10)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("salaryFrom can't be greater than salaryTo");
        verifyNoMoreInteractions(applicationRepository);
    }

    @Test
    void list_rejectsNegativeSalaryBound() {
        ApplicationListFilter filter = new ApplicationListFilter(null, null, new BigDecimal("-1"), null);

        assertThatThrownBy(() -> service.list(filter, PageRequest.of(0, 10)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("can't be negative");
    }

    @Test
    void get_returnsApplicationWithNotes() {
        Application card = card(5L, APPLIED, 0);
        when(applicationRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(card));
        when(noteRepository.findByApplicationIdOrderByCreatedAtDesc(5L))
                .thenReturn(List.of(new Note(card, "Called the recruiter")));

        ApplicationDetailResponse detail = service.get(5L);

        assertThat(detail.application().id()).isEqualTo(5L);
        assertThat(detail.notes()).singleElement()
                .satisfies(note -> assertThat(note.body()).isEqualTo("Called the recruiter"));
    }

    @Test
    void get_unknownOrForeignId_throwsNotFound() {
        when(applicationRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
    }

    // ---------- create / update / delete ----------

    @Test
    void create_appendsCardToEndOfItsColumn() {
        when(applicationRepository.countByUserIdAndStatus(USER_ID, INTERVIEW)).thenReturn(3L);

        ApplicationResponse response = service.create(new CreateApplicationRequest(
                "Acme", "Backend Engineer", INTERVIEW, null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<Application> saved = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(saved.capture());
        assertThat(saved.getValue().getPosition()).isEqualTo(3);
        assertThat(saved.getValue().getStatus()).isEqualTo(INTERVIEW);
        assertThat(response.company()).isEqualTo("Acme");
        verify(userRepository).getReferenceById(USER_ID);
    }

    @Test
    void update_withCurrentVersion_appliesChanges() {
        Application card = card(5L, APPLIED, 0);
        card.setVersion(2);
        when(applicationRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(card));

        ApplicationResponse response = service.update(5L, updateRequest("New Co", "Staff Engineer", 2));

        assertThat(response.company()).isEqualTo("New Co");
        assertThat(response.role()).isEqualTo("Staff Engineer");
        assertThat(response.status()).isEqualTo(APPLIED); // status is not editable through update
        verify(applicationRepository).saveAndFlush(card);
    }

    @Test
    void update_withStaleVersion_throwsOptimisticLockAndDoesNotSave() {
        Application card = card(5L, APPLIED, 0);
        card.setVersion(3);
        when(applicationRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.update(5L, updateRequest("New Co", "Staff Engineer", 2)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(card.getCompany()).isEqualTo("Company 5");
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_withNewStatus_movesCardToBottomOfTargetColumnAndClosesTheGap() {
        Application card = givenCard(5L, APPLIED, 1);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, INTERVIEW)).thenReturn(3L);

        ApplicationResponse response = service.update(5L, updateRequest("Acme", "Engineer", INTERVIEW, 0));

        verify(applicationRepository).shiftPositions(USER_ID, APPLIED, 2, END, -1);
        assertThat(card.getStatus()).isEqualTo(INTERVIEW);
        assertThat(card.getPosition()).isEqualTo(3);
        assertThat(response.status()).isEqualTo(INTERVIEW);
        assertThat(response.company()).isEqualTo("Acme");
        verify(applicationRepository).saveAndFlush(card);
    }

    @Test
    void update_withSameStatus_leavesPositionsAlone() {
        Application card = givenCard(5L, APPLIED, 1);

        service.update(5L, updateRequest("Acme", "Engineer", APPLIED, 0));

        verify(applicationRepository, never()).shiftPositions(any(), any(), anyInt(), anyInt(), anyInt());
        assertThat(card.getPosition()).isEqualTo(1);
    }

    @Test
    void update_withoutStatus_keepsTheCurrentColumn() {
        Application card = givenCard(5L, OFFER, 0);

        service.update(5L, updateRequest("Acme", "Engineer", null, 0));

        verify(applicationRepository, never()).shiftPositions(any(), any(), anyInt(), anyInt(), anyInt());
        assertThat(card.getStatus()).isEqualTo(OFFER);
    }

    @Test
    void update_withNewFollowUpDate_bringsTheNotificationBack() {
        Application card = givenCard(5L, APPLIED, 0);
        card.setFollowUpOn(LocalDate.of(2026, 10, 10));
        card.setReminderDismissedOn(LocalDate.of(2026, 10, 10));

        service.update(5L, new UpdateApplicationRequest("Acme", "Engineer", null, null, null, null, null, null, null,
                null, LocalDate.of(2026, 10, 20), null, 0));

        assertThat(card.getReminderDismissedOn()).isNull();
        assertThat(card.getFollowUpOn()).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    void update_withSameFollowUpDate_keepsTheNotificationDismissed() {
        Application card = givenCard(5L, APPLIED, 0);
        card.setFollowUpOn(LocalDate.of(2026, 10, 10));
        card.setReminderDismissedOn(LocalDate.of(2026, 10, 10));

        service.update(5L, new UpdateApplicationRequest("Acme Corp", "Engineer", null, null, null, null, null, null, null,
                null, LocalDate.of(2026, 10, 10), null, 0));

        assertThat(card.getReminderDismissedOn()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    void update_withStaleVersion_doesNotMoveTheCard() {
        Application card = givenCard(5L, APPLIED, 1);
        card.setVersion(3);

        assertThatThrownBy(() -> service.update(5L, updateRequest("Acme", "Engineer", REJECTED, 2)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(applicationRepository, never()).shiftPositions(any(), any(), anyInt(), anyInt(), anyInt());
        assertThat(card.getStatus()).isEqualTo(APPLIED);
    }

    @Test
    void delete_removesCardAndClosesGapBelowIt() {
        Application card = card(5L, APPLIED, 2);
        when(applicationRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(card));

        service.delete(5L);

        verify(applicationRepository).delete(card);
        verify(applicationRepository).shiftPositions(USER_ID, APPLIED, 3, END, -1);
    }

    @Test
    void delete_unknownId_throwsNotFound() {
        when(applicationRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);
        verify(applicationRepository, never()).delete(any(Application.class));
    }

    // ---------- move ----------

    @Test
    void move_downWithinColumn_shiftsCardsInBetweenUp() {
        Application card = givenCard(5L, APPLIED, 1);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, APPLIED)).thenReturn(5L);

        ApplicationResponse response = service.move(5L, new MoveApplicationRequest(APPLIED, 3));

        // cards at 2 and 3 move up to 1 and 2
        verify(applicationRepository).shiftPositions(USER_ID, APPLIED, 2, 3, -1);
        assertThat(card.getPosition()).isEqualTo(3);
        assertThat(response.position()).isEqualTo(3);
        verify(applicationRepository).saveAndFlush(card);
    }

    @Test
    void move_upWithinColumn_shiftsCardsInBetweenDown() {
        Application card = givenCard(5L, APPLIED, 3);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, APPLIED)).thenReturn(5L);

        service.move(5L, new MoveApplicationRequest(APPLIED, 1));

        // cards at 1 and 2 move down to 2 and 3
        verify(applicationRepository).shiftPositions(USER_ID, APPLIED, 1, 2, 1);
        assertThat(card.getPosition()).isEqualTo(1);
    }

    @Test
    void move_toSamePosition_changesNothing() {
        Application card = givenCard(5L, APPLIED, 2);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, APPLIED)).thenReturn(5L);

        service.move(5L, new MoveApplicationRequest(APPLIED, 2));

        verify(applicationRepository, never()).shiftPositions(any(), any(), anyInt(), anyInt(), anyInt());
        verify(applicationRepository, never()).saveAndFlush(any());
        assertThat(card.getPosition()).isEqualTo(2);
    }

    @Test
    void move_withinColumn_clampsPositionToLastIndex() {
        Application card = givenCard(5L, APPLIED, 0);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, APPLIED)).thenReturn(3L);

        service.move(5L, new MoveApplicationRequest(APPLIED, 99));

        verify(applicationRepository).shiftPositions(USER_ID, APPLIED, 1, 2, -1);
        assertThat(card.getPosition()).isEqualTo(2);
    }

    @Test
    void move_toOtherColumn_closesGapInSourceAndOpensSlotInTarget() {
        Application card = givenCard(5L, APPLIED, 1);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, INTERVIEW)).thenReturn(2L);

        ApplicationResponse response = service.move(5L, new MoveApplicationRequest(INTERVIEW, 0));

        verify(applicationRepository).shiftPositions(USER_ID, APPLIED, 2, END, -1);
        verify(applicationRepository).shiftPositions(USER_ID, INTERVIEW, 0, END, 1);
        assertThat(card.getStatus()).isEqualTo(INTERVIEW);
        assertThat(card.getPosition()).isZero();
        assertThat(response.status()).isEqualTo(INTERVIEW);
        verify(applicationRepository).saveAndFlush(card);
    }

    @Test
    void move_toOtherColumn_clampsPositionToEndOfTarget() {
        Application card = givenCard(5L, APPLIED, 0);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, OFFER)).thenReturn(2L);

        service.move(5L, new MoveApplicationRequest(OFFER, 50));

        verify(applicationRepository).shiftPositions(USER_ID, OFFER, 2, END, 1);
        assertThat(card.getStatus()).isEqualTo(OFFER);
        assertThat(card.getPosition()).isEqualTo(2);
    }

    @Test
    void move_toEmptyColumn_placesCardAtTop() {
        Application card = givenCard(5L, APPLIED, 4);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, REJECTED)).thenReturn(0L);

        service.move(5L, new MoveApplicationRequest(REJECTED, 3));

        assertThat(card.getStatus()).isEqualTo(REJECTED);
        assertThat(card.getPosition()).isZero();
    }

    @Test
    void move_unknownId_throwsNotFound() {
        when(applicationRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.move(99L, new MoveApplicationRequest(OFFER, 0)))
                .isInstanceOf(NotFoundException.class);
        verify(applicationRepository, never()).shiftPositions(any(), any(), anyInt(), anyInt(), anyInt());
    }

    // ---------- helpers ----------

    private static ApplicationListFilter noFilter() {
        return new ApplicationListFilter(null, null, null, null);
    }

    private Application givenCard(Long id, ApplicationStatus status, int position) {
        Application card = card(id, status, position);
        when(applicationRepository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.of(card));
        return card;
    }

    private static Application card(Long id, ApplicationStatus status, int position) {
        Application card = new Application();
        card.setId(id);
        card.setCompany("Company " + id);
        card.setRole("Engineer");
        card.setStatus(status);
        card.setPosition(position);
        card.setVersion(0);
        return card;
    }

    private static UpdateApplicationRequest updateRequest(String company, String role, int version) {
        return updateRequest(company, role, null, version);
    }

    private static UpdateApplicationRequest updateRequest(String company, String role, ApplicationStatus status, int version) {
        return new UpdateApplicationRequest(company, role, status, null, null, null, null, null, null, null, null, null, version);
    }
}
