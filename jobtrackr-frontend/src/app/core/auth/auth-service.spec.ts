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

  it('stores the token after login and loads the profile', async () => {
    const auth = setUp();
    const token = makeJwt(Date.now() + 15 * 60_000);
    let loggedIn: User | undefined;

    auth.login({ email: 'demo@jobtrackr.dev', password: 'Demo@1234' }).subscribe((result) => (loggedIn = result));
    http.expectOne(`${API}/auth/login`).flush({ accessToken: token, tokenType: 'Bearer', expiresIn: 900 });
    http.expectOne(`${API}/me`).flush(user);

    expect(localStorage.getItem(TOKEN_KEY)).toBe(token);
    expect(auth.token()).toBe(token);
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

    const auth = setUp();

    expect(auth.isAuthenticated()).toBe(false);
  });

  it('logout clears the token, the user and storage, notifies listeners and goes to /login', async () => {
    localStorage.setItem(TOKEN_KEY, makeJwt(Date.now() + 60_000));
    const auth = setUp();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    let notified = false;
    auth.onLogout(() => (notified = true));

    auth.logout();

    expect(auth.token()).toBeNull();
    expect(auth.user()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
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
