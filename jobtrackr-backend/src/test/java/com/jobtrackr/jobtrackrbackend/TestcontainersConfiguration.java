package com.jobtrackr.jobtrackrbackend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// One throwaway PostgreSQL 16 for the whole test run, in Docker, on a random host port (so a local
// PostgreSQL service on 5432 doesn't matter). @ServiceConnection points spring.datasource.* at it, and
// Flyway migrates the empty database from V1 when the first test context starts.
//
// The container is a static singleton, not created per bean: test classes with different @MockitoBean
// sets get different Spring contexts, and each would otherwise start its own database. start() on an
// already running container does nothing, so every context shares this one. Testcontainers' Ryuk
// sidecar removes it when the JVM exits.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Same major version as docker-compose.yml, so tests see the same SQL dialect as development
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }
}
