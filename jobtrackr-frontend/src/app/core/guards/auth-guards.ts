import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth-service';

/**
 * Signed-in users only. With no valid access token (e.g. a reload after it expired) it first tries to
 * renew the session silently from the refresh cookie; only then does it send the user to /login.
 */
export const authGuard: CanActivateFn = async (_route, state) => {
  // inject() before any await: it only works synchronously, inside the injection context
  const auth = inject(AuthService);
  const router = inject(Router);
  if (await auth.tryRestoreSession()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** /login and /register are pointless when already signed in (or silently re-signable): go to the board. */
export const guestGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return (await auth.tryRestoreSession()) ? router.createUrlTree(['/board']) : true;
};

/**
 * Only follow in-app paths after login. Without this, /login?returnUrl=https://evil.example would
 * turn the login page into an open redirect.
 */
export function safeReturnUrl(returnUrl: string | null): string {
  return returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/board';
}
