package com.jobtrackr.jobtrackrbackend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// "Now" as an injectable bean: code that depends on time (token expiry, revocation) asks this Clock instead
// of calling Instant.now(), so unit tests can pass Clock.fixed(...) and move time explicitly.
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
