# JobTrackr

[![CI](https://github.com/varsha-k-karthikeyan/jobtrackr/actions/workflows/ci.yml/badge.svg)](https://github.com/varsha-k-karthikeyan/jobtrackr/actions/workflows/ci.yml)

A job application tracker: a Kanban board for your applications (Applied, Interview, Offer, Rejected), notes per application, follow-up dates with in-app reminders, and a searchable list.

This is a monorepo with the Spring Boot backend and the Angular frontend side by side.

| Path | What it is |
|---|---|
| `jobtrackr-backend/` | REST API: Spring Boot 4.1, Java 21, Maven |
| `jobtrackr-frontend/` | Angular 22 app: Kanban board, list, details drawer, stats, follow-up bell (Angular Material + Tailwind CSS; see its README) |
| `docker-compose.yml` | PostgreSQL 16 for local development |
| `docs/` | Architecture and debugging guides |
| `commitlint.config.js`, `.husky/`, `package.json` | Commit message linting (repository tooling only) |
| `CLAUDE.md` | Working rules for AI coding agents; also a good one-page summary for people |

## Documentation

| Read this | When you want to |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Understand the code structure, the data model and how the main logic works |
| [docs/DEBUGGING.md](docs/DEBUGGING.md) | Fix a startup failure, an unexpected status code, or wrong card positions |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Add a feature or a migration, and write commit messages that pass the lint |
| [jobtrackr-frontend/README.md](jobtrackr-frontend/README.md) | Understand the Angular app: structure, state flow, authentication, styling rules |

## Prerequisites

- JDK 21
- Docker Desktop
- Node.js 22.22.3+ or 24.15+ (the Angular 22 frontend; the commit message hook also runs on it)

## Run everything in Docker

The whole app (PostgreSQL, Mailpit, the Spring Boot API and the Angular app behind nginx) in containers. Needs only Docker Desktop.

```powershell
cd "E:\JobTrackr project"
Copy-Item .env.example .env     # once; then put your own random JWT_SECRET in .env
docker compose --profile full up --build
```

Open http://localhost:4200 and click **Try the demo**. Swagger UI is at http://localhost:8080/swagger-ui.html and emails at http://localhost:8025. Stop with `Ctrl+C`, or `docker compose --profile full down` (keeps the data).

nginx serves the app and forwards `/api/` to the backend, so the browser talks to one origin: the refresh cookie is first-party and no CORS is involved. Plain `docker compose up -d` still starts only PostgreSQL and Mailpit, for development.

## Quick start (development)

Windows PowerShell. Quote the path because it contains a space.

```powershell
# 1. Start the database and Mailpit (the local mail catcher)
cd "E:\JobTrackr project"
docker compose up -d

# 2. Install the commit hook (once per clone)
npm install

# 3. Create the local secrets file once (see below), then start the backend
cd "E:\JobTrackr project\jobtrackr-backend"
.\mvnw.cmd spring-boot:run        # or run JobtrackrBackendApplication in IntelliJ
```

### The JWT secret

The backend needs `JWT_SECRET`, any random string of at least 32 characters, to sign login tokens. For local development, put it in `jobtrackr-backend/.env.properties`, which Spring reads at startup and git ignores. It works the same from IntelliJ, Maven or a terminal. Create it once:

```powershell
cd "E:\JobTrackr project\jobtrackr-backend"
$bytes = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
"JWT_SECRET=$([Convert]::ToBase64String($bytes))" | Out-File -Encoding ascii .env.properties
```

A real `JWT_SECRET` environment variable, if set, takes priority over the file; that is how a deployed server would provide it. Keep the value stable: changing it logs out everyone who holds a token.

Every variable the backend reads is listed, with placeholders, in [`.env.example`](.env.example) at the repository root. Only `JWT_SECRET` is required; the others (`CORS_ALLOWED_ORIGINS`, `REFRESH_COOKIE_SECURE`, `NOTIFICATIONS_ZONE`, `SCHEDULING_ENABLED`, `TOKEN_CLEANUP_CRON`, `APP_URL`, `MAIL_FROM` and the `MAIL_*` server settings) have working defaults. Real values never go in that file: `.env` and `.env.properties` are git-ignored, `.env.example` is not.

#### Environment variables in an IntelliJ run configuration (Windows)

Use this instead of the file, or to override it:

1. **Run > Edit Configurations...** and select `JobtrackrBackendApplication`. If it is missing, click **+**, choose **Spring Boot** (or **Application** in the Community edition), set the main class to `com.jobtrackr.jobtrackrbackend.JobtrackrBackendApplication` and the module to `jobtrackr-backend`.
2. Set **Working directory** to `E:\JobTrackr project\jobtrackr-backend`. The `.env.properties` file is looked up relative to it, so with the monorepo root as the working directory the file is silently skipped and startup fails on the missing `JWT_SECRET`.
3. In **Environment variables** (if the field is hidden: **Modify options > Environment variables**), click the icon at the right end of the field, then **+**, and add `JWT_SECRET` with your value. Typed directly, the format is `JWT_SECRET=...;CORS_ALLOWED_ORIGINS=http://localhost:4200`, with entries separated by semicolons.
   Alternatively, copy `.env.example` to `.env`, fill it in, and select that file with the field's folder icon (recent IntelliJ versions load `.env` files there).
4. Leave **Store as project file** unchecked. Run configurations then live in `.idea/`, which is git-ignored; a stored one would be written to `.run/` and could be committed together with your secret.
5. **Apply**, then run. A missing secret fails at startup with `jobtrackr.jwt.secret` in the error.

Then open Swagger UI at http://localhost:8080/swagger-ui.html:

1. Call `POST /api/v1/auth/register` (or `/login`) and copy `accessToken` from the response.
2. Click **Authorize**, paste the token (without the word "Bearer").
3. Every other endpoint now works.

### Emails (forgot password)

"Forgot password?" emails a reset link. Locally nothing is really delivered: the backend sends to **Mailpit**, a mail catcher started by `docker compose up -d`, and you read the email in its inbox at http://localhost:8025. No account is needed. The link works once and expires after 30 minutes.

To deliver real email instead, point the `MAIL_*` variables in `.env.example` at a provider; no code changes.

### The app

With the backend running:

```powershell
cd "E:\JobTrackr project\jobtrackr-frontend"
npm install        # first time only
npm start
```

Open http://localhost:4200 and click **Try the demo**, or register your own account. Node 22.22.3+ or 24.15+ is required (Angular 22).

## Tests

```powershell
cd "E:\JobTrackr project\jobtrackr-backend"
.\mvnw.cmd verify
```

Docker Desktop must be running, but the compose database is **not** needed: the integration tests start their own throwaway PostgreSQL 16 with [Testcontainers](https://testcontainers.com/) on a random port (the first run downloads the image). Integration tests run inside transactions that are rolled back, so they leave no data behind. Tests use their own dummy JWT secret and need no environment variable, and background jobs are switched off during tests.

`verify` also writes a coverage report to `jobtrackr-backend/target/site/jacoco/index.html` (open it in a browser: `start target\site\jacoco\index.html`).

Every push and pull request to `main` runs the same checks on GitHub Actions (`.github/workflows/ci.yml`): backend `mvnw verify`, frontend tests and production build, both Docker images, and commit messages on pull requests.

Frontend unit tests (Vitest) need neither the backend nor the database:

```powershell
cd "E:\JobTrackr project\jobtrackr-frontend"
npm run test:ci
```

## API at a glance

Base path `/api/v1`. Everything except `/auth/**` needs `Authorization: Bearer <token>`.

| Method and path | Purpose |
|---|---|
| `POST /auth/register` | Create an account; returns a 15-minute access token and sets the refresh-token cookie |
| `POST /auth/login` | Returns a 15-minute access token and sets the 14-day refresh-token cookie (httpOnly) |
| `POST /auth/refresh` | No body: the browser sends the cookie. Rotates the refresh token and returns a new access token |
| `POST /auth/logout` | Revokes the refresh token and deletes the cookie |
| `POST /auth/forgot-password` | Emails a single-use, 30-minute reset link. Always 204, so it never reveals whether an address is registered |
| `POST /auth/reset-password` | Sets a new password from the link's token and ends every session; 400 if the link is used, expired or unknown |
| `GET /me` | The caller's profile |
| `PUT /me/password` | Change the password (`currentPassword`, `newPassword`). Ends every session and returns a fresh one for this browser; 400 on a wrong current password, 403 for the demo account |
| `GET /applications/board` | All cards grouped by status, ordered by position |
| `GET /applications?status=&searchText=&salaryFrom=&salaryTo=&sort=&page=&size=` | Filtered, sorted, paginated list. `searchText` matches company, role, location, source or status; `salaryFrom`/`salaryTo` (LPA) keep jobs whose salary range overlaps that range; `sort` accepts company, role, status, location, salaryMinLpa, salaryMaxLpa, appliedOn (and a few more), empty values last |
| `POST /applications` | Create a card (appended to the bottom of its column) |
| `GET /applications/{id}` | One card with its notes |
| `PUT /applications/{id}` | Replace the editable fields; needs the current `version`. A new `status` moves the card to the bottom of that column |
| `PATCH /applications/{id}/move` | Move a card to a column and position |
| `DELETE /applications/{id}` | Delete a card |
| `POST /applications/{id}/notes` | Add a note |
| `DELETE /notes/{id}` | Delete a note |
| `GET /stats` | Counts per status, total and response rate |
| `GET /notifications` | Follow-ups for the bell: `due` (today or overdue) and `upcoming` (next 7 days), Applied and Interview cards only |
| `DELETE /notifications/{applicationId}` | Dismiss one due follow-up (until its date changes); `DELETE /notifications` dismisses all |

Swagger UI is the full, always-current reference.

## Demo account

Log in with **demo@jobtrackr.dev** / **Demo@1234** to explore a board with 16 sample applications, notes and follow-up dates. The account is created by the Flyway migration `V4__demo_user_data.sql`.

The data is not stored in this repository: the migration recreates it in your own local database, so every setup gets its own private copy. Changes you make to the demo board stay on your machine. Dates are calculated from the day the migration runs, so the board always shows recent applications and follow-ups that are due.

The password is published here on purpose, so use the demo account for exploring and register your own account for real use.

To reset the demo data to its original state (this also deletes any accounts you registered locally):

```powershell
cd "E:\JobTrackr project"
docker compose down -v
docker compose up -d
```

Then start the backend; Flyway rebuilds the database from the migrations.

## Status

Built:
- Backend: Kanban API (CRUD, move, notes), stats, follow-up notifications, validation and error handling, JWT authentication with nightly cleanup of expired sessions, password change and "forgot password" by email (caught locally by Mailpit), CORS, a public demo account, tests.
- Frontend: login/register with "Try the demo", drag-and-drop board with optimistic updates, create/edit dialog with 409 handling, details drawer with notes, searchable server-side list with the state in the URL, stats strip, follow-up bell in the header, change-password dialog, forgot/reset password pages, tests.

Sessions: a 15-minute access token plus a 14-day refresh token in an httpOnly cookie, rotated on every use, so you stay signed in while you keep using the app (see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), section 8).

Not built: deployment. Out of scope by decision: AI resume match. Email is used only for password-reset links and goes to the local Mailpit inbox; follow-up reminders are in-app only, through the bell.
