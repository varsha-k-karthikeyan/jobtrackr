package com.jobtrackr.jobtrackrbackend.user.dto;

import java.time.Instant;

// Deliberately has no passwordHash
public record UserResponse(Long id, String email, String fullName, Instant createdAt) {
}
