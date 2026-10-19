import { HttpClient, HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, finalize, firstValueFrom, map, Observable, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
  User,
} from '../models/auth.model';
import { SKIP_ERROR_TOAST } from '../interceptors/error-interceptor';
import { clearStoredToken, hasSessionHint, readStoredToken, storeToken, TOKEN_KEY } from './token-storage';

// Single source of truth for "who is signed in". State is held in signals so templates, guards and
// interceptors read it synchronously; HTTP calls stay Observables.
//
// Two tokens: a 15-minute access token (Authorization header, kept in localStorage) and a 14-day refresh
// token in an httpOnly cookie that only the backend can read. withCredentials on the /auth calls is what
// lets the browser store and send that cookie across origins (localhost:4200 -> localhost:8080).
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly authUrl = `${environment.apiBaseUrl}/auth`;

  // Restored from localStorage on startup (expired tokens are discarded by readStoredToken)
  private readonly tokenState = signal<string | null>(readStoredToken());
  private readonly userState = signal<User | null>(null);
  private readonly logoutCallbacks: (() => void)[] = [];

  /** The refresh request currently in flight, shared by every caller that needs a new token meanwhile. */
  private refreshInFlight: Observable<string> | null = null;

  readonly token = this.tokenState.asReadonly();
  readonly user = this.userState.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenState() !== null);

  constructor() {
    // Another tab logged in, refreshed or logged out: follow it, so tabs never fight over the session
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', (event) => {
        if (event.key === TOKEN_KEY || event.key === null) {
          this.tokenState.set(readStoredToken());
        }
      });
    }
  }

  /** Logs in, then loads the profile. Errors are left to the caller (the login form shows them inline). */
  login(credentials: LoginRequest): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.authUrl}/login`, credentials, { context: skipToast(), withCredentials: true })
      .pipe(switchMap((response) => this.startSession(response)));
  }

  /** Registering also signs the user in: the backend answers with a token (201) and the refresh cookie. */
  register(request: RegisterRequest): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.authUrl}/register`, request, { context: skipToast(), withCredentials: true })
      .pipe(switchMap((response) => this.startSession(response)));
  }

  /**
   * Changes the password. The backend ends every session and answers with a fresh access token plus a new
   * refresh cookie for this browser; withCredentials is required for the browser to store that cookie.
   * Errors (wrong current password, demo account) are left to the dialog.
   */
  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http
      .put<AuthResponse>(`${environment.apiBaseUrl}/me/password`, request, { context: skipToast(), withCredentials: true })
      .pipe(
        map((response) => {
          storeToken(response.accessToken);
          this.tokenState.set(response.accessToken);
        }),
      );
  }

  /** Asks for a reset link. The backend answers 204 whether or not the address is registered. */
  forgotPassword(request: ForgotPasswordRequest): Observable<void> {
    return this.http.post<void>(`${this.authUrl}/forgot-password`, request, { context: skipToast() });
  }

  /** Sets a new password from an emailed link. No session starts: the user logs in afterwards. */
  resetPassword(request: ResetPasswordRequest): Observable<void> {
    return this.http.post<void>(`${this.authUrl}/reset-password`, request, { context: skipToast() });
  }

  loadMe(context?: HttpContext): Observable<User> {
    return this.http
      .get<User>(`${environment.apiBaseUrl}/me`, { context })
      .pipe(tap((user) => this.userState.set(user)));
  }

  /**
   * Exchanges the refresh cookie for a new access token. Concurrent callers share ONE request: when several
   * API calls fail with 401 at the same moment, the backend sees a single refresh, not one per call.
   */
  refreshAccessToken(): Observable<string> {
    this.refreshInFlight ??= this.http
      .post<AuthResponse>(`${this.authUrl}/refresh`, null, { context: skipToast(), withCredentials: true })
      .pipe(
        map((response) => response.accessToken),
        tap((token) => {
          storeToken(token);
          this.tokenState.set(token);
        }),
        catchError((error: unknown) => {
          // Another tab may have won a simultaneous refresh with the same cookie; it saved its new token
          // to localStorage, which this tab can simply adopt instead of logging out
          const fromOtherTab = readStoredToken();
          if (fromOtherTab && fromOtherTab !== this.tokenState()) {
            this.tokenState.set(fromOtherTab);
            return of(fromOtherTab);
          }
          return throwError(() => error);
        }),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    return this.refreshInFlight;
  }

  /**
   * Used by the route guards when there is no valid access token (e.g. a reload after it expired):
   * silently renews the session from the refresh cookie. Resolves to whether the user is signed in.
   */
  async tryRestoreSession(): Promise<boolean> {
    if (this.isAuthenticated()) {
      return true;
    }
    if (!hasSessionHint()) {
      return false;
    }
    try {
      await firstValueFrom(this.refreshAccessToken());
      this.loadMe().subscribe({ error: () => undefined });
      return true;
    } catch {
      clearStoredToken(); // the cookie is gone or revoked: stop trying until the next login
      return false;
    }
  }

  /** Called once at startup: fills `user` when a stored token survived the reload. */
  restoreSession(): void {
    if (this.tokenState()) {
      // A 401 here (expired or revoked) is handled by the auth interceptor: refresh, retry, or log out
      this.loadMe().subscribe({ error: () => undefined });
    }
  }

  /** Ends the session on the server too: revokes the refresh token and deletes its cookie. */
  logout(): void {
    this.http
      .post<void>(`${this.authUrl}/logout`, null, { context: skipToast(), withCredentials: true })
      .subscribe({ error: () => undefined }); // logging out locally must never wait for or fail on the network
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  /**
   * Lets other root services (e.g. the application store) wipe their state on logout without
   * AuthService depending on them, which would create an import cycle.
   */
  onLogout(callback: () => void): void {
    this.logoutCallbacks.push(callback);
  }

  /** Clears everything held for the current user in this browser. Safe to call more than once. */
  clearSession(): void {
    clearStoredToken();
    this.tokenState.set(null);
    this.userState.set(null);
    this.logoutCallbacks.forEach((callback) => callback());
  }

  private startSession(response: AuthResponse): Observable<User> {
    storeToken(response.accessToken);
    this.tokenState.set(response.accessToken);
    // Still part of the login/register flow, so its errors also belong to the form, not the snackbar
    return this.loadMe(skipToast());
  }
}

/** Auth calls show their own errors (or none), so the global snackbar must stay quiet for them. */
function skipToast(): HttpContext {
  return new HttpContext().set(SKIP_ERROR_TOAST, true);
}
