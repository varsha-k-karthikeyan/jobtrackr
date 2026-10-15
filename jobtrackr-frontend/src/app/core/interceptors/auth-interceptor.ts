import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Notifier } from '../notifications/notifier';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth-service';

/**
 * Adds the Bearer token to calls to OUR API only (never leak it to another host), and turns a 401
 * from a protected endpoint into "session over": clear state, one message, go to /login.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notifier = inject(Notifier);

  const isApiCall = request.url.startsWith(environment.apiBaseUrl);
  const token = auth.token();
  const authorised =
    isApiCall && token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;

  return next(authorised).pipe(
    catchError((error: unknown) => {
      // A 401 from /auth/login means wrong credentials, not an expired session: the form handles it
      const isAuthEndpoint = request.url.startsWith(`${environment.apiBaseUrl}/auth/`);
      if (isApiCall && !isAuthEndpoint && error instanceof HttpErrorResponse && error.status === 401) {
        // Several requests can fail together; only the first one (still signed in) reacts
        if (auth.isAuthenticated()) {
          auth.clearSession();
          notifier.show('Your session has expired. Please log in again.');
          void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
        }
      }
      return throwError(() => error);
    }),
  );
};
