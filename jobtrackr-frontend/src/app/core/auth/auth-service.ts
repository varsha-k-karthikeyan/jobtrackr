import { HttpClient, HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, switchMap, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest, User } from '../models/auth.model';
import { SKIP_ERROR_TOAST } from '../interceptors/error-interceptor';
import { clearStoredToken, readStoredToken, storeToken } from './token-storage';

// Single source of truth for "who is signed in". State is held in signals so templates, guards and
// interceptors read it synchronously; HTTP calls stay Observables.
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly authUrl = `${environment.apiBaseUrl}/auth`;

  // Restored from localStorage on startup (expired tokens are discarded by readStoredToken)
  private readonly tokenState = signal<string | null>(readStoredToken());
  private readonly userState = signal<User | null>(null);
  private readonly logoutCallbacks: (() => void)[] = [];

  readonly token = this.tokenState.asReadonly();
  readonly user = this.userState.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenState() !== null);

  /** Logs in, then loads the profile. Errors are left to the caller (the login form shows them inline). */
  login(credentials: LoginRequest): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.authUrl}/login`, credentials, { context: skipToast() })
      .pipe(switchMap((response) => this.startSession(response)));
  }

  /** Registering also signs the user in: the backend answers with a token (201). */
  register(request: RegisterRequest): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.authUrl}/register`, request, { context: skipToast() })
      .pipe(switchMap((response) => this.startSession(response)));
  }

  loadMe(context?: HttpContext): Observable<User> {
    return this.http
      .get<User>(`${environment.apiBaseUrl}/me`, { context })
      .pipe(tap((user) => this.userState.set(user)));
  }

  /** Called once at startup: fills `user` when a stored token survived the reload. */
  restoreSession(): void {
    if (this.tokenState()) {
      // A 401 here (token revoked by a secret change, user deleted) is handled by the auth interceptor
      this.loadMe().subscribe({ error: () => undefined });
    }
  }

  logout(): void {
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

  /** Clears everything held for the current user. Safe to call more than once. */
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

/** Auth pages show their own errors inline, so the global snackbar must stay quiet for these calls. */
function skipToast(): HttpContext {
  return new HttpContext().set(SKIP_ERROR_TOAST, true);
}
