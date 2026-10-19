package com.jobtrackr.jobtrackrbackend.user;

import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import com.jobtrackr.jobtrackrbackend.common.exception.ForbiddenException;
import com.jobtrackr.jobtrackrbackend.common.exception.NotFoundException;
import com.jobtrackr.jobtrackrbackend.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserProvider currentUser;
    @InjectMocks
    private UserService service;

    @Test
    void getCurrentUser_returnsProfileOfTokenSubject() {
        when(currentUser.getUserId()).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(new User("varsha@example.com", "hash", "Varsha")));

        UserResponse response = service.getCurrentUser();

        assertThat(response.email()).isEqualTo("varsha@example.com");
        assertThat(response.fullName()).isEqualTo("Varsha");
        assertThat(response.emailRemindersEnabled()).isTrue();
    }

    @Test
    void setEmailReminders_storesTheSwitch_andReturnsTheUpdatedProfile() {
        User user = new User("varsha@example.com", "hash", "Varsha");
        when(currentUser.getUserId()).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        UserResponse response = service.setEmailReminders(false);

        assertThat(user.isEmailRemindersEnabled()).isFalse();
        assertThat(response.emailRemindersEnabled()).isFalse();
    }

    @Test
    void setEmailReminders_forTheDemoAccount_isForbidden() {
        User demo = new User(DemoAccount.EMAIL, "hash", "Demo User");
        when(currentUser.getUserId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(demo));

        assertThatThrownBy(() -> service.setEmailReminders(false)).isInstanceOf(ForbiddenException.class);
        assertThat(demo.isEmailRemindersEnabled()).isTrue();
    }

    @Test
    void getCurrentUser_accountNoLongerExists_throwsNotFound() {
        when(currentUser.getUserId()).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUser()).isInstanceOf(NotFoundException.class);
    }
}
