package com.jobtrackr.jobtrackrbackend.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Registers PasswordResetProperties and turns on @Async, which the reset email listener needs to send off
// the request thread. Spring Boot supplies the thread pool (spring.task.execution.*).
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetConfig {
}
