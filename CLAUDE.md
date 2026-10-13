# JobTrackr

Job application tracker: Kanban board plus AI resume match. Monorepo; the git repository is at this root, not inside `jobtrackr-backend`.

The owner is a senior Angular developer learning Spring Boot. After each piece of work, briefly explain the key design decisions, because the code must be explained in interviews. Work one stage at a time, say exactly how to verify it, and wait for "next".

## Where to look

- `docs/ARCHITECTURE.md`: code structure, data model, and how the move, optimistic-locking and auth logic work. Read it before changing code.
- `docs/DEBUGGING.md`: symptom-to-fix tables for startup failures, status codes, positions and tests.
- `CONTRIBUTING.md`: commit format and step-by-step recipes (add a column, an endpoint, a feature).

Keep these in step with the code: a change to behaviour, configuration or structure updates the matching document in the same commit.

## Commits

Commit messages are linted by commitlint through a husky `commit-msg` hook (`commitlint.config.js`). Format: `<type>(<scope>): <subject>`, for example `feat(application): add follow-up reminder endpoint`. The allowed types and scopes are listed in `CONTRIBUTING.md`. Subject in lower case, imperative, no full stop, header at most 100 characters. Do not bypass the hook with `--no-verify`.

## Stack

- Backend: `jobtrackr-backend` — Spring Boot 4.1.x, Java 21, Maven (use the wrapper, `mvnw.cmd`)
- Web MVC, Spring Data JPA (Hibernate), Bean Validation, Flyway, Spring Security with the OAuth2 resource server (JWT, HS256), Actuator, Mail, Lombok
- springdoc-openapi 3.x for Swagger UI (the 3.x line is the one for Boot 4; its version is pinned in `pom.xml`)
- PostgreSQL 16 in Docker (`docker-compose.yml` at this root)
- Tests: JUnit 5, Mockito, AssertJ, MockMvc
- Frontend (later): Angular
- Base package: `com.jobtrackr.jobtrackrbackend`

## Run commands

Windows, PowerShell. Quote paths that contain spaces.

```powershell
# 1. Database (from the monorepo root)
cd "E:\JobTrackr project"
docker compose up -d

# 2. Backend (JWT_SECRET is required, at least 32 characters)
cd "E:\JobTrackr project\jobtrackr-backend"
$env:JWT_SECRET = "<your secret>"
.\mvnw.cmd spring-boot:run        # or: mvn spring-boot:run

# 3. Tests (the Docker database must be running; tests use their own dummy JWT secret)
.\mvnw.cmd test
```

- Swagger UI: http://localhost:8080/swagger-ui.html (register or log in, then use Authorize with the access token)
- `docker compose down` keeps the data; `docker compose down -v` wipes it

## Working rules

- **Package by feature**, not by layer: `common`, `user`, `application`, `note`, `auth`. Each feature holds its own entity, repository, service, controller, mapper and `dto` package.
- **DTOs are Java records.** Never return or accept JPA entities in controllers. Map with the hand-written mappers.
- **Constructor injection only** (Lombok `@RequiredArgsConstructor` on final fields). No field `@Autowired`.
- **Schema changes only through Flyway** migrations in `src/main/resources/db/migration`. `ddl-auto` stays `validate`. Never edit a migration that has been applied; add a new `V<n>__description.sql`.
- **Test every service method** with a mocked unit test. Add an integration test when the behaviour depends on real SQL or the security filter chain.
- **Meaningful names** for variables, parameters, query parameters and columns. No abbreviations such as `q` or `cb`; write `searchText`, `criteriaBuilder`.
- **Every query filters by the current user.** Get the user id only from `CurrentUserProvider` (the JWT subject), never from a URL or request body. Another user's record must look like 404.
- **Comments explain why, not what**, and every class has a short comment stating its role. Schema documentation lives in `COMMENT ON` statements (see `V3__schema_comments.sql`).
- Entities use Lombok `@Getter`/`@Setter` (not `@Data`), lazy `@ManyToOne`, and no `@OneToMany` collections.

## API conventions

- Base path `/api/v1`. Everything needs a Bearer token except `/api/v1/auth/**` and the Swagger paths.
- POST returns 201 with a `Location` header and the created object; PUT returns 200 with the updated object; DELETE returns 204.
- Errors are RFC 9457 `ProblemDetail` from `GlobalExceptionHandler`: 400 validation (with an `errors` map), 401 bad credentials, 404 not found, 409 conflict or stale `version`.
- `PUT /applications/{id}` requires the `version` last read (optimistic locking). Status and position change only through `PATCH /applications/{id}/move`.
- Lists return `PageResponse`; sortable fields are whitelisted in `ApplicationService`.

## Things that are easy to trip over

- The JVM runs in UTC (set in `main` and in the surefire config). Windows reports the zone as `Asia/Calcutta`, which the `postgres:16` image rejects on connect.
- A locally installed PostgreSQL service on port 5432 hides the Docker container and causes "password authentication failed". Stop the Windows service before starting the container.
- `spring.docker.compose.enabled` is `false`: Docker is started manually, the app never starts containers.
- The seeded demo user (id 1) has a placeholder password hash and cannot log in.

## Not built yet

Refresh tokens, resumes and AI match (`resumes` and `match_results` tables exist but have no entities), follow-up reminder emails, the Angular frontend, CORS configuration.
