package com.jobtrackr.jobtrackrbackend.user;

import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import com.jobtrackr.jobtrackrbackend.common.exception.NotFoundException;
import com.jobtrackr.jobtrackrbackend.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Read access to the caller's own profile
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        // A valid token can outlive its account (deleted user), hence the 404 path
        User user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt());
    }
}
