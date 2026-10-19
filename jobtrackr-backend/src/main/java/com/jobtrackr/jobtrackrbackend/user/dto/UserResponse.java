package com.jobtrackr.jobtrackrbackend.user.dto;

import java.time.Instant;

// Deliberately has no passwordHash. emailRemindersEnabled drives the switch in the account menu.
public record UserResponse(Long id, String email, String fullName, Instant createdAt, boolean emailRemindersEnabled) {
}
