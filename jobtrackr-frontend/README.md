# JobTrackr frontend

Angular 22 single-page app for JobTrackr: a Kanban board with drag and drop, a searchable list, a details drawer with notes, and stats. Standalone components, Signals, Angular Material and Tailwind CSS.

Project-wide documentation (setup, backend architecture, debugging, commit rules) lives at the monorepo root: see [../README.md](../README.md) and [../docs](../docs).

## Run

Needs Node 22.22.3+ or 24.15+ and the backend running on http://localhost:8080 (see the root README).

```powershell
cd "E:\JobTrackr project\jobtrackr-frontend"
npm install      # first time only
npm start        # ng serve on http://localhost:4200
```

| Command | Purpose |
|---|---|
| `npm start` | Dev server with live reload |
| `npm test` | Unit tests (Vitest), once with `npm test -- --watch=false` |
| `npm run build` | Production build into `dist/` |

If PowerShell refuses `npm` ("running scripts is disabled"), use `npm.cmd`, or run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once.

Log in with **Try the demo** (demo@jobtrackr.dev / Demo@1234) to see sample data.

## Structure

```
src/app/
├── core/                      app-wide singletons, no UI of their own (except the shell)
│   ├── api/                   ApplicationApiService (one typed method per endpoint), ProblemDetail -> message
│   ├── auth/                  AuthService (signals), token storage, demo credentials
│   ├── guards/                authGuard, guestGuard, safeReturnUrl
│   ├── interceptors/          authInterceptor (Bearer + 401), errorInterceptor (snackbar)
│   ├── layout/shell/          toolbar, user menu, Board/List tabs, router outlet
│   ├── models/                TypeScript mirrors of the backend's DTO records
│   ├── notifications/         Notifier: lazily loaded snackbar
│   ├── state/                 ApplicationStore + pure board transforms (board-state.ts)
│   └── testing/               builders shared by unit tests
├── features/                  one folder per page area, lazy-loaded by the router
│   ├── auth/                  login (with "Try the demo") and register pages
│   ├── board/                 board page (CDK drag and drop) and the application card
│   ├── list/                  server-side paginated table
│   └── stats/                 stats strip shown above the board
└── shared/                    reusable across features
    ├── components/            create/edit dialog, details drawer, confirm dialog, ApplicationDialogs
    ├── utils/                 dates (local calendar logic), salary formatting, dd/mm/yyyy date adapter
    └── validators/            password strength (mirrors the backend rule)
```

## How data flows

```
component ──calls──▶ ApplicationStore ──▶ ApplicationApiService ──▶ HttpClient ──▶ interceptors ──▶ API
    ▲                    │ signals: board, counts, total, detail, loading, error
    └────reads───────────┘
```

- **The store is the single source of truth for the board.** Components read its signals and call its methods. Every change is immutable, using the pure functions in `core/state/board-state.ts`, which mirror the backend's position rules.
- **Mutations return Promises** (`await store.move(...)`): they run immediately and cannot be forgotten like an unsubscribed Observable. HTTP itself stays RxJS.
- **Optimistic drag and drop:** `store.move()` updates the board at once, sends `PATCH /move`, takes the server's copy (new `version`) on success, rolls back on failure, and reloads the board on 404/409.
- **The stats strip is derived** from the store's counts: no request, and it updates instantly after create, delete and move. Same formula as the backend's `GET /stats`.
- **The list page** keeps search, status, sort and page in the URL, so refresh, back/forward and shared links restore the view. It fetches pages directly through `ApplicationApiService.list()`: a page of search results is a view of the server, not shared state.
- **On logout** the store resets (registered through `AuthService.onLogout`), and responses that arrive after a logout are dropped, so one user's data never shows for the next.

## Styling

- `src/styles.scss`: the Angular Material 3 theme (azure/violet, Inter) and a few app-wide classes (`.app-error-banner`, `.status-badge`, ...).
- `src/tailwind.css`: Tailwind CSS v4, plain CSS on purpose (Tailwind v4 is not meant to run through Sass).

**Form fields** are stacked with `gap-4` (16px) on their wrapper element. An outlined field's floating label sits on its top border, a few pixels above the field's box, so with less space a validation error or hint under one field runs into the label of the next. Keep hints short enough for one line.

Tailwind sits in cascade layers and Material does not, so Material always wins on its own components. Use Tailwind for layout around Material widgets (put utilities on a plain wrapper element), and colour custom pieces with Material's `--mat-sys-*` variables or `mat.*-overrides` mixins.

## Authentication

- `core/auth/auth-service.ts` holds the session in signals (`token`, `user`, `isAuthenticated`). The token is kept in `localStorage` so a reload keeps you signed in; an expired token is discarded on startup.
- `core/interceptors/auth-interceptor.ts` adds `Authorization: Bearer ...` to calls to our API only. A 401 from a protected endpoint clears the session, shows one "session expired" message and goes to `/login?returnUrl=...`.
- `core/interceptors/error-interceptor.ts` shows any other failed request as a snackbar, using the server's ProblemDetail text. Requests whose caller shows the error itself (forms, the edit dialog's 409 handling, drag and drop) set `SKIP_ERROR_TOAST`, so nothing is shown twice.
- `core/guards/auth-guards.ts`: `authGuard` protects `/board` and `/list`; `guestGuard` keeps signed-in users away from `/login` and `/register`.

### Talking point: why localStorage and not an httpOnly cookie?

A token in `localStorage` can be read by any JavaScript on the page, so a cross-site scripting (XSS) bug could steal it. An httpOnly cookie cannot be read by JavaScript at all, which removes that risk, but the browser then sends it automatically, so the API needs CSRF protection and cross-origin cookie settings (`SameSite`, `Secure`, credentialed CORS).

For this app, `localStorage` is a reasonable trade-off: the token lives only 15 minutes, Angular escapes template output by default, and the app loads no third-party scripts. In a production system holding sensitive data, the stronger setup is a short-lived access token kept in memory plus a refresh token in an httpOnly, `SameSite=Strict` cookie.

## Configuration

`src/environments/environment.development.ts` (used by `ng serve`) and `environment.ts` (production build) hold `apiBaseUrl`. Everything in them ships to the browser, so no secrets.

## Tests

`npm test` runs Vitest through Angular's unit-test builder. The important suites:

| Spec | Covers |
|---|---|
| `core/state/application-store.spec.ts` | Load, optimistic move, rollback on failure, reload on 409, cleared on logout, stale responses dropped |
| `core/state/board-state.spec.ts` | Pure move/insert/remove maths, clamping, immutability |
| `core/auth/auth-service.spec.ts` | Token persistence, restore and expiry on startup, logout |
| `shared/components/application-dialog/application-dialog.spec.ts` | Form validation (required, cross-field salary, limits), edit prefill, the exact request body |

HTTP is tested with `HttpTestingController`; no real request leaves a test.
