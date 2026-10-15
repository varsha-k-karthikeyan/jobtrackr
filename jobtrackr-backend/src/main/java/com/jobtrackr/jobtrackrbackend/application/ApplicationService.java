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
import com.jobtrackr.jobtrackrbackend.note.NoteMapper;
import com.jobtrackr.jobtrackrbackend.note.NoteRepository;
import com.jobtrackr.jobtrackrbackend.note.dto.NoteResponse;
import com.jobtrackr.jobtrackrbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// All business rules for applications: ownership, card ordering and optimistic locking.
// Controllers stay thin (HTTP only) and repositories stay dumb (queries only).
@Service
@RequiredArgsConstructor // Lombok: constructor for all final fields = constructor injection
@Transactional(readOnly = true) // default for reads; write methods override it below
public class ApplicationService {

    // Whitelist for ?sort=, so clients cannot sort by internal paths such as user.passwordHash
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "appliedOn", "followUpOn", "company", "role", "status", "location", "salaryMinLpa", "salaryMaxLpa",
            "position", "createdAt", "updatedAt");

    private final ApplicationRepository applicationRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final ApplicationMapper applicationMapper;
    private final NoteMapper noteMapper;
    private final CurrentUserProvider currentUser;

    public Map<ApplicationStatus, List<ApplicationResponse>> getBoard() {
        // Start with every column so the client also gets the empty ones
        Map<ApplicationStatus, List<ApplicationResponse>> board = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            board.put(status, new ArrayList<>());
        }
        applicationRepository.findByUserIdOrderByStatusAscPositionAsc(currentUser.getUserId())
                .forEach(application -> board.get(application.getStatus()).add(applicationMapper.toResponse(application)));
        return board;
    }

    public PageResponse<ApplicationResponse> list(ApplicationListFilter filter, Pageable pageable) {
        validateSalaryRange(filter);
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot sort by '" + order.getProperty() + "'. Allowed: " + SORTABLE_FIELDS.stream().sorted().toList());
            }
        }
        // Empty values always go to the end, in either direction. PostgreSQL's default treats NULL as larger
        // than any value, so "highest salary first" would otherwise start with every card that has no salary.
        Sort nullsLast = Sort.by(pageable.getSort().stream().map(Sort.Order::nullsLast).toList());
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), nullsLast);
        return PageResponse.from(applicationRepository
                .findAll(ApplicationSpecifications.filter(currentUser.getUserId(), filter), sortedPageable)
                .map(applicationMapper::toResponse));
    }

    public ApplicationDetailResponse get(Long id) {
        Application application = findOwned(id);
        List<NoteResponse> notes = noteRepository.findByApplicationIdOrderByCreatedAtDesc(id).stream()
                .map(noteMapper::toResponse)
                .toList();
        return new ApplicationDetailResponse(applicationMapper.toResponse(application), notes);
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request) {
        Long userId = currentUser.getUserId();
        // New cards go to the bottom of their column
        int position = (int) applicationRepository.countByUserIdAndStatus(userId, request.status());
        // getReferenceById gives a proxy holding only the id: enough for the FK, no SELECT on users
        Application application = applicationMapper.toEntity(request, userRepository.getReferenceById(userId), position);
        applicationRepository.save(application);
        return applicationMapper.toResponse(application);
    }

    @Transactional
    public ApplicationResponse update(Long id, UpdateApplicationRequest request) {
        Application application = findOwned(id);
        // Hibernate's own @Version check compares against the value it just loaded, which is always current.
        // The stale value lives in the client, so compare the version the client sent explicitly.
        if (!request.version().equals(application.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Application.class, id);
        }
        // Changing the column from the edit form: the card goes to the bottom of the new column. Same
        // transaction as the field changes, so a failure leaves neither the move nor the edit applied.
        if (request.status() != null && request.status() != application.getStatus()) {
            moveToBottomOfColumn(application, request.status());
        }
        applicationMapper.applyUpdate(application, request);
        // Flush now so the response carries the incremented version and new updatedAt
        applicationRepository.saveAndFlush(application);
        return applicationMapper.toResponse(application);
    }

    @Transactional
    public void delete(Long id) {
        Application application = findOwned(id);
        applicationRepository.delete(application);
        applicationRepository.flush();
        // Close the gap the card leaves behind
        applicationRepository.shiftPositions(currentUser.getUserId(), application.getStatus(),
                application.getPosition() + 1, Integer.MAX_VALUE, -1);
    }

    // One transaction: either the neighbours shift AND the card moves, or nothing changes.
    @Transactional
    public ApplicationResponse move(Long id, MoveApplicationRequest request) {
        Long userId = currentUser.getUserId();
        Application card = findOwned(id);

        ApplicationStatus sourceStatus = card.getStatus();
        ApplicationStatus targetStatus = request.status();
        int sourcePosition = card.getPosition();
        int targetCount = (int) applicationRepository.countByUserIdAndStatus(userId, targetStatus);

        int targetPosition;
        if (sourceStatus == targetStatus) {
            // Reorder inside one column; the last valid index is count - 1 because the card is already in it
            targetPosition = Math.min(request.position(), targetCount - 1);
            if (targetPosition == sourcePosition) {
                return applicationMapper.toResponse(card);
            }
            if (targetPosition > sourcePosition) {
                // Moving down: cards between the old and new slot move up by one
                applicationRepository.shiftPositions(userId, sourceStatus, sourcePosition + 1, targetPosition, -1);
            } else {
                // Moving up: cards between the new and old slot move down by one
                applicationRepository.shiftPositions(userId, sourceStatus, targetPosition, sourcePosition - 1, 1);
            }
        } else {
            // Different column; the card may be appended, so the last valid index is count
            targetPosition = Math.min(request.position(), targetCount);
            // Close the gap in the source column, then open a slot in the target column
            applicationRepository.shiftPositions(userId, sourceStatus, sourcePosition + 1, Integer.MAX_VALUE, -1);
            applicationRepository.shiftPositions(userId, targetStatus, targetPosition, Integer.MAX_VALUE, 1);
        }

        card.setStatus(targetStatus);
        card.setPosition(targetPosition);
        applicationRepository.saveAndFlush(card);
        return applicationMapper.toResponse(card);
    }

    // Query parameters are not a @Valid body, so the salary bounds are checked here: a 400 with a clear
    // message instead of silently returning nothing.
    private static void validateSalaryRange(ApplicationListFilter filter) {
        BigDecimal from = filter.salaryFrom();
        BigDecimal to = filter.salaryTo();
        if ((from != null && from.signum() < 0) || (to != null && to.signum() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Salary bounds can't be negative");
        }
        if (from != null && to != null && from.compareTo(to) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "salaryFrom can't be greater than salaryTo");
        }
    }

    // Takes a card out of its column (closing the gap) and appends it to another one. Only sets the new
    // status/position on the entity; the caller's flush writes them, together with any other changes.
    private void moveToBottomOfColumn(Application card, ApplicationStatus targetStatus) {
        Long userId = currentUser.getUserId();
        // Counted before the card changes column, so it is exactly the next free position at the bottom
        int targetPosition = (int) applicationRepository.countByUserIdAndStatus(userId, targetStatus);
        applicationRepository.shiftPositions(userId, card.getStatus(), card.getPosition() + 1, Integer.MAX_VALUE, -1);
        card.setStatus(targetStatus);
        card.setPosition(targetPosition);
    }

    // Every single-card operation goes through here, so the owner filter is always applied
    private Application findOwned(Long id) {
        return applicationRepository.findByIdAndUserId(id, currentUser.getUserId())
                .orElseThrow(() -> new NotFoundException("Application " + id + " not found"));
    }
}
