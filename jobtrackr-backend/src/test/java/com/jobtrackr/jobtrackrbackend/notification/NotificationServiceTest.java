package com.jobtrackr.jobtrackrbackend.notification;

import com.jobtrackr.jobtrackrbackend.application.Application;
import com.jobtrackr.jobtrackrbackend.application.ApplicationRepository;
import com.jobtrackr.jobtrackrbackend.application.ApplicationStatus;
import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import com.jobtrackr.jobtrackrbackend.common.exception.NotFoundException;
import com.jobtrackr.jobtrackrbackend.notification.dto.FollowUpNotification;
import com.jobtrackr.jobtrackrbackend.notification.dto.InterviewNotification;
import com.jobtrackr.jobtrackrbackend.notification.dto.NotificationsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pure unit test: repository and current user are mocks, time is a fixed Clock
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private CurrentUserProvider currentUser;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        // 20:00 UTC on the 14th is already 01:30 on the 15th in India
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T20:00:00Z"), ZoneOffset.UTC);
        NotificationProperties properties = new NotificationProperties(ZoneId.of("Asia/Kolkata"), 7);
        service = new NotificationService(applicationRepository, new NotificationMapper(), properties, currentUser, clock);
        when(currentUser.getUserId()).thenReturn(USER_ID);
    }

    @Test
    void getNotifications_decidesTodayInTheConfiguredZone_andLooksSevenDaysAhead() {
        when(applicationRepository.findOpenFollowUps(any(), any(), any())).thenReturn(List.of());

        NotificationsResponse response = service.getNotifications();

        assertThat(response.today()).isEqualTo(TODAY);
        verify(applicationRepository).findOpenFollowUps(USER_ID, NotificationService.NOTIFIED_STATUSES, TODAY.plusDays(7));
    }

    @Test
    void getNotifications_splitsDueFromUpcoming_andCountsDays() {
        // Mocks are built first: creating them inside when(...).thenReturn(...) would be nested stubbing
        List<Application> open = List.of(
                application(10L, "Overdue", TODAY.minusDays(3)),
                application(11L, "Today", TODAY),
                application(12L, "Soon", TODAY.plusDays(2)));
        when(applicationRepository.findOpenFollowUps(any(), any(), any())).thenReturn(open);

        NotificationsResponse response = service.getNotifications();

        assertThat(response.due()).extracting(FollowUpNotification::company, FollowUpNotification::daysUntilDue)
                .containsExactly(tuple("Overdue", -3L), tuple("Today", 0L));
        assertThat(response.upcoming()).extracting(FollowUpNotification::applicationId, FollowUpNotification::daysUntilDue)
                .containsExactly(tuple(12L, 2L));
    }

    @Test
    void getNotifications_listsInterviewsFromNowToTheEndOfTheWindow_withDaysCountedInIndia() {
        // Now is 01:30 on the 15th in India; the window ends at midnight after the 22nd, India time
        Application tonight = interview(20L, "Zoho", Instant.parse("2026-10-15T17:30:00Z"));   // 23:00 on the 15th
        Application tomorrow = interview(21L, "Swiggy", Instant.parse("2026-10-15T19:00:00Z")); // 00:30 on the 16th
        when(applicationRepository.findInterviewsBetween(USER_ID, NotificationService.INTERVIEW_STATUSES,
                Instant.parse("2026-10-14T20:00:00Z"), Instant.parse("2026-10-22T18:30:00Z")))
                .thenReturn(List.of(tonight, tomorrow));

        NotificationsResponse response = service.getNotifications();

        assertThat(response.interviews()).extracting(InterviewNotification::company, InterviewNotification::daysUntil)
                .containsExactly(tuple("Zoho", 0L), tuple("Swiggy", 1L));
    }

    @Test
    void dismiss_ownCard_dismissesOnlyThatCardForToday() {
        when(applicationRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(mock(Application.class)));

        service.dismiss(10L);

        verify(applicationRepository).dismissFollowUps(USER_ID, 10L, NotificationService.NOTIFIED_STATUSES, TODAY);
    }

    @Test
    void dismiss_cardOfAnotherUser_is404AndChangesNothing() {
        when(applicationRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dismiss(99L)).isInstanceOf(NotFoundException.class);
        verify(applicationRepository, never()).dismissFollowUps(any(), anyLong(), any(), any());
    }

    @Test
    void dismissAll_dismissesEveryDueCardOfTheCaller() {
        service.dismissAll();

        verify(applicationRepository).dismissFollowUps(USER_ID, null, NotificationService.NOTIFIED_STATUSES, TODAY);
    }

    private static Application interview(Long id, String company, Instant interviewAt) {
        Application application = mock(Application.class);
        when(application.getId()).thenReturn(id);
        when(application.getCompany()).thenReturn(company);
        when(application.getInterviewAt()).thenReturn(interviewAt);
        return application;
    }

    private static Application application(Long id, String company, LocalDate followUpOn) {
        Application application = mock(Application.class);
        when(application.getId()).thenReturn(id);
        when(application.getCompany()).thenReturn(company);
        when(application.getRole()).thenReturn("Engineer");
        when(application.getStatus()).thenReturn(ApplicationStatus.APPLIED);
        when(application.getFollowUpOn()).thenReturn(followUpOn);
        return application;
    }
}
