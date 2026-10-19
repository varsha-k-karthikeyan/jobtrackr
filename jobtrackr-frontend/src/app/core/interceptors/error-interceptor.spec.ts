import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Notifier } from '../notifications/notifier';
import { errorInterceptor, SKIP_ERROR_TOAST } from './error-interceptor';

const URL = 'http://localhost:8080/api/v1/applications';

describe('errorInterceptor', () => {
  const show = vi.fn();
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    show.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: Notifier, useValue: { show } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  /** Sends a GET that the backend answers with `status`; resolves to the error the caller received. */
  function failWith(status: number, body: object | null, context?: HttpContext): unknown {
    let received: unknown = null;
    http.get(URL, { context }).subscribe({ error: (error: unknown) => (received = error) });
    backend.expectOne(URL).flush(body, { status, statusText: 'Error' });
    return received;
  }

  it("shows exactly one snackbar per failed request, with the server's ProblemDetail text", () => {
    failWith(404, { status: 404, detail: 'Application 42 not found' });

    expect(show).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledWith('Application 42 not found');
  });

  it('still hands the error to the caller (e.g. so a failed move can roll back)', () => {
    const received = failWith(500, null);

    expect(received).toMatchObject({ status: 500 });
  });

  it('stays quiet when the caller shows the error itself (SKIP_ERROR_TOAST)', () => {
    const received = failWith(409, { status: 409, detail: 'Stale version' }, new HttpContext().set(SKIP_ERROR_TOAST, true));

    expect(show).not.toHaveBeenCalled();
    expect(received).toMatchObject({ status: 409 }); // the caller still gets it
  });

  it('leaves 401 to the auth interceptor, which owns sessions', () => {
    failWith(401, null);

    expect(show).not.toHaveBeenCalled();
  });

  it('shows nothing for a successful request', () => {
    http.get(URL).subscribe();
    backend.expectOne(URL).flush([]);

    expect(show).not.toHaveBeenCalled();
  });

  it('explains a network failure (status 0) instead of showing a blank message', () => {
    let received: unknown = null;
    http.get(URL).subscribe({ error: (error: unknown) => (received = error) });
    backend.expectOne(URL).error(new ProgressEvent('error'));

    expect(received).toMatchObject({ status: 0 });
    expect(show).toHaveBeenCalledWith("Can't reach the server. Check that the backend is running.");
  });
});
