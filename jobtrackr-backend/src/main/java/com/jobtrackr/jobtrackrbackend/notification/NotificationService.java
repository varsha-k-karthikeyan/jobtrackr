package com.jobtrackr.jobtrackrbackend.notification;

import com.jobtrackr.jobtrackrbackend.application.Application;
import com.jobtrackr.jobtrackrbackend.application.ApplicationRepository;
import com.jobtrackr.jobtrackrbackend.application.ApplicationStatus;
import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import com.jobtrackr.jobtrackrbackend.common.exception.NotFoundException;
import com.jobtrackr.jobtrackrbackend.notification.dto.FollowUpNotification;
import com.jobtrackr.jobtrackrbackend.notification.dto.InterviewNotification;
import com.jobtrackr.jobtrackrbackend.notification.dto.NotificationsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// The bell: follow-ups and upcoming interviews. Nothing is generated or sent ahead of time: the lists are
// computed from the cards on every request, so they are always in step with the board. The only stored state
// is reminder_dismissed_on, which hides a due follow-up until its date changes. Interviews need no dismissing:
// they leave the list once they start.
@Service
@RequiredArgsConstructor
public class NotificationService {

    // Offer and Rejected are finished: there is nobody left to follow up with
    static final List<ApplicationStatus> NOTIFIED_STATUSES = List.of(ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW);

    // Interviews in the bell and in reminder emails: Rejected cards are left out; an Offer card can still have
    // a final round
    static final List<ApplicationStatus> INTERVIEW_STATUSES =
            List.of(ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);

    private final ApplicationRepository applicationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationProperties properties;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NotificationsResponse getNotifications() {
        Long userId = currentUser.getUserId();
        LocalDate today = today();
        LocalDate lastUpcomingDay = today.plusDays(properties.upcomingDays());
        List<FollowUpNotification> due = new ArrayList<>();
        List<FollowUpNotification> upcoming = new ArrayList<>();
        for (Application application : applicationRepository.findOpenFollowUps(userId, NOTIFIED_STATUSES, lastUpcomingDay)) {
            FollowUpNotification notification = notificationMapper.toNotification(application, today);
            (notification.daysUntilDue() <= 0 ? due : upcoming).add(notification);
        }
        // Interviews still ahead (from now, so a finished one drops off) until the end of the same window
        Instant endOfWindow = lastUpcomingDay.plusDays(1).atStartOfDay(properties.zone()).toInstant();
        List<InterviewNotification> interviews = applicationRepository
                .findInterviewsBetween(userId, INTERVIEW_STATUSES, clock.instant(), endOfWindow).stream()
                .map(application -> notificationMapper.toInterviewNotification(application, today, properties.zone()))
                .toList();
        return new NotificationsResponse(today, due, upcoming, interviews);
    }

    // Idempotent, like any DELETE: dismissing twice, or dismissing a card that is not due, changes nothing.
    // Another user's card is a 404, exactly as if it did not exist.
    @Transactional
    public void dismiss(Long applicationId) {
        Long userId = currentUser.getUserId();
        if (applicationRepository.findByIdAndUserId(applicationId, userId).isEmpty()) {
            throw new NotFoundException("Application " + applicationId + " not found");
        }
        applicationRepository.dismissFollowUps(userId, applicationId, NOTIFIED_STATUSES, today());
    }

    @Transactional
    public void dismissAll() {
        applicationRepository.dismissFollowUps(currentUser.getUserId(), null, NOTIFIED_STATUSES, today());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(properties.zone()));
    }
}
