package com.jobtrackr.jobtrackrbackend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Every migration, from V1 to the latest file, applied to a brand-new EMPTY schema: what happens to a fresh
// database (a new developer, a new deployment). Independent of the schema the Spring tests run on, and
// written without Spring so nothing else can have touched the schema first. The expected count comes from the
// migration files themselves, so adding V10 needs no change here.
class FlywayMigrationIntegrationTest {

    private final String schema = "migration_check_" + UUID.randomUUID().toString().replace("-", "");

    // This class may run before any Spring test has started the shared container; start() is a no-op otherwise
    @BeforeAll
    static void startContainer() {
        TestcontainersConfiguration.POSTGRES.start();
    }

    @AfterEach
    void dropSchema() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migratesAnEmptyDatabaseFromV1ToTheLatestVersion() throws Exception {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");
        int latestVersion = Arrays.stream(files)
                .map(file -> file.getFilename().substring(1, file.getFilename().indexOf("__")))
                .mapToInt(Integer::parseInt)
                .max().orElseThrow();

        Flyway flyway = Flyway.configure()
                .dataSource(TestcontainersConfiguration.POSTGRES.getJdbcUrl(),
                        TestcontainersConfiguration.POSTGRES.getUsername(), TestcontainersConfiguration.POSTGRES.getPassword())
                .schemas(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(files.length);
        assertThat(result.targetSchemaVersion).isEqualTo(String.valueOf(latestVersion));
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate(); // throws if an applied file's checksum no longer matches

        assertThat(tables()).contains("users", "applications", "notes", "refresh_tokens", "password_reset_tokens")
                .doesNotContain("resumes", "match_results"); // dropped by V8
    }

    private List<String> tables() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = connect();
             ResultSet rows = connection.getMetaData().getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (rows.next()) {
                names.add(rows.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(TestcontainersConfiguration.POSTGRES.getJdbcUrl(),
                TestcontainersConfiguration.POSTGRES.getUsername(), TestcontainersConfiguration.POSTGRES.getPassword());
    }
}
