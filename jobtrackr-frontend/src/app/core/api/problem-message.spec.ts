import { HttpErrorResponse } from '@angular/common/http';
import { problemFieldErrors, problemMessage } from './problem-message';

function httpError(status: number, body: unknown = null): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('problemMessage', () => {
  it("prefers the server's ProblemDetail detail", () => {
    expect(problemMessage(httpError(409, { status: 409, detail: 'An account with this email already exists' })))
      .toBe('An account with this email already exists');
  });

  it('lists field errors when the body has an errors map', () => {
    expect(problemMessage(httpError(400, { status: 400, detail: 'Validation failed', errors: { company: 'must not be blank', role: 'must not be blank' } })))
      .toBe('company: must not be blank; role: must not be blank');
  });

  it('falls back to a readable sentence per status when there is no ProblemDetail', () => {
    expect(problemMessage(httpError(0))).toBe("Can't reach the server. Check that the backend is running.");
    expect(problemMessage(httpError(404, '<html>Not Found</html>'))).toBe('That item no longer exists.');
    expect(problemMessage(httpError(503))).toBe('Request failed (503). Please try again.');
  });

  it('handles errors that are not HTTP errors at all', () => {
    expect(problemMessage(new Error('boom'))).toBe('Something went wrong. Please try again.');
  });
});

describe('problemFieldErrors', () => {
  it('returns the field map of a 400 validation ProblemDetail', () => {
    expect(problemFieldErrors(httpError(400, { status: 400, errors: { currentPassword: 'Current password is incorrect' } })))
      .toEqual({ currentPassword: 'Current password is incorrect' });
  });

  it('returns an empty object for anything else', () => {
    expect(problemFieldErrors(httpError(409, { status: 409, detail: 'Conflict' }))).toEqual({});
    expect(problemFieldErrors(new Error('boom'))).toEqual({});
  });
});
