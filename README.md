# JobTrackr

A job application tracker: a Kanban board for your applications (Applied, Interview, Offer, Rejected), notes per application, and (planned) an AI resume-to-job-description match.

This is a monorepo. Today it contains the Spring Boot backend; the Angular frontend will be added next to it.

| Path | What it is |
|---|---|
| `jobtrackr-backend/` | REST API: Spring Boot 4.1, Java 21, Maven |
| `jobtrackr-frontend/` | Angular 22 app: Kanban board, list, details drawer, stats (Angular Material + Tailwind CSS; see its README) |
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

## Prerequisites

- JDK 21
- Docker Desktop
- Node.js 20 or newer (only for the commit message hook)

## Quick start

Windows PowerShell. Quote the path because it contains a space.

```powershell
# 1. Start the database
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

Optional: `CORS_ALLOWED_ORIGINS` lists the browser origins allowed to call the API (comma-separated). It defaults to `http://localhost:4200`, the Angular dev server.

Then open Swagger UI at http://localhost:8080/swagger-ui.html:

1. Call `POST /api/v1/auth/register` (or `/login`) and copy `accessToken` from the response.
2. Click **Authorize**, paste the token (without the word "Bearer").
3. Every other endpoint now works.

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
.\mvnw.cmd test
```

The Docker database must be running. Integration tests run inside transactions that are rolled back, so they leave no data behind. Tests use their own dummy JWT secret and need no environment variable.

## API at a glance

Base path `/api/v1`. Everything except `/auth/**` needs `Authorization: Bearer <token>`.

| Method and path | Purpose |
|---|---|
| `POST /auth/register` | Create an account; returns an access token |
| `POST /auth/login` | Returns a 15-minute access token |
| `GET /me` | The caller's profile |
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
- Backend: Kanban API (CRUD, move, notes), stats, validation and error handling, JWT authentication, CORS, a public demo account, tests.
- Frontend: login/register with "Try the demo", drag-and-drop board with optimistic updates, create/edit dialog with 409 handling, details drawer with notes, searchable server-side list with the state in the URL, stats strip, tests.

Sessions: a 15-minute access token plus a 14-day refresh token in an httpOnly cookie, rotated on every use, so you stay signed in while you keep using the app (see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), section 8).

Not built yet: resume upload and AI match, follow-up reminder emails, deployment.
