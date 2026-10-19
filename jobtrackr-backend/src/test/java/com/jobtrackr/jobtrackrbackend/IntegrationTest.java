package com.jobtrackr.jobtrackrbackend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a full-context test against the shared Testcontainers PostgreSQL: the whole application, the
 * "test" profile (dummy JWT secret, scheduling off) and the database from {@link TestcontainersConfiguration}.
 * One annotation, so no integration test can forget the container and fall back to localhost:5432.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
