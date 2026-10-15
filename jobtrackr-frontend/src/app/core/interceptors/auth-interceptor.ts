import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth-service';
import { Notifier } from '../notifications/notifier';

/** Marks a request that is already the retry after a refresh, so it is never refreshed twice. */
const RETRIED_AFTER_REFRESH = new HttpContextToken<boolean>(() => false);

/**
 * Adds the Bearer token to calls to OUR API only (never leak it to another host). When a protected call
 * fails with 401 (the 15-minute access token expired), it refreshes once using the httpOnly cookie and
 * retries the original request once with the new token. Only if that fails is the session over.
 * Several calls failing together share one refresh (AuthService.refreshAccessToken).
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notifier = inject(Notifier);

  const isApiCall = request.url.startsWith(environment.apiBaseUrl);
  // /auth/* never needs a Bearer token, and its 401s mean "wrong password" or "no session", not "expired"
  const isAuthEndpoint = request.url.startsWith(`${environment.apiBaseUrl}/auth/`);
  const isProtectedCall = isApiCall && !isAuthEndpoint;

  const withToken = (outgoing: HttpRequest<unknown>, token: string | null) =>
    isProtectedCall && token ? outgoing.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : outgoing;

  const endSession = (): void => {
    // Several requests can fail together; only the first one (still signed in) reacts
    if (auth.isAuthenticated()) {
      auth.clearSession();
      notifier.show('Your session has expired. Please log in again.');
      void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
    }
  };

  const isUnauthorized = (error: unknown) => error instanceof HttpErrorResponse && error.status === 401;

  const retryWith = (token: string) =>
    next(withToken(request.clone({ context: request.context.set(RETRIED_AFTER_REFRESH, true) }), token)).pipe(
      catchError((retryError: unknown) => {
        if (isUnauthorized(retryError)) {
          endSession(); // even a fresh token was refused: the account is gone or the session revoked
        }
        return throwError(() => retryError);
      }),
    );

  // Remember which token this request carried, to tell "expired" from "already replaced meanwhile"
  const sentToken = auth.token();

  return next(withToken(request, sentToken)).pipe(
    catchError((error: unknown) => {
      if (!isProtectedCall || !isUnauthorized(error)) {
        return throwError(() => error);
      }
      if (request.context.get(RETRIED_AFTER_REFRESH)) {
        endSession();
        return throwError(() => error);
      }
      // Another request already refreshed while this one was on its way: just retry with the new token
      // (a second refresh would rotate the cookie again for nothing)
      const currentToken = auth.token();
      if (currentToken && currentToken !== sentToken) {
        return retryWith(currentToken);
      }
      return auth.refreshAccessToken().pipe(
        catchError(() => {
          endSession(); // no valid refresh cookie: the session really is over
          return throwError(() => error);
        }),
        switchMap((token) => retryWith(token)),
      );
    }),
  );
};
