package com.jobtrackr.jobtrackrbackend.notification;

import com.jobtrackr.jobtrackrbackend.notification.dto.NotificationsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Backs the bell in the app header. A notification is addressed by its application id (one card has at
// most one follow-up). DELETE means "remove it from my notifications", never "delete the application".
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public NotificationsResponse getNotifications() {
        return notificationService.getNotifications();
    }

    @DeleteMapping("/{applicationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable Long applicationId) {
        notificationService.dismiss(applicationId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismissAll() {
        notificationService.dismissAll();
    }
}
