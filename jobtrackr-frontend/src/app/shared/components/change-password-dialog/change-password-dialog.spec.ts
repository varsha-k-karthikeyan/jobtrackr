import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { environment } from '../../../../environments/environment';
import { makeJwt } from '../../../core/testing/test-data';
import { ChangePasswordDialog } from './change-password-dialog';

const URL = `${environment.apiBaseUrl}/me/password`;

describe('ChangePasswordDialog', () => {
  const close = vi.fn();
  let http: HttpTestingController;

  function create() {
    close.mockReset();
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [ChangePasswordDialog],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: { close, disableClose: false } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ChangePasswordDialog);
    fixture.detectChanges();
    // `form`, `save` and `serverError` are protected (template-only); element access is fine in a test
    const component = fixture.componentInstance;
    return { fixture, component, form: component['form'] };
  }

  afterEach(() => http.verify());

  it('sends nothing while the form is invalid', () => {
    const { component, form } = create();

    component['save']();

    expect(form.controls.currentPassword.hasError('required')).toBe(true);
    expect(form.controls.newPassword.hasError('required')).toBe(true);
    http.expectNone(URL);
  });

  it('requires a strong new password and a matching confirmation, re-checked when the new one changes', () => {
    const { form } = create();

    form.controls.newPassword.setValue('weak');
    expect(form.controls.newPassword.hasError('passwordStrength')).toBe(true);

    form.patchValue({ newPassword: 'N3w@Secret', confirmPassword: 'N3w@Secret' });
    expect(form.controls.confirmPassword.valid).toBe(true);

    form.controls.newPassword.setValue('N3w@Secret2');
    expect(form.controls.confirmPassword.hasError('mismatch')).toBe(true);
  });

  it('sends only the current and new password, stores the fresh token, and closes with true', () => {
    const { component, form } = create();
    const token = makeJwt(Date.now() + 15 * 60_000);
    form.setValue({ currentPassword: 'Old@Pass1', newPassword: 'N3w@Secret', confirmPassword: 'N3w@Secret' });

    component['save']();
    const request = http.expectOne(URL);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ currentPassword: 'Old@Pass1', newPassword: 'N3w@Secret' });
    expect(request.request.withCredentials).toBe(true); // or the browser drops the new refresh cookie
    request.flush({ accessToken: token, tokenType: 'Bearer', expiresIn: 900 });

    expect(localStorage.getItem('jobtrackr.accessToken')).toBe(token);
    expect(close).toHaveBeenCalledWith(true);
  });

  it('shows "Current password is incorrect" under that field and stays open', () => {
    const { component, form } = create();
    form.setValue({ currentPassword: 'Wrong@Pass1', newPassword: 'N3w@Secret', confirmPassword: 'N3w@Secret' });

    component['save']();
    http.expectOne(URL).flush(
      { status: 400, detail: 'Validation failed', errors: { currentPassword: 'Current password is incorrect' } },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(form.controls.currentPassword.getError('server')).toBe('Current password is incorrect');
    expect(close).not.toHaveBeenCalled();
  });

  it("shows the demo account's 403 in the banner", () => {
    const { component, form } = create();
    form.setValue({ currentPassword: 'Demo@1234', newPassword: 'N3w@Secret', confirmPassword: 'N3w@Secret' });

    component['save']();
    http.expectOne(URL).flush(
      { status: 403, detail: "The demo account's password can't be changed. Register your own account to try this." },
      { status: 403, statusText: 'Forbidden' },
    );

    expect(component['serverError']()).toContain("demo account's password can't be changed");
    expect(close).not.toHaveBeenCalled();
  });
});
