package com.jobtrackr.jobtrackrbackend.user;

import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import com.jobtrackr.jobtrackrbackend.common.exception.ForbiddenException;
import com.jobtrackr.jobtrackrbackend.common.exception.NotFoundException;
import com.jobtrackr.jobtrackrbackend.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The caller's own profile: read it, and switch reminder emails on or off
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        return toResponse(findCurrentUser());
    }

    // Takes effect at the reminder job's next run (it runs every minute and reads the switch each time).
    // The demo account never gets reminder emails, so a switch there would only mislead whoever is using it.
    @Transactional
    public UserResponse setEmailReminders(boolean enabled) {
        User user = findCurrentUser();
        if (DemoAccount.isDemo(user)) {
            throw new ForbiddenException("The demo account does not receive reminder emails. Register your own account to get them.");
        }
        user.setEmailRemindersEnabled(enabled);
        return toResponse(user);
    }

    private User findCurrentUser() {
        // A valid token can outlive its account (deleted user), hence the 404 path
        return userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt(), user.isEmailRemindersEnabled());
    }
}
