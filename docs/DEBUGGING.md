# Debugging guide

Find your symptom, apply the fix. For how the code is meant to work, see [ARCHITECTURE.md](ARCHITECTURE.md).

Commands are Windows PowerShell. The database container is assumed to be named `jobtrackrproject-postgres-1`; check with `docker ps` if yours differs.

## 1. Thirty-second health check

```powershell
docker ps                                   # is the postgres:16 container Up?
Get-NetTCPConnection -LocalPort 5432 -State Listen | ForEach-Object { (Get-Process -Id $_.OwningProcess).ProcessName }
                                            # should list only com.docker.backend
echo $env:JWT_SECRET                        # set? (terminal runs only; IntelliJ has its own setting)
```

## 2. The app does not start

Read the **last** `Caused by:` in the stack trace; that is the real reason.

| Message in the log | Cause | Fix |
|---|---|---|
| `Could not resolve placeholder 'JWT_SECRET'` | The environment variable is not set | Terminal: `$env:JWT_SECRET = "..."`. IntelliJ: Run configuration, Environment variables |
| `jobtrackr.jwt.secret ... must be at least 32 characters` | The secret is too short for HS256 | Use a longer value |
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
| It worked 15 minutes ago | The token expired. Log in again |
| It worked before the last restart | `JWT_SECRET` changed between runs; old tokens no longer verify. Log in again |
| Body says `Invalid email or password` | That is the login endpoint. The same message is used for an unknown email on purpose. The seeded demo user (id 1) can never log in |

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

## 9. The commit is rejected

`git commit` prints `✖ ... [type-enum]` or similar: the message does not follow the format. See [CONTRIBUTING.md](../CONTRIBUTING.md). If no check runs at all, the hook is not installed: run `npm install` in the monorepo root.
