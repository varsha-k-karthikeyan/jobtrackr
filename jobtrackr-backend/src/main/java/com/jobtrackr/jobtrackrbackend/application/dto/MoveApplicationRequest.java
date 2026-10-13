package com.jobtrackr.jobtrackrbackend.application.dto;

import com.jobtrackr.jobtrackrbackend.application.ApplicationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Target column and zero-based index inside it
public record MoveApplicationRequest(
        @NotNull ApplicationStatus status,
        @NotNull @Min(0) Integer position
) {
}
