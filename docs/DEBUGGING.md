# Debugging guide

Find your symptom, apply the fix. For how the code is meant to work, see [ARCHITECTURE.md](ARCHITECTURE.md).

Commands are Windows PowerShell. The database container is assumed to be named `jobtrackrproject-postgres-1`; check with `docker ps` if yours differs.

## 1. Thirty-second health check

```powershell
docker ps                                   # is the postgres:16 container Up?
Get-NetTCPConnection -LocalPort 5432 -State Listen | ForEach-Object { (Get-Process -Id $_.OwningProcess).ProcessName }
                                            # should list only com.docker.backend
Test-Path "E:\JobTrackr project\jobtrackr-backend\.env.properties"   # local JWT_SECRET file present?
```

## 2. The app does not start

Read the **last** `Caused by:` in the stack trace; that is the real reason.

| Message in the log | Cause | Fix |
|---|---|---|
| `Binding to target ...JwtProperties failed`, `Value: "${JWT_SECRET}"`, `must be at least 32 characters` (or `Could not resolve placeholder 'JWT_SECRET'`) | `JWT_SECRET` is **not set** for this run: Spring left the placeholder as literal text, which then fails the length check | Create `jobtrackr-backend/.env.properties` with a `JWT_SECRET=...` line (copy `.env.example` from the repository root, or use the command in the README). If it exists, check the run's working directory is `jobtrackr-backend`: the file is found relative to it |
| `jobtrackr.jwt.secret ... must be at least 32 characters` with your real value shown | The secret is too short for HS256 | Use a value of at least 32 characters |
| `Connection to localhost:5432 refused` | The database container is not running | `docker compose up -d` from the monorepo root |
| `password authentication failed for user "jobtrackr"` | A locally installed PostgreSQL service owns port 5432, so the app is talking to the wrong server | In an administrator PowerShell: `Stop-Service postgresql-x64-18` (use your version), then `docker compose down` and `docker compose up -d` |
| `password authentication failed`, and no local PostgreSQL | The data volume was first created with different credentials | `docker compose down -v` then `docker compose up -d`. This deletes all data |
| `invalid value for parameter "TimeZone": "Asia/Calcutta"` | The JVM is not running in UTC | Start through `JobtrackrBackendApplication.main` or Maven; both set UTC. For any other launcher add `-Duser.timezone=UTC` |
| `Schema-validation: missing table` / `missing column` / `wrong column type` | An entity does not match the tables | Compare the entity's `@Column` with the migration. Fix the entity, or add a new migration. Never set `ddl-auto` to `update` |
| `Migration checksum mismatch for migration version N` | A migration file was edited after it was applied | Revert the edit and put the change in a new `V<n+1>__*.sql`. On a throwaway local database only: `docker compose down -v` |
| `Found non-empty schema(s) "public" but no schema history table` | The database has tables Flyway did not create | Local: `docker compose down -v` and start again |
| `Port 8080 was already in use` | Another copy of the app is running | Stop it, or add `--server.port=8081` |
| `cannot find symbol ... getId()` at compile time | Lombok annotation processing is off | IntelliJ: enable annotation processing and install the Lombok plugin; then reload Maven |

## 3. A request returns an unexpected status

### 401

| Situation | Cause |
|---|---|
| Empty response body | The security filter rejected the token before any controller ran. Look at the `WWW-Authenticate` response header: it states the reason (expired, invalid signature, malformed) |
| No `Authorization` header sent | In Swagger, click **Authorize** and paste the token without the word "Bearer" |
| It worked 15 minutes ago (API called directly, e.g. Swagger) | The access token expired. Log in again, or call `POST /api/v1/auth/refresh` from the browser (the cookie is needed). The frontend does this automatically |
| `POST /auth/refresh` returns 401 | No cookie (logged out, cookie expired after 14 days, or blocked), the token was already used (rotation), or it was revoked by logout or theft detection | Log in again. In DevTools > Application > Cookies > `http://localhost:8080`, check `jobtrackr_refresh` exists |
| Refresh works in Swagger but not from the app | The cookie is not sent cross-origin | Requests to `/auth/*` must use `withCredentials: true`, and CORS must answer `Access-Control-Allow-Credentials: true` (see `CorsConfig`) |
| Logged out of every tab/device at once | Theft detection: a refresh token that had already been rotated was presented again later | Expected behaviour; log in again. If it happens without reason, check for something replaying old cookies |
| It worked before the last restart | `JWT_SECRET` changed between runs; old tokens no longer verify. Log in again |
| Body says `Invalid email or password` | That is the login endpoint. The same message is used for an unknown email on purpose. The demo account's password is `Demo@1234`; if that fails, check that `V4__demo_user_data.sql` has run (`flyway_schema_history`) |

### 403

`PUT /me/password` for the demo account: its password is published and shared, so it cannot be changed. Register your own account. (A missing or bad token is a 401, never a 403.)

### Password-reset email does not arrive

`POST /auth/forgot-password` always answers 204, so the answer tells you nothing; check in this order:

| Check | Why |
|---|---|
| Open http://localhost:8025 | Locally every email goes to Mailpit, never to a real inbox |
| `docker ps` lists `axllent/mailpit` | If Mailpit is down the send fails in the background: the log shows `Password-reset email for user N could not be sent: MailSendException` and the request still answers 204 |
| The address is registered (lower case) and is not `demo@jobtrackr.dev` | Unknown addresses and the demo account silently get no email, on purpose |
| A minute has passed since the last request | `resend-cooldown`: a second request within 1 minute sends nothing |

A link that "doesn't work" was already used, is older than 30 minutes, or was replaced by a newer request (only the newest link works). Request a new one.

### 404 on a record you can see in the database

The record belongs to a different user. Every lookup includes `user_id` (see ARCHITECTURE, section 5). Compare the token's subject with the row:

```powershell
docker exec jobtrackrproject-postgres-1 psql -U jobtrackr -d jobtrackr -c "SELECT id, user_id, company, status, position FROM applications WHERE id = 42;"
```

To see whose token you hold, call `GET /api/v1/me`.

### 409

| Endpoint | Cause | Fix |
|---|---|---|
| `PUT /applications/{id}` | The `version` in the body is older than the one in the database. A move also increments the moved card's version | Re-read the card and send its current `version` |
| `POST /auth/register` | The email is already registered (compared in lower case) | Log in instead |

### 400

- Body has an `errors` map: Bean Validation rejected those fields. The rules are the annotations on the request record in the feature's `dto` package.
- Body has no `errors` map: the JSON could not be read at all. Typical causes are an unknown status (`"HIRED"`), a date not in `yyyy-MM-dd` form, a trailing comma, or text in a number field.
- `Cannot sort by '...'`: only the fields listed in `ApplicationService.SORTABLE_FIELDS` are allowed.
- `PUT /me/password` with `errors.currentPassword: "Current password is incorrect"`: a 400 on purpose, not a 401 (a 401 would make the frontend try a token refresh).

### CORS error in the browser console

`blocked by CORS policy` means the browser refused to show the response to JavaScript; the request may well have reached the server.

| Message | Cause | Fix |
|---|---|---|
| `No 'Access-Control-Allow-Origin' header is present` | The page's origin is not in the allowed list | Add it to `CORS_ALLOWED_ORIGINS` (comma-separated; exact scheme, host and port; no trailing slash) and restart |
| Preflight returns 401 | CORS is not enabled in the security chain | `SecurityConfig` must call `.cors(Customizer.withDefaults())` |
| Preflight returns 403 | The origin, method or a request header is not allowed | Compare with `CorsConfig`: only the `Authorization` and `Content-Type` request headers are allowed |
| The app cannot read the `Location` header of a 201 | The header is not exposed | `CorsConfig.setExposedHeaders` |

Test a preflight without a browser:

```powershell
Invoke-WebRequest -UseBasicParsing -Method Options http://localhost:8080/api/v1/applications -Headers @{ Origin = 'http://localhost:4200'; 'Access-Control-Request-Method' = 'GET' }
```

### 500

An exception with no handler. The stack trace is in the application log. Find the first line that starts with `com.jobtrackr`; that is where to put the breakpoint.

## 4. Where to put a breakpoint

| Symptom | Start here |
|---|---|
| Wrong card order after a drag | `ApplicationService.move`; watch `sourcePosition`, `targetPosition`, `targetCount` |
| A gap or duplicate after create or delete | `ApplicationService.create` / `delete` |
| Unexpected 409 | `ApplicationService.update`, the version comparison |
| Wrong user's data, or 404 that should be 200 | `CurrentUserProvider.getUserId` |
| Filter or search returns the wrong rows | `ApplicationSpecifications`; then turn on SQL logging (section 5) |
| Login fails with correct details | `AuthService.login`; check the normalised email and that `password_hash` starts with `$2` |
| A field is missing or null in the response | `ApplicationMapper`: was the field added to `toResponse`, `toEntity` and `applyUpdate`? |
| A validation message is wrong | The annotations on the request record; then `GlobalExceptionHandler.handleMethodArgumentNotValid` |
| The bell shows the wrong follow-ups or count | `NotificationService.getNotifications`; check `today` (decided in `notifications.zone`) and the rows from `findOpenFollowUps` |
| Old refresh-token or reset-link rows are not cleaned up | `ExpiredTokenCleanupJob.deleteExpiredTokens`; the log prints `Token cleanup deleted N expired refresh token(s) and M expired reset link(s)` each night. No line at all: `jobtrackr.scheduling.enabled` is false |

## 5. See what the app is doing

Add these as program arguments (IntelliJ: Run configuration, Program arguments; Maven: `-Dspring-boot.run.arguments="..."`):

| Argument | Shows |
|---|---|
| `--logging.level.org.hibernate.SQL=debug` | Every SQL statement |
| `--logging.level.org.hibernate.orm.jdbc.bind=trace` | The values bound to each `?` |
| `--logging.level.org.springframework.security=debug` | Why a request was authenticated or rejected |
| `--logging.level.org.flywaydb=debug` | Which migrations Flyway found and ran |
| `--debug` | Spring Boot's report of which auto-configuration applied |

## 6. Look inside the database

Open a SQL prompt in the container:

```powershell
docker exec -it jobtrackrproject-postgres-1 psql -U jobtrackr -d jobtrackr
```

Useful statements:

```sql
\dt                                  -- list tables
\d+ applications                     -- columns, constraints and the comments from V3

-- Which migrations have run
SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank;

-- One user's board exactly as the API orders it
SELECT id, status, position, company, version FROM applications WHERE user_id = 1 ORDER BY status, position;
```

### Check that positions are healthy

Each column must read `{0,1,2,...}` with no gaps or repeats:

```sql
SELECT user_id, status, array_agg(position ORDER BY position) AS positions
FROM applications
GROUP BY user_id, status
ORDER BY user_id, status;
```

### Repair broken positions

Renumbers every column from 0, keeping the current order. Safe to run more than once:

```sql
UPDATE applications a
SET position = r.new_position
FROM (
  SELECT id, row_number() OVER (PARTITION BY user_id, status ORDER BY position, id) - 1 AS new_position
  FROM applications
) r
WHERE a.id = r.id AND a.position <> r.new_position;
```

## 7. Read a token

A JWT is three Base64 parts separated by dots; the middle part is the payload. To decode it without sending the token to a website:

```powershell
$token = "<paste the accessToken>"
$part = $token.Split('.')[1].Replace('-', '+').Replace('_', '/')
$part += '=' * ((4 - $part.Length % 4) % 4)
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($part))
```

`sub` is the user id, `exp` is the expiry as seconds since 1970 (UTC).

## 8. Tests fail

| Symptom | Cause |
|---|---|
| Every `@SpringBootTest` fails with a connection error | The Docker database is not running. Unit tests still pass |
| `Could not resolve placeholder 'JWT_SECRET'` in a test | The test class is missing `@ActiveProfiles("test")` |
| An integration test reads old positions after a move or delete | The whole test shares one transaction and one persistence context, and bulk updates bypass it. Call `entityManager.flush(); entityManager.clear();` before reading (see `sync()` in `ApplicationApiIntegrationTest`) |
| `UnnecessaryStubbingException` | A `when(...)` in the test is never used by that test. Remove it, or mark shared setup stubs `lenient()` |
| Tests pass in Maven but fail in IntelliJ with the time zone error | IntelliJ did not import the surefire `argLine`. Reload the Maven project, or add `-Duser.timezone=UTC` to the JUnit run configuration template |

## 9. Frontend problems

| Symptom | Cause | Fix |
|---|---|---|
| `npm : File ... npm.ps1 cannot be loaded because running scripts is disabled` | PowerShell execution policy | Use `npm.cmd`, or once: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` |
| `ng` refuses to run: Node version not supported | Angular 22 needs Node 22.22.3+ or 24.15+ | `winget install OpenJS.NodeJS.LTS`, then reopen the terminal |
| Login page says "Can't reach the server" | Backend not running, or `apiBaseUrl` in `src/environments` points elsewhere | Start the backend; check the URL |
| Browser console: `blocked by CORS policy` | The page's origin is not allowed | See "CORS error in the browser console" above; the default allows only `http://localhost:4200` |
| Every page bounces to /login with "session expired" | The stored token no longer verifies (backend secret changed, or the user was deleted) | Log in again; the interceptor already cleared the bad token |
| Board shows "Couldn't load your board" | `GET /applications/board` failed | Network tab shows the status; press Retry once fixed |
| A dragged card snaps back with "Couldn't move ..." | `PATCH /move` failed with a non-409 error | Network tab; on 404/409 the board reloads instead |
| Edit dialog shows "This card was changed elsewhere" | 409: another tab saved the card first | Expected. The form now holds the latest version; edit and save again |
| Styling on a Material component has no effect | Tailwind utilities are layered and lose to Material | Put the utility on a wrapper element, or use a `mat.*-overrides` mixin |
| The bell does not list a card whose follow-up is due | Only APPLIED and INTERVIEW cards are listed; or it was dismissed (`reminder_dismissed_on >= follow_up_on`); or "today" differs because the server decides it in `notifications.zone` | Check the row in psql. Changing the follow-up date brings it back; `update applications set reminder_dismissed_on = null where id = ...` resets it |
| `ng build` warns about the bundle budget | Something heavy was imported eagerly | Load it lazily (see `core/notifications/notifier.ts` and the dialog's own providers) |

Useful tools: the Angular DevTools browser extension (component tree and signal values), and DevTools > Network > right-click a request > **Block request URL** to rehearse failure paths.

## 10. The commit is rejected

`git commit` prints `✖ ... [type-enum]` or similar: the message does not follow the format. See [CONTRIBUTING.md](../CONTRIBUTING.md). If no check runs at all, the hook is not installed: run `npm install` in the monorepo root.
