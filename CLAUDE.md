# JobTrackr

Job application tracker: Kanban board, searchable list and in-app follow-up reminders. Monorepo; the git repository is at this root, not inside `jobtrackr-backend`.

The owner is a senior Angular developer learning Spring Boot. After each piece of work, briefly explain the key design decisions, because the code must be explained in interviews. Work one stage at a time, say exactly how to verify it, and wait for "next".

## Where to look

- `docs/ARCHITECTURE.md`: code structure, data model, and how the move, optimistic-locking and auth logic work. Read it before changing code.
- `docs/DEBUGGING.md`: symptom-to-fix tables for startup failures, status codes, positions and tests.
- `CONTRIBUTING.md`: commit format and step-by-step recipes (add a column, an endpoint, a feature).
- `jobtrackr-frontend/README.md`: frontend structure, state flow and conventions.

Keep these in step with the code: a change to behaviour, configuration or structure updates the matching document in the same commit.

## Commits

Commit messages are linted by commitlint through a husky `commit-msg` hook (`commitlint.config.js`). Format: `<type>(<scope>): <subject>`, for example `feat(notification): add follow-up notifications for the header bell`. The allowed types and scopes are listed in `CONTRIBUTING.md`. Subject in lower case, imperative, no full stop, header at most 100 characters. Do not bypass the hook with `--no-verify`.

## Stack

- Backend: `jobtrackr-backend` — Spring Boot 4.1.x, Java 21, Maven (use the wrapper, `mvnw.cmd`)
- Web MVC, Spring Data JPA (Hibernate), Bean Validation, Flyway, Spring Security with the OAuth2 resource server (JWT, HS256), Actuator, Lombok
- springdoc-openapi 3.x for Swagger UI (the 3.x line is the one for Boot 4; its version is pinned in `pom.xml`)
- PostgreSQL 16 in Docker (`docker-compose.yml` at this root)
- Tests: JUnit 5, Mockito, AssertJ, MockMvc
- Frontend: `jobtrackr-frontend` — Angular 22 (standalone, zoneless, OnPush by default), Angular Material 22 + CDK, Tailwind CSS 4, Vitest. Needs Node 22.22.3+ or 24.15+
- Base package: `com.jobtrackr.jobtrackrbackend`

## Run commands

Windows, PowerShell. Quote paths that contain spaces.

```powershell
# 1. Database (from the monorepo root)
cd "E:\JobTrackr project"
docker compose up -d

# 2. Backend. JWT_SECRET (min 32 chars) comes from jobtrackr-backend/.env.properties
#    (git-ignored, imported by application.yml); a real environment variable overrides it.
#    Every variable, with placeholders only, is listed in .env.example at the root (committed on purpose).
cd "E:\JobTrackr project\jobtrackr-backend"
.\mvnw.cmd spring-boot:run        # or: mvn spring-boot:run

# 3. Tests (the Docker database must be running; tests use their own dummy JWT secret)
.\mvnw.cmd test

# 4. Frontend (the backend must be running on :8080)
cd "E:\JobTrackr project\jobtrackr-frontend"
npm install        # first time only (use npm.cmd if PowerShell blocks npm.ps1)
npm start          # http://localhost:4200
npm test           # unit tests (Vitest)
```

- Demo login: `demo@jobtrackr.dev` / `Demo@1234` (or "Try the demo" on the login page)

- Swagger UI: http://localhost:8080/swagger-ui.html (register or log in, then use Authorize with the access token)
- `docker compose down` keeps the data; `docker compose down -v` wipes it

## Working rules

- **Package by feature**, not by layer: `common`, `user`, `application`, `note`, `auth`, `stats`, `notification`. Each feature holds its own entity, repository, service, controller, mapper and `dto` package.
- **DTOs are Java records.** Never return or accept JPA entities in controllers. Map with the hand-written mappers.
- **Constructor injection only** (Lombok `@RequiredArgsConstructor` on final fields). No field `@Autowired`.
- **Schema changes only through Flyway** migrations in `src/main/resources/db/migration`. `ddl-auto` stays `validate`. Never edit a migration that has been applied; add a new `V<n>__description.sql`.
- **Test every service method** with a mocked unit test. Add an integration test when the behaviour depends on real SQL or the security filter chain.
- **Meaningful names** for variables, parameters, query parameters and columns. No abbreviations such as `q` or `cb`; write `searchText`, `criteriaBuilder`.
- **Every query filters by the current user.** Get the user id only from `CurrentUserProvider` (the JWT subject), never from a URL or request body. Another user's record must look like 404. The only exception is a scheduled job's housekeeping query (`RefreshTokenRepository.deleteExpiredBefore`), never reachable from a controller.
- **Comments explain why, not what**, and every class has a short comment stating its role. Schema documentation lives in `COMMENT ON` statements (see `V3__schema_comments.sql`).
- Entities use Lombok `@Getter`/`@Setter` (not `@Data`), lazy `@ManyToOne`, and no `@OneToMany` collections.

## Frontend rules

- **Standalone components only**, no NgModules. `inject()` instead of constructor injection. Strict TypeScript, no `any`.
- **Signals for state** (`signal`, `computed`; `effect` sparingly). RxJS only where it fits: HTTP and debouncing.
- **New control flow** (`@if`, `@for`, `@switch`, `@let`). Code must work with OnPush (the Angular 22 default) and zoneless.
- **Functional interceptors and guards** (`core/interceptors`, `core/guards`). **Typed reactive forms.** Lazy routes with `loadComponent`.
- **UI: Angular Material (+ CDK) and Tailwind CSS only**, no other UI library. Material for interactive widgets; Tailwind for layout. Tailwind sits in cascade layers and loses to Material on Material elements, so put utilities on a plain wrapper, never on a Material component, and colour custom pieces with the `--mat-sys-*` theme variables or `mat.*-overrides` mixins.
- **Folders:** `core` (api, auth, interceptors, guards, models, state, notifications (snackbar `Notifier` and the bell's `NotificationStore`), layout), `features/{auth,board,list,stats}`, `shared` (components, validators, utils).
- **State:** board state lives in `ApplicationStore` (immutable updates via the pure functions in `board-state.ts`; mutations return Promises). Components never call `HttpClient` directly; the list page is the only direct caller of `ApplicationApiService` (`list()`).
- **Sessions:** 15-minute access token (localStorage, `Authorization` header) + 14-day refresh token in an httpOnly cookie that JavaScript never sees. All `/auth/*` calls use `withCredentials: true`. On a 401 the auth interceptor does one shared refresh and one retry; guards restore the session after a reload. Never put the refresh token anywhere JavaScript can read.
- **Errors:** the error interceptor shows one snackbar per failed request; a caller that shows the error itself passes `{ skipErrorToast: true }` (`SKIP_ERROR_TOAST`). 401s are owned by the auth interceptor.
- **Model types mirror the backend records field for field** (`core/models`); when a DTO changes, update both in the same commit.
- **No secrets in the frontend.** `apiBaseUrl` comes from `src/environments`. The demo credentials constant is public on purpose.
- **Form field spacing:** stack `mat-form-field`s with `gap-4` (16px) on the plain wrapper (`flex flex-col gap-4`, or `gap-y-4` in a grid). An outlined field's floating label sits above its box, so anything tighter makes an error or hint touch the next label. Keep hints to one line.
- Keep the initial bundle under the 500 kB budget: anything only needed on demand (snackbar from interceptors, datepicker setup) is loaded lazily.

## API conventions

- Base path `/api/v1`. Everything needs a Bearer token except `/api/v1/auth/**` and the Swagger paths.
- POST returns 201 with a `Location` header and the created object; PUT returns 200 with the updated object; DELETE returns 204.
- Errors are RFC 9457 `ProblemDetail` from `GlobalExceptionHandler`: 400 validation (with an `errors` map; services add field errors with `FieldValidationException`), 401 bad credentials or token, 403 forbidden (the demo account's password), 404 not found, 409 conflict or stale `version`. A form-input mistake is never a 401: the frontend treats 401 as an expired session.
- `PUT /applications/{id}` requires the `version` last read (optimistic locking). Its optional `status` moves the card to the bottom of that column in the same transaction (null keeps it). An exact position is only set through `PATCH /applications/{id}/move` (drag and drop).
- Lists return `PageResponse`; sortable fields are whitelisted in `ApplicationService`.

## Things that are easy to trip over

- The JVM runs in UTC (set in `main` and in the surefire config). Windows reports the zone as `Asia/Calcutta`, which the `postgres:16` image rejects on connect.
- A locally installed PostgreSQL service on port 5432 hides the Docker container and causes "password authentication failed". Stop the Windows service before starting the container.
- `spring.docker.compose.enabled` is `false`: Docker is started manually, the app never starts containers.
- Scheduled jobs (`RefreshTokenCleanupJob`) run only when `jobtrackr.scheduling.enabled` is true (the default); `application-test.yml` sets it to false so nothing fires during tests.
- Public demo account `demo@jobtrackr.dev` / `Demo@1234` (user id 1), created by V2 and given a real hash and sample data by `V4__demo_user_data.sql`. The password is published in the repo on purpose; the app is not hosted, so each local database has its own copy (reset with `docker compose down -v`). An integration test relies on it being able to log in.
- CORS origins come from `CORS_ALLOWED_ORIGINS` (comma-separated, default `http://localhost:4200`). CORS is wired into the security chain (`http.cors(...)`), otherwise preflight requests get 401.

## Not built yet

Deployment. Out of scope by decision: AI resume match (its tables were dropped in V8) and email (follow-up reminders are in-app only: the header bell, `notification` package; there is deliberately no SMTP).
