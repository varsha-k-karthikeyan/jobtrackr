package com.jobtrackr.jobtrackrbackend.stats;

import com.jobtrackr.jobtrackrbackend.application.ApplicationStatus;

// One row of the grouped count query: how many of the user's applications are in one status.
// count is Long (not long) because JPQL's count() returns Long and the constructor expression must match it.
public record StatusCount(ApplicationStatus status, Long count) {
}
