package com.jobtrackr.jobtrackrbackend.user.dto;

import jakarta.validation.constraints.NotNull;

// Body of PUT /me/email-reminders. Boolean, not boolean: a missing value must be a 400, not a silent "false".
public record EmailRemindersRequest(@NotNull Boolean enabled) {
}
