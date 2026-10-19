package com.jobtrackr.jobtrackrbackend.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Registers the notification feature's typed settings (in-app bell and reminder emails) as beans
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({NotificationProperties.class, ReminderEmailProperties.class})
public class NotificationConfig {
}
