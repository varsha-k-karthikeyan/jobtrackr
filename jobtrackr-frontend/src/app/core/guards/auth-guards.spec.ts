import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from '../auth/auth-service';
import { authGuard, guestGuard, safeReturnUrl } from './auth-guards';

describe('safeReturnUrl (no open redirect after login)', () => {
  it('follows in-app paths', () => {
    expect(safeReturnUrl('/list?status=OFFER')).toBe('/list?status=OFFER');
  });

  it('refuses anything that would leave the app', () => {
    expect(safeReturnUrl('https://evil.example')).toBe('/board');
    expect(safeReturnUrl('//evil.example')).toBe('/board'); // protocol-relative: still another host
    expect(safeReturnUrl('javascript:alert(1)')).toBe('/board');
  });

  it('defaults to the board', () => {
    expect(safeReturnUrl(null)).toBe('/board');
    expect(safeReturnUrl('')).toBe('/board');
  });
});

describe('route guards', () => {
  let signedIn: boolean;

  beforeEach(() => {
    signedIn = false;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { tryRestoreSession: () => Promise.resolve(signedIn) } },
      ],
    });
  });

  function run(guard: typeof authGuard, url = '/list'): Promise<boolean | UrlTree> {
    return TestBed.runInInjectionContext(
      () => guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot) as Promise<boolean | UrlTree>,
    );
  }

  const serialize = (result: boolean | UrlTree) =>
    result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : result;

  it('authGuard lets a signed-in (or silently restored) user through', async () => {
    signedIn = true;
    expect(await run(authGuard)).toBe(true);
  });

  it('authGuard sends everyone else to /login, remembering where they were going', async () => {
    expect(serialize(await run(authGuard, '/list?status=OFFER'))).toBe('/login?returnUrl=%2Flist%3Fstatus%3DOFFER');
  });

  it('guestGuard sends a signed-in user from /login to the board', async () => {
    signedIn = true;
    expect(serialize(await run(guestGuard, '/login'))).toBe('/board');
  });

  it('guestGuard shows /login to a signed-out user', async () => {
    expect(await run(guestGuard, '/login')).toBe(true);
  });
});
