import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { User } from '../models/auth.model';
import { makeJwt } from '../testing/test-data';
import { AuthService } from './auth-service';

const API = environment.apiBaseUrl;
const TOKEN_KEY = 'jobtrackr.accessToken';
const HINT_KEY = 'jobtrackr.hasSession';
const user: User = { id: 1, email: 'demo@jobtrackr.dev', fullName: 'Demo User', createdAt: '2026-10-13T16:20:35Z' };

describe('AuthService', () => {
  let http: HttpTestingController;

  function setUp(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => http.verify());

  it('stores the access token after login, sends credentials for the refresh cookie, and loads the profile', async () => {
    const auth = setUp();
    const token = makeJwt(Date.now() + 15 * 60_000);
    let loggedIn: User | undefined;

    auth.login({ email: 'demo@jobtrackr.dev', password: 'Demo@1234' }).subscribe((result) => (loggedIn = result));
    const login = http.expectOne(`${API}/auth/login`);
    expect(login.request.withCredentials).toBe(true);
    login.flush({ accessToken: token, tokenType: 'Bearer', expiresIn: 900 });
    http.expectOne(`${API}/me`).flush(user);

    expect(localStorage.getItem(TOKEN_KEY)).toBe(token);
    expect(localStorage.getItem(HINT_KEY)).toBe('1');
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.user()).toEqual(user);
    expect(loggedIn).toEqual(user);
  });

  it('restores a stored, unexpired token on startup', () => {
    const token = makeJwt(Date.now() + 60_000);
    localStorage.setItem(TOKEN_KEY, token);

    const auth = setUp();

    expect(auth.token()).toBe(token);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('discards an expired stored token on startup', () => {
    localStorage.setItem(TOKEN_KEY, makeJwt(Date.now() - 1000));

    expect(setUp().isAuthenticated()).toBe(false);
  });

  it('shares one refresh request between concurrent callers', async () => {
    const auth = setUp();
    const fresh = makeJwt(Date.now() + 15 * 60_000);
    const results: string[] = [];

    auth.refreshAccessToken().subscribe((token) => results.push(token));
    auth.refreshAccessToken().subscribe((token) => results.push(token));
    auth.refreshAccessToken().subscribe((token) => results.push(token));

    const refresh = http.expectOne(`${API}/auth/refresh`); // exactly one, despite three callers
    expect(refresh.request.withCredentials).toBe(true);
    refresh.flush({ accessToken: fresh, tokenType: 'Bearer', expiresIn: 900 });

    expect(results).toEqual([fresh, fresh, fresh]);
    expect(auth.token()).toBe(fresh);
    expect(localStorage.getItem(TOKEN_KEY)).toBe(fresh);
  });

  it('silently restores the session from the refresh cookie when the access token expired', async () => {
    localStorage.setItem(TOKEN_KEY, makeJwt(Date.now() - 1000));
    localStorage.setItem(HINT_KEY, '1');
    const auth = setUp();
    const fresh = makeJwt(Date.now() + 15 * 60_000);

    const restoring = auth.tryRestoreSession();
    http.expectOne(`${API}/auth/refresh`).flush({ accessToken: fresh, tokenType: 'Bearer', expiresIn: 900 });

    expect(await restoring).toBe(true);
    http.expectOne(`${API}/me`).flush(user);
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.user()).toEqual(user);
  });

  it('does not try a refresh when there was never a session (no hint)', async () => {
    const auth = setUp();

    expect(await auth.tryRestoreSession()).toBe(false);
    http.expectNone(`${API}/auth/refresh`);
  });

  it('forgets the session hint when the refresh cookie is rejected', async () => {
    localStorage.setItem(HINT_KEY, '1');
    const auth = setUp();

    const restoring = auth.tryRestoreSession();
    http.expectOne(`${API}/auth/refresh`).flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(await restoring).toBe(false);
    expect(localStorage.getItem(HINT_KEY)).toBeNull();
  });

  it('logout revokes the refresh token on the server, clears local state, notifies listeners and goes to /login', () => {
    localStorage.setItem(TOKEN_KEY, makeJwt(Date.now() + 60_000));
    const auth = setUp();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    let notified = false;
    auth.onLogout(() => (notified = true));

    auth.logout();

    const logout = http.expectOne(`${API}/auth/logout`);
    expect(logout.request.method).toBe('POST');
    expect(logout.request.withCredentials).toBe(true);
    logout.flush(null, { status: 204, statusText: 'No Content' });
    expect(auth.token()).toBeNull();
    expect(auth.user()).toBeNull();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(HINT_KEY)).toBeNull();
    expect(notified).toBe(true);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('keeps the user signed out when login fails', () => {
    const auth = setUp();
    let failed = false;

    auth.login({ email: 'demo@jobtrackr.dev', password: 'wrong' }).subscribe({ error: () => (failed = true) });
    http.expectOne(`${API}/auth/login`).flush({ status: 401, detail: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });
});
