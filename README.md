# JobTrackr

A job application tracker: a Kanban board for your applications (Applied, Interview, Offer, Rejected), notes per application, and (planned) an AI resume-to-job-description match.

This is a monorepo. Today it contains the Spring Boot backend; the Angular frontend will be added next to it.

| Path | What it is |
|---|---|
| `jobtrackr-backend/` | REST API: Spring Boot 4.1, Java 21, Maven |
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

# 3. Start the backend. JWT_SECRET is required: any random string of at least 32 characters
cd "E:\JobTrackr project\jobtrackr-backend"
$env:JWT_SECRET = "<your secret>"
.\mvnw.cmd spring-boot:run
```

To generate a secret:

```powershell
$bytes = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes); [Convert]::ToBase64String($bytes)
```

In IntelliJ, put `JWT_SECRET=<your secret>` in the run configuration's environment variables instead.

Then open Swagger UI at http://localhost:8080/swagger-ui.html:

1. Call `POST /api/v1/auth/register` (or `/login`) and copy `accessToken` from the response.
2. Click **Authorize**, paste the token (without the word "Bearer").
3. Every other endpoint now works.

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
| `GET /applications?status=&searchText=&sort=&page=&size=` | Filtered, sorted, paginated list |
| `POST /applications` | Create a card (appended to the bottom of its column) |
| `GET /applications/{id}` | One card with its notes |
| `PUT /applications/{id}` | Replace the editable fields; needs the current `version` |
| `PATCH /applications/{id}/move` | Move a card to a column and position |
| `DELETE /applications/{id}` | Delete a card |
| `POST /applications/{id}/notes` | Add a note |
| `DELETE /notes/{id}` | Delete a note |

Swagger UI is the full, always-current reference.

## Status

Built: Kanban API (CRUD, move, notes), validation and error handling, JWT authentication, tests.

Not built yet: refresh tokens, resume upload and AI match, follow-up reminder emails, CORS configuration, the Angular frontend.
