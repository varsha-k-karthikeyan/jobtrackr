# Contributing

How to make changes that fit the codebase, and how to write commit messages that pass the lint.

## One-time setup

```powershell
cd "E:\JobTrackr project"
npm install
```

This installs commitlint and husky and registers the git hook in `.husky/commit-msg`. From then on every `git commit` checks the message and refuses a badly formatted one. Run it again after a fresh clone.

## Commit messages

The format is [Conventional Commits](https://www.conventionalcommits.org), the convention the Angular project uses:

```
<type>(<scope>): <subject>

<optional body: what changed and why, wrapped at 100 characters>

<optional footer: BREAKING CHANGE: ..., or Refs: #12>
```

### Rules

| Part | Rule |
|---|---|
| `type` | Required. One of the types below |
| `scope` | Optional. If present, one of the scopes below |
| `subject` | Required. Starts with a lower-case letter, imperative mood ("add", not "added"), no full stop at the end |
| Header (first line) | At most 100 characters |
| Body and footer | Separated from the header by a blank line; lines at most 100 characters |

### Types

| Type | Use it for |
|---|---|
| `feat` | New behaviour an API client or user can see |
| `fix` | A bug fix |
| `docs` | Documentation or comments only |
| `refactor` | A code change that neither fixes a bug nor adds behaviour |
| `test` | Adding or correcting tests |
| `perf` | A performance improvement |
| `build` | Maven, npm, dependency changes |
| `ci` | Pipeline configuration |
| `chore` | Repository housekeeping |
| `style` | Formatting only |
| `revert` | Reverting an earlier commit |

### Scopes

| Scope | Covers |
|---|---|
| `application`, `note`, `user`, `auth`, `stats`, `notification`, `common` | The backend feature package of the same name |
| `db` | Flyway migrations |
| `config` | `application.yml`, `pom.xml` settings |
| `docker` | `docker-compose.yml` |
| `frontend` | The Angular app |
| `deps` | Dependency upgrades |
| `docs` | Documentation |
| `repo` | Root tooling: git hooks, `.gitignore`, commitlint |

To add a type or scope, edit `commitlint.config.js` and this table together.

### Examples

Accepted:

```
feat(notification): add follow-up notifications for the header bell
fix(auth): return 401 instead of 500 for a malformed token
refactor(application): rename q to searchText
docs: add debugging guide
build(deps): upgrade springdoc to 3.1.2
refactor(db): drop the unused resumes and match_results tables
```

Rejected, with the reason:

```
Initial commit                      no type
added login                         no type; not imperative
feature(auth): add login            "feature" is not a type; use "feat"
feat(payments): add stripe          "payments" is not a scope
fix(auth): Fix login.               subject starts with a capital and ends with a full stop
feat(auth):add login                no space after the colon
```

A breaking change to the API gets an exclamation mark and a footer:

```
feat(application)!: rename searchText query parameter to search

BREAKING CHANGE: clients must send ?search= instead of ?searchText=
```

To check a message without committing (the `cmd /c` wrapper matters: Windows PowerShell's own pipe adds an invisible character that commitlint rejects):

```powershell
cmd /c "echo feat(auth): add refresh tokens| npx commitlint"
```

No output means the message is valid.

## Code rules

The short version; [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) explains the reasons.

- Package by feature. New code goes in the feature it belongs to, or in a new feature package.
- Request and response bodies are Java records in the feature's `dto` package. Entities never appear in a controller signature.
- Constructor injection only (`@RequiredArgsConstructor` with `final` fields). No field `@Autowired`.
- The schema changes only through Flyway. `ddl-auto` stays `validate`.
- Every query is scoped to the current user, whose id comes only from `CurrentUserProvider`. The exceptions have no signed-in user by nature: a scheduled job's housekeeping query (the nightly token cleanup) and the password-reset flow, which identifies the user by email address or hashed reset token.
- Names are meaningful and unabbreviated: `searchText`, not `q`.
- Every service method has a unit test.
- Comments explain why, not what.

## Recipes

### Add a column

1. Create `src/main/resources/db/migration/V<next>__<description>.sql` with the `ALTER TABLE`. Never edit a migration that has already run.
2. Add the field to the entity with a matching `@Column`.
3. Add it to the request and response records, with validation that mirrors the column limits.
4. Add it to the mapper (`toResponse`, `toEntity`, `applyUpdate`).
5. Extend the tests, then run `.\mvnw.cmd verify` (Docker Desktop running; no compose database needed).

If the app then fails to start with `Schema-validation`, the entity and the migration disagree.

### Add an endpoint to an existing feature

1. Write the service method first, with its unit test. Get the user id from `CurrentUserProvider` and filter by it.
2. Add the repository method if a new query is needed.
3. Add the controller method: `@Valid` on the body, the right status code, records in and out.
4. If a new kind of error can occur, add an exception in `common/exception` and a handler in `GlobalExceptionHandler`.
5. Check it in Swagger UI.

### Add a feature package

Create `com.jobtrackr.jobtrackrbackend.<feature>` with entity, repository, service, controller, mapper and `dto/`, following `note/` as the smallest complete example. Add the scope to `commitlint.config.js`.

## Before you commit

```powershell
cd "E:\JobTrackr project\jobtrackr-backend"
.\mvnw.cmd verify   # needs Docker Desktop (Testcontainers), not the compose database

# If the frontend changed
cd "E:\JobTrackr project\jobtrackr-frontend"
npm run test:ci
npm run build      # also checks the 500 kB initial bundle budget
```

All tests must pass. Check `git status` and make sure no `.env` or `.env.properties` file, secret or `target/` output is staged (`.env.example`, with placeholders only, is the one env file that is committed). When a backend DTO changes, update its TypeScript mirror in `jobtrackr-frontend/src/app/core/models` in the same commit.
