package com.jobtrackr.jobtrackrbackend.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
