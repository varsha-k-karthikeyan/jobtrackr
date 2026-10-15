package com.jobtrackr.jobtrackrbackend.notification;

import com.jobtrackr.jobtrackrbackend.application.Application;
import com.jobtrackr.jobtrackrbackend.notification.dto.FollowUpNotification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// Application entity -> follow-up notification DTO
@Component
public class NotificationMapper {

    public FollowUpNotification toNotification(Application application, LocalDate today) {
        return new FollowUpNotification(
                application.getId(),
                application.getCompany(),
                application.getRole(),
                application.getStatus(),
                application.getFollowUpOn(),
                ChronoUnit.DAYS.between(today, application.getFollowUpOn()));
    }
}
