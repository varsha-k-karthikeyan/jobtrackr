import { HttpErrorResponse } from '@angular/common/http';
import { isProblemDetail } from '../models/problem-detail.model';

const FALLBACK_BY_STATUS: Record<number, string> = {
  0: "Can't reach the server. Check that the backend is running.",
  401: 'Your session has expired. Please log in again.',
  403: "You don't have permission to do that.",
  404: 'That item no longer exists.',
  409: 'This item was changed elsewhere. Reload and try again.',
};

/** Turns any HTTP error into one readable sentence, preferring the server's ProblemDetail text. */
export function problemMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Something went wrong. Please try again.';
  }
  const body: unknown = error.error;
  if (isProblemDetail(body)) {
    if (body.errors && Object.keys(body.errors).length > 0) {
      return Object.entries(body.errors)
        .map(([field, message]) => `${field}: ${message}`)
        .join('; ');
    }
    if (body.detail) {
      return body.detail;
    }
  }
  return FALLBACK_BY_STATUS[error.status] ?? `Request failed (${error.status}). Please try again.`;
}

/** Field-level messages from a 400 validation ProblemDetail, or an empty object. */
export function problemFieldErrors(error: unknown): Record<string, string> {
  if (error instanceof HttpErrorResponse && isProblemDetail(error.error) && error.error.errors) {
    return error.error.errors;
  }
  return {};
}
