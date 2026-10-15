package com.jobtrackr.jobtrackrbackend.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Turns on @Scheduled methods (currently only auth/RefreshTokenCleanupJob). Behind a switch so that tests,
// or a second instance of the app, can run without background jobs: jobtrackr.scheduling.enabled=false.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "jobtrackr.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
