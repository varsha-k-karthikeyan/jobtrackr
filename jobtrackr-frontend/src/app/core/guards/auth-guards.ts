import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth-service';

/** Signed-in users only. Others go to /login, which sends them back here afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** /login and /register are pointless when already signed in. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? inject(Router).createUrlTree(['/board']) : true;
};

/**
 * Only follow in-app paths after login. Without this, /login?returnUrl=https://evil.example would
 * turn the login page into an open redirect.
 */
export function safeReturnUrl(returnUrl: string | null): string {
  return returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/board';
}
