package com.jobtrackr.jobtrackrbackend.stats;

import com.jobtrackr.jobtrackrbackend.stats.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Stats for the caller's own applications; the user comes from the Bearer token
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public StatsResponse getStats() {
        return statsService.getStats();
    }
}
