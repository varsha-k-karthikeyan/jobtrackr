package com.jobtrackr.jobtrackrbackend.application.dto;

import com.jobtrackr.jobtrackrbackend.application.ApplicationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Body of POST /applications. There is deliberately no id, owner or position: the service assigns them.
// The limits mirror the column sizes in V1__init.sql, so bad input is a 400 here, never a database error.
public record CreateApplicationRequest(
        @NotBlank @Size(max = 150) String company,
        @NotBlank @Size(max = 150) String role,
        @NotNull ApplicationStatus status,
        String jdUrl,
        String jdText,
        @Size(max = 100) String location,
        @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal salaryMinLpa,
        @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal salaryMaxLpa,
        @Size(max = 50) String source,
        LocalDate appliedOn,
        LocalDate followUpOn,
        // Interview start as an exact instant (ISO-8601 with an offset or Z); optional
        Instant interviewAt
) {
}
