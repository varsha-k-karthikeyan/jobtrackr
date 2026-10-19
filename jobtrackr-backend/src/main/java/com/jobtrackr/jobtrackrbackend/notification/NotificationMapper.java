package com.jobtrackr.jobtrackrbackend.notification;

import com.jobtrackr.jobtrackrbackend.application.Application;
import com.jobtrackr.jobtrackrbackend.notification.dto.FollowUpNotification;
import com.jobtrackr.jobtrackrbackend.notification.dto.InterviewNotification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

// Application entity -> follow-up and interview notification DTOs
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

    // daysUntil compares calendar days in the configured zone: 23:30 tonight is 0, 00:30 tomorrow is 1
    public InterviewNotification toInterviewNotification(Application application, LocalDate today, ZoneId zone) {
        return new InterviewNotification(
                application.getId(),
                application.getCompany(),
                application.getRole(),
                application.getStatus(),
                application.getInterviewAt(),
                ChronoUnit.DAYS.between(today, application.getInterviewAt().atZone(zone).toLocalDate()));
    }
}
