package com.jobtrackr.jobtrackrbackend.stats;

import com.jobtrackr.jobtrackrbackend.application.ApplicationStatus;
import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import com.jobtrackr.jobtrackrbackend.stats.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

// Summary numbers for the caller's board, computed from one grouped count query
@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsRepository statsRepository;
    private final CurrentUserProvider currentUser;

    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        Map<ApplicationStatus, Long> countsByStatus = new EnumMap<>(ApplicationStatus.class);
        statsRepository.countByStatus(currentUser.getUserId())
                .forEach(statusCount -> countsByStatus.put(statusCount.status(), statusCount.count()));

        // Statuses with no cards are missing from the query result, hence the default of 0
        long applied = countsByStatus.getOrDefault(ApplicationStatus.APPLIED, 0L);
        long interview = countsByStatus.getOrDefault(ApplicationStatus.INTERVIEW, 0L);
        long offer = countsByStatus.getOrDefault(ApplicationStatus.OFFER, 0L);
        long rejected = countsByStatus.getOrDefault(ApplicationStatus.REJECTED, 0L);
        long total = applied + interview + offer + rejected;

        // Any outcome other than "still waiting" counts as a response, including a rejection
        double responseRate = total == 0 ? 0 : (double) (interview + offer + rejected) / total;
        return new StatsResponse(total, applied, interview, offer, rejected, responseRate);
    }
}
