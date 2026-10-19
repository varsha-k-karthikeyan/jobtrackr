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

// Body of PUT /applications/{id}. Replaces every editable field.
// status is optional: null keeps the current column. A different status moves the card to the BOTTOM of
// that column (ApplicationService.update keeps positions gap-free). To place a card at a specific
// position, use PATCH /applications/{id}/move instead. There is never a position field here.
public record UpdateApplicationRequest(
        @NotBlank @Size(max = 150) String company,
        @NotBlank @Size(max = 150) String role,
        ApplicationStatus status,
        String jdUrl,
        String jdText,
        @Size(max = 100) String location,
        @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal salaryMinLpa,
        @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal salaryMaxLpa,
        @Size(max = 50) String source,
        LocalDate appliedOn,
        LocalDate followUpOn,
        // Interview start as an exact instant; like every other field, leaving it out of a PUT clears it
        Instant interviewAt,
        // The version the client last read; a mismatch means someone else saved first (409)
        @NotNull Integer version
) {
}
