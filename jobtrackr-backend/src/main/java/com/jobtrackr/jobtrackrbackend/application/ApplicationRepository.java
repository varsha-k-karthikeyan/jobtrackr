package com.jobtrackr.jobtrackrbackend.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// JpaSpecificationExecutor adds findAll(Specification, Pageable) for the filterable, paginated list
public interface ApplicationRepository
        extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    // Board view: every card of one user, grouped by column and in card order
    List<Application> findByUserIdOrderByStatusAscPositionAsc(Long userId);

    // Single-card lookups always include the owner, so another user's id behaves as "not found"
    Optional<Application> findByIdAndUserId(Long id, Long userId);

    // Number of cards in one column; also the position of the next card appended to it
    long countByUserIdAndStatus(Long userId, ApplicationStatus status);

    // ---- Follow-up notifications (notification package) ----

    // Open cards whose follow-up date is on or before lastDay and whose notification was not dismissed
    // for that date. Most overdue first. NotificationService splits the result into "due" and "upcoming".
    @Query("""
            select a from Application a
             where a.user.id = :userId
               and a.status in :statuses
               and a.followUpOn <= :lastDay
               and (a.reminderDismissedOn is null or a.reminderDismissedOn < a.followUpOn)
             order by a.followUpOn, a.id
            """)
    List<Application> findOpenFollowUps(@Param("userId") Long userId,
                                        @Param("statuses") Collection<ApplicationStatus> statuses,
                                        @Param("lastDay") LocalDate lastDay);

    // Dismisses every due notification of one user (one card when applicationId is given, else all).
    // A bulk update, like shiftPositions: it does not bump @Version, so dismissing a notification never
    // makes the user's open edit form fail with a 409.
    @Modifying
    @Query("""
            update Application a
               set a.reminderDismissedOn = :today
             where a.user.id = :userId
               and (:applicationId is null or a.id = :applicationId)
               and a.status in :statuses
               and a.followUpOn <= :today
               and (a.reminderDismissedOn is null or a.reminderDismissedOn < a.followUpOn)
            """)
    int dismissFollowUps(@Param("userId") Long userId,
                         @Param("applicationId") Long applicationId,
                         @Param("statuses") Collection<ApplicationStatus> statuses,
                         @Param("today") LocalDate today);

    // Moves a contiguous range of cards in one column up (-1) or down (+1) with a single UPDATE.
    // A bulk JPQL update does not bump @Version, so shifting neighbours never causes a 409 on them.
    @Modifying
    @Query("""
            update Application a
               set a.position = a.position + :delta
             where a.user.id = :userId
               and a.status = :status
               and a.position between :fromPosition and :toPosition
            """)
    int shiftPositions(@Param("userId") Long userId,
                       @Param("status") ApplicationStatus status,
                       @Param("fromPosition") int fromPosition,
                       @Param("toPosition") int toPosition,
                       @Param("delta") int delta);
}
