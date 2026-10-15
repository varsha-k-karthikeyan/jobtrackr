package com.jobtrackr.jobtrackrbackend.stats;

import com.jobtrackr.jobtrackrbackend.application.Application;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

// Read-only queries for the stats feature. A second Spring Data repository over the Application entity keeps
// this feature self-contained; extending the bare Repository (not JpaRepository) exposes no save or delete.
public interface StatsRepository extends Repository<Application, Long> {

    // One query, one row per status that has at least one card. A status with no cards is simply absent,
    // which StatsService treats as 0.
    @Query("""
            select new com.jobtrackr.jobtrackrbackend.stats.StatusCount(a.status, count(a))
              from Application a
             where a.user.id = :userId
             group by a.status
            """)
    List<StatusCount> countByStatus(@Param("userId") Long userId);
}
