package com.jobtrackr.jobtrackrbackend.user;

import com.jobtrackr.jobtrackrbackend.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Profile of the caller, identified purely by the Bearer token (there is no user id in the URL)
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me() {
        return userService.getCurrentUser();
    }
}
