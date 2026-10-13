package com.jobtrackr.jobtrackrbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

// Entry point. @SpringBootApplication turns on auto-configuration and scans this package and everything
// below it for beans (controllers, services, components, repositories, entities). That is why every
// feature package lives under com.jobtrackr.jobtrackrbackend.
@SpringBootApplication
public class JobtrackrBackendApplication {

    public static void main(String[] args) {
        // Run the JVM in UTC. The Postgres driver sends the JVM's default zone on connect, and Windows
        // reports the legacy name "Asia/Calcutta", which the postgres:16 image no longer recognises.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(JobtrackrBackendApplication.class, args);
    }

}
