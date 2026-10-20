# JobTrackr frontend

Angular 22 single-page app for JobTrackr: a Kanban board with drag and drop, a searchable list, a details drawer with notes, stats, and a notification bell (interviews and follow-ups) in the header. Standalone components, Signals, Angular Material and Tailwind CSS.

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
| `npm test` | Unit tests (Vitest) in watch mode |
| `npm run test:ci` | Unit tests once, then exit (what CI runs). Same as `npm test -- --watch=false`; without a terminal (TTY) the builder already defaults to a single run |
| `npm run build` | Production build into `dist/` (the default configuration is `production`; it fails above 1 MB and warns above 500 kB for the initial bundle) |

If PowerShell refuses `npm` ("running scripts is disabled"), use `npm.cmd`, or run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once.

Log in with **Try the demo** (demo@jobtrackr.dev / Demo@1234) to see sample data.

## Structure

```
src/app/
├── core/                      app-wide singletons, no UI of their own (except the shell)
│   ├── api/                   ApplicationApiService, NotificationApiService (one typed method per endpoint), ProblemDetail -> message
│   ├── auth/                  AuthService (signals), token storage, demo credentials
│   ├── guards/                authGuard, guestGuard, safeReturnUrl
│   ├── interceptors/          authInterceptor (Bearer + 401), errorInterceptor (snackbar)
│   ├── layout/shell/          toolbar, notification bell, user menu, Board/List tabs, router outlet
│   ├── models/                TypeScript mirrors of the backend's DTO records
│   ├── notifications/         Notifier (lazily loaded snackbar), NotificationStore (the bell: interviews and follow-ups)
│   ├── state/                 ApplicationStore + pure board transforms (board-state.ts)
│   └── testing/               builders shared by unit tests
├── features/                  one folder per page area, lazy-loaded by the router
│   ├── auth/                  login (with "Try the demo"), register, forgot-password and reset-password pages
│   ├── board/                 board page (CDK drag and drop) and the application card
│   ├── list/                  server-side paginated table
│   └── stats/                 stats strip shown above the board
└── shared/                    reusable across features
    ├── components/            create/edit dialog, details drawer, confirm dialog, ApplicationDialogs,
    │                          change-password dialog (lazy), PasswordRules checklist
    ├── utils/                 dates (local calendar logic), salary formatting, dd/mm/yyyy date adapter
    └── validators/            password strength (mirrors the backend rule), matchesControl (confirm field)
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
- **Notification bell:** `NotificationStore` holds the server's `GET /notifications` result (`interviews`, `due`, `upcoming`; `badgeCount` = due follow-ups + interviews whose `daysUntil` is 0). The menu lists Interviews (time in the browser's zone via `interviewLabel`: "Today · 7:45 PM"; no dismiss button, the server drops an interview once it starts), then Follow-ups due (with Dismiss all), then Upcoming follow-ups. The shell reloads it on sign-in, whenever the menu opens, and after every board change: `ApplicationStore.revision` is bumped after each successful create, update, delete or move, and an `effect` in the shell watches it. That keeps the board store free of any knowledge of notifications. The server decides what is due (in its configured zone), so the client does no date arithmetic. Clicking an item opens the details drawer; dismiss calls `DELETE /notifications/{id}`.
- **On logout** the store resets (registered through `AuthService.onLogout`), and responses that arrive after a logout are dropped, so one user's data never shows for the next.

## Styling

- `src/tailwind.css`: Tailwind CSS v4 (plain CSS on purpose: Tailwind v4 is not meant to run through Sass) and the **design tokens** in `@theme static`: every colour, radius and shadow of the design (`--color-band`, `--color-applied-soft`, `--radius-field`, `--shadow-menu`, ...). Each token is a CSS variable and a Tailwind utility (`bg-band`, `rounded-card`). Components use these, never raw hex values. The approved screen designs are in `docs/design/`.
- `src/styles.scss`: the Angular Material 3 theme. Palettes generated from the brand colours (`src/styles/_theme-colors.scss`, by `ng generate @angular/material:theme-color`), the exact brand roles pinned with `mat.theme-overrides`, Figtree as the plain and brand font, and the component shapes (`mat.form-field-overrides`, `button-`, `dialog-`, `menu-`, ...) set once for the app. Plus a few app-wide classes (`.app-error-banner`, `.status-badge`, ...).
- Fonts come from Google Fonts in `index.html`, not the bundle: Figtree and **Material Symbols Outlined**, which `MatIconRegistry` makes the default for `<mat-icon>` (`app.config.ts`).

**Password fields** use our own show/hide toggle. Microsoft Edge adds a second, built-in eye to every password input; `styles.scss` hides it with `input::-ms-reveal { display: none; }`, so keep that rule.

**Form fields** are stacked with `gap-4` (16px) on their wrapper element. An outlined field's floating label sits on its top border, a few pixels above the field's box, so with less space a validation error or hint under one field runs into the label of the next. Keep hints short enough for one line.

Tailwind sits in cascade layers and Material does not, so Material always wins on its own components. Use Tailwind for layout around Material widgets (put utilities on a plain wrapper element), and colour custom pieces with Material's `--mat-sys-*` variables or `mat.*-overrides` mixins.

## Authentication

Two tokens:

| | Access token | Refresh token |
|---|---|---|
| What | Signed JWT | Random opaque value (the backend stores only its SHA-256) |
| Lifetime | 15 minutes | 14 days, rotated on every use |
| Where | `localStorage`, sent as `Authorization: Bearer` | httpOnly cookie `jobtrackr_refresh`, sent by the browser only to `/api/v1/auth/*` |
| Readable by JavaScript | Yes | No |

- `core/auth/auth-service.ts` holds the session in signals (`token`, `user`, `isAuthenticated`). `refreshAccessToken()` exchanges the cookie for a new access token; concurrent callers share **one** request. All `/auth/*` calls use `withCredentials: true`, which is what lets the browser store and send the cookie across origins (`:4200` to `:8080`).
- `core/interceptors/auth-interceptor.ts` adds the Bearer token to calls to our API only. On a 401 from a protected endpoint it refreshes once and retries the original request once with the new token. If the token was already replaced by another request meanwhile, it just retries. Only when the refresh fails does it clear the session, show one "session expired" message and go to `/login?returnUrl=...`.
- `core/guards/auth-guards.ts`: `authGuard` protects `/board` and `/list`; `guestGuard` keeps signed-in users away from `/login` and `/register`. Both are async: with no valid access token (a reload after it expired) they first try to restore the session from the cookie.
- `core/auth/token-storage.ts` also keeps a "session hint" flag, so a silent refresh is only attempted when a cookie probably exists (JavaScript cannot check the cookie itself). Logout clears it.
- **Forgot password:** the login page links to `/forgot-password` (guest only), which calls `POST /auth/forgot-password` and always shows the same neutral "if an account exists..." message, as the backend never reveals whether an address is registered. In development it also links to the Mailpit inbox (`environment.mailInboxUrl`). The emailed link opens `/reset-password?token=...`, which has **no guard** (it must work in a browser that is still signed in), reads the token once and immediately removes it from the address bar with `replaceUrl`, so it is not kept in history. The token travels in the request body. On success the local session is cleared (the backend ended every session) and the page goes to `/login?reset=done`, which shows a confirmation. A used or expired link shows "Request a new link".
- **Reminder emails switch** (account menu, disabled for the demo account): a `mat-menu-item` with `role="menuitemcheckbox"` and `aria-checked`, showing "Reminder emails: On/Off" from `AuthService.user().emailRemindersEnabled`. `AuthService.setEmailReminders` calls `PUT /me/email-reminders` and stores the returned profile, so the menu updates without reloading `/me`; a snackbar confirms (off: "Reminder emails are turned off"; nothing is sent from the next minute). A failure gets the usual error snackbar and the item keeps its old state.
- **Change password** (account menu, disabled for the demo account): `AuthService.changePassword` calls `PUT /me/password` with `withCredentials: true`, because the answer sets a new refresh cookie (the backend ended every session, this one included) and the browser only stores a cross-origin cookie from a credentialed request. The fresh access token replaces the stored one, so this tab stays signed in. A wrong current password comes back as a 400 field error and is shown under the field. The dialog is loaded on first use.
- **Logout** calls `POST /auth/logout` (revokes the refresh token, deletes the cookie) and clears local state without waiting for the network.
- **Tabs** stay in step through the `storage` event; if two tabs refresh at the same moment, the loser adopts the token the winner saved.
- `core/interceptors/error-interceptor.ts` shows any other failed request as a snackbar, using the server's ProblemDetail text. Requests whose caller shows the error itself (forms, the edit dialog's 409 handling, drag and drop, auth calls) set `SKIP_ERROR_TOAST`, so nothing is shown twice.

### Talking point: where do the tokens live, and why?

A token in `localStorage` can be read by any JavaScript on the page, so a cross-site scripting (XSS) bug could steal it. An httpOnly cookie cannot be read by JavaScript at all.

So the **long-lived refresh token is in an httpOnly cookie**. The worst an XSS bug could take is the access token, which expires within 15 minutes, never the 14-day session. The cookie is `SameSite=Strict` (not sent on requests started by other sites, which is the CSRF protection) and scoped to `/api/v1/auth`.

The **access token stays in `localStorage`** so a reload does not need a refresh round trip. Keeping it only in memory would be stricter still; the trade-off is one extra `/auth/refresh` call on every page load.

## Configuration

`src/environments/environment.development.ts` (used by `ng serve`) and `environment.ts` (production build) hold `apiBaseUrl` and `mailInboxUrl` (the local Mailpit inbox in development, `null` in production).

- Development: `apiBaseUrl` is `http://localhost:8080/api/v1` (another origin, so CORS and `withCredentials` apply).
- Production: `apiBaseUrl` is the **relative** `/api/v1`. Whatever serves the app forwards `/api/` to the backend: nginx in the Docker image (`nginx.conf`), or a hosting platform's rewrite rules. The browser then sees one origin, so the `SameSite=Strict` refresh cookie is first-party.

## Docker image

`Dockerfile`: Node 24 runs `npm ci` and the production build; nginx 1.30 serves `dist/jobtrackr-frontend/browser`. `nginx.conf` (a template: `BACKEND_URL` is filled in at container start) does the SPA fallback to `index.html`, gzip, a one-year `immutable` cache for the content-hashed JS and CSS and `no-cache` for `index.html`, and proxies `/api/`. It listens on IPv4 and IPv6, because the health check's `localhost` resolves to `::1` inside the container. Everything in them ships to the browser, so no secrets.

## Tests

`npm test` runs Vitest through Angular's unit-test builder. The important suites:

| Spec | Covers |
|---|---|
| `core/state/application-store.spec.ts` | Load, optimistic move, rollback on failure, reload on 409, cleared on logout, stale responses dropped, `revision` bumped only by successful changes |
| `core/notifications/notification-store.spec.ts` | Due vs upcoming and the badge count, a failed refresh keeps the last list, dismiss one / all, responses after logout dropped, `followUpLabel` wording |
| `core/state/board-state.spec.ts` | Pure move/insert/remove maths, clamping, immutability |
| `core/auth/auth-service.spec.ts` | Token persistence, restore and expiry on startup, one shared refresh, silent restore from the cookie, logout |
| `core/interceptors/auth-interceptor.spec.ts` | Bearer only to our API, refresh + single retry on 401, one refresh for simultaneous 401s, no refresh loop, logout when the refresh fails |
| `features/auth/reset-password-page/reset-password-page.spec.ts` | Token removed from the URL without a history entry, missing token, the exact request body, redirect with confirmation, used/expired link state |
| `features/auth/forgot-password-page/forgot-password-page.spec.ts` | Email validation, the request body, the neutral confirmation |
| `core/interceptors/error-interceptor.spec.ts` | One snackbar per failed request with the ProblemDetail text, `SKIP_ERROR_TOAST` honoured, 401 left to the auth interceptor, the error still reaches the caller, network failures explained |
| `core/guards/auth-guards.spec.ts` | `safeReturnUrl` refuses external and protocol-relative URLs (no open redirect after login); `authGuard` and `guestGuard` decisions |
| `core/api/problem-message.spec.ts`, `core/auth/token-storage.spec.ts` | Error text for every kind of failure; expired or unreadable tokens dropped, session hint set and cleared |
| `shared/components/change-password-dialog/change-password-dialog.spec.ts` | Required and match rules, the exact request (credentials included), token stored on success, server field error and the demo 403 |
| `shared/components/application-dialog/application-dialog.spec.ts` | Form validation (required, cross-field salary, limits), edit prefill, the exact request body |

HTTP is tested with `HttpTestingController`; no real request leaves a test.
