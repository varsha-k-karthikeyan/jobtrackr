import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { makeJwt } from '../testing/test-data';
import { authInterceptor } from './auth-interceptor';

const API = environment.apiBaseUrl;
const BOARD = `${API}/applications/board`;
const REFRESH = `${API}/auth/refresh`;

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;
  const expired = makeJwt(Date.now() + 60_000); // still "valid" for the client; the server will say 401
  const fresh = makeJwt(Date.now() + 15 * 60_000);

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('jobtrackr.accessToken', expired);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => backend.verify());

  const unauthorized = { status: 401, statusText: 'Unauthorized' };

  it('adds the Bearer token to API calls', () => {
    http.get(BOARD).subscribe();
    expect(backend.expectOne(BOARD).request.headers.get('Authorization')).toBe(`Bearer ${expired}`);
  });

  it('never sends the token to another host', () => {
    http.get('https://example.com/data').subscribe();
    expect(backend.expectOne('https://example.com/data').request.headers.has('Authorization')).toBe(false);
  });

  it('on 401: refreshes once, then retries the original request with the new token', () => {
    let body: unknown;
    http.get(BOARD).subscribe((result) => (body = result));

    backend.expectOne(BOARD).flush(null, unauthorized);
    backend.expectOne(REFRESH).flush({ accessToken: fresh, tokenType: 'Bearer', expiresIn: 900 });
    const retry = backend.expectOne(BOARD);
    expect(retry.request.headers.get('Authorization')).toBe(`Bearer ${fresh}`);
    retry.flush({ APPLIED: [] });

    expect(body).toEqual({ APPLIED: [] });
    expect(navigate).not.toHaveBeenCalled();
  });

  it('several requests failing together trigger only ONE refresh, and all are retried', () => {
    const done: string[] = [];
    http.get(BOARD).subscribe(() => done.push('board'));
    http.get(`${API}/stats`).subscribe(() => done.push('stats'));

    backend.expectOne(BOARD).flush(null, unauthorized);
    backend.expectOne(`${API}/stats`).flush(null, unauthorized);
    backend.expectOne(REFRESH).flush({ accessToken: fresh, tokenType: 'Bearer', expiresIn: 900 });
    backend.expectOne(BOARD).flush({});
    backend.expectOne(`${API}/stats`).flush({});

    expect(done.sort()).toEqual(['board', 'stats']);
  });

  it('a 401 arriving AFTER another request already refreshed is retried without a second refresh', () => {
    const done: string[] = [];
    http.get(BOARD).subscribe(() => done.push('board'));
    http.get(`${API}/stats`).subscribe(() => done.push('stats'));
    const board = backend.expectOne(BOARD);
    const stats = backend.expectOne(`${API}/stats`);

    // board fails first and its refresh completes before the stats response comes back
    board.flush(null, unauthorized);
    backend.expectOne(REFRESH).flush({ accessToken: fresh, tokenType: 'Bearer', expiresIn: 900 });
    backend.expectOne(BOARD).flush({});
    stats.flush(null, unauthorized);

    backend.expectNone(REFRESH);
    const statsRetry = backend.expectOne(`${API}/stats`);
    expect(statsRetry.request.headers.get('Authorization')).toBe(`Bearer ${fresh}`);
    statsRetry.flush({});
    expect(done.sort()).toEqual(['board', 'stats']);
  });

  it('when the refresh fails: ends the session and goes to /login', () => {
    let failed = false;
    http.get(BOARD).subscribe({ error: () => (failed = true) });

    backend.expectOne(BOARD).flush(null, unauthorized);
    backend.expectOne(REFRESH).flush({ status: 401 }, unauthorized);

    expect(failed).toBe(true);
    expect(localStorage.getItem('jobtrackr.accessToken')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });

  it('when the retry is refused too: ends the session instead of refreshing again', () => {
    http.get(BOARD).subscribe({ error: () => undefined });

    backend.expectOne(BOARD).flush(null, unauthorized);
    backend.expectOne(REFRESH).flush({ accessToken: fresh, tokenType: 'Bearer', expiresIn: 900 });
    backend.expectOne(BOARD).flush(null, unauthorized);

    backend.expectNone(REFRESH); // no second refresh, no loop
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });

  it('does not refresh on a 401 from /auth/login (that means a wrong password)', () => {
    http.post(`${API}/auth/login`, {}).subscribe({ error: () => undefined });

    backend.expectOne(`${API}/auth/login`).flush(null, unauthorized);

    backend.expectNone(REFRESH);
    expect(navigate).not.toHaveBeenCalled();
  });
});
