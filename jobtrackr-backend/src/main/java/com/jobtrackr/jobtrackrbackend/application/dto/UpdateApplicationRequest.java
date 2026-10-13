package com.jobtrackr.jobtrackrbackend.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

// No status or position here: those only change through PATCH /applications/{id}/move,
// which keeps the other cards' positions consistent.
public record UpdateApplicationRequest(
        @NotBlank @Size(max = 150) String company,
        @NotBlank @Size(max = 150) String role,
        String jdUrl,
        String jdText,
        @Size(max = 100) String location,
        @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal salaryMinLpa,
        @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal salaryMaxLpa,
        @Size(max = 50) String source,
        LocalDate appliedOn,
        LocalDate followUpOn,
        // The version the client last read; a mismatch means someone else saved first (409)
        @NotNull Integer version
) {
}
