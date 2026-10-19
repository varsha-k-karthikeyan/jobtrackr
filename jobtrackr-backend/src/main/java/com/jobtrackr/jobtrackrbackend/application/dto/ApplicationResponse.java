package com.jobtrackr.jobtrackrbackend.application.dto;

import com.jobtrackr.jobtrackrbackend.application.ApplicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// What the API returns for a card. The client must send `version` back on PUT (optimistic locking).
public record ApplicationResponse(
        Long id,
        String company,
        String role,
        ApplicationStatus status,
        int position,
        String jdUrl,
        String jdText,
        String location,
        BigDecimal salaryMinLpa,
        BigDecimal salaryMaxLpa,
        String source,
        LocalDate appliedOn,
        LocalDate followUpOn,
        Instant interviewAt,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
