import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Notifier } from '../notifications/notifier';
import { catchError, throwError } from 'rxjs';
import { problemMessage } from '../api/problem-message';

/**
 * Set on a request whose caller shows the error itself (a form with inline errors, a dialog that
 * handles 409), so the user never gets the same message twice.
 */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

/** Shows one snackbar per failed request, using the server's ProblemDetail text when there is one. */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const notifier = inject(Notifier);

  return next(request).pipe(
    catchError((error: unknown) => {
      const handledElsewhere =
        request.context.get(SKIP_ERROR_TOAST) ||
        // 401 is owned by the auth interceptor (it clears the session and shows its own message)
        (error instanceof HttpErrorResponse && error.status === 401);

      if (!handledElsewhere) {
        notifier.show(problemMessage(error));
      }
      // Always rethrow: callers still need to know the call failed (e.g. to roll back a move)
      return throwError(() => error);
    }),
  );
};
