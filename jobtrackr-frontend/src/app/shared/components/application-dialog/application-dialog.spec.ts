import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { environment } from '../../../../environments/environment';
import { makeCard } from '../../../core/testing/test-data';
import { ApplicationDialog, ApplicationDialogData } from './application-dialog';

describe('ApplicationDialog', () => {
  const close = vi.fn();

  function create(data: ApplicationDialogData) {
    close.mockReset();
    TestBed.configureTestingModule({
      imports: [ApplicationDialog],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    const fixture = TestBed.createComponent(ApplicationDialog);
    fixture.detectChanges();
    // `form` and `save` are protected (template-only); element access is fine in a test
    return { fixture, form: fixture.componentInstance['form'], component: fixture.componentInstance };
  }

  it('requires company and role', () => {
    const { form } = create({ mode: 'create', status: 'APPLIED' });

    expect(form.controls.company.hasError('required')).toBe(true);
    expect(form.controls.role.hasError('required')).toBe(true);
    form.patchValue({ company: 'Freshworks', role: 'Senior Frontend Engineer' });
    expect(form.valid).toBe(true);
  });

  it('rejects a minimum salary above the maximum (cross-field)', () => {
    const { form } = create({ mode: 'create', status: 'APPLIED' });
    form.patchValue({ company: 'Freshworks', role: 'Engineer' });

    form.patchValue({ salaryMinLpa: 30, salaryMaxLpa: 20 });
    expect(form.hasError('salaryRange')).toBe(true);
    expect(form.valid).toBe(false);

    form.patchValue({ salaryMinLpa: 20, salaryMaxLpa: 20 });
    expect(form.hasError('salaryRange')).toBe(false);
    expect(form.valid).toBe(true);
  });

  it('applies the same number and URL limits as the backend', () => {
    const { form } = create({ mode: 'create', status: 'APPLIED' });

    form.controls.salaryMinLpa.setValue(-1);
    expect(form.controls.salaryMinLpa.hasError('min')).toBe(true);
    form.controls.salaryMinLpa.setValue(1000);
    expect(form.controls.salaryMinLpa.hasError('max')).toBe(true);
    form.controls.salaryMinLpa.setValue(18.555);
    expect(form.controls.salaryMinLpa.hasError('maxDecimalPlaces')).toBe(true);

    form.controls.jdUrl.setValue('careers.example.com/job');
    expect(form.controls.jdUrl.hasError('pattern')).toBe(true);
    form.controls.jdUrl.setValue('https://careers.example.com/job');
    expect(form.controls.jdUrl.valid).toBe(true);
  });

  it('starts a new application in the requested column, applied today', () => {
    const { form } = create({ mode: 'create', status: 'OFFER' });

    expect(form.controls.status.value).toBe('OFFER');
    expect(form.controls.appliedOn.value?.toDateString()).toBe(new Date().toDateString());
  });

  it('prefills edit mode and converts dates to local Date objects', () => {
    const card = makeCard(5, 'INTERVIEW', 0, { company: 'Zoho', salaryMinLpa: 15, followUpOn: '2026-10-21' });
    const { form } = create({ mode: 'edit', application: card });

    expect(form.controls.company.value).toBe('Zoho');
    expect(form.controls.salaryMinLpa.value).toBe(15);
    expect(form.controls.followUpOn.value).toEqual(new Date(2026, 9, 21));
    expect(form.controls.status.value).toBe('INTERVIEW');
    expect(form.controls.status.enabled).toBe(true);
  });

  it('sends the chosen status and the version with an edit', async () => {
    const card = makeCard(5, 'INTERVIEW', 0, { company: 'Zoho', version: 7 });
    const { form, component } = create({ mode: 'edit', application: card });
    const http = TestBed.inject(HttpTestingController);
    form.controls.status.setValue('OFFER');

    const saving = component['save']();
    const request = http.expectOne(`${environment.apiBaseUrl}/applications/5`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toMatchObject({ company: 'Zoho', status: 'OFFER', version: 7 });
    request.flush({ ...card, status: 'OFFER', version: 8 });
    await saving;

    expect(close).toHaveBeenCalled();
    http.verify();
  });

  it('sends a clean create request (trimmed, blanks as null, dates as yyyy-MM-dd) and closes with the result', async () => {
    const { form, component } = create({ mode: 'create', status: 'APPLIED' });
    const http = TestBed.inject(HttpTestingController);
    form.patchValue({
      company: '  Freshworks ',
      role: 'Engineer',
      location: '   ',
      appliedOn: new Date(2026, 9, 14),
      followUpOn: null,
      source: 'Referral',
    });

    const saving = component['save']();
    const request = http.expectOne(`${environment.apiBaseUrl}/applications`);
    expect(request.request.body).toEqual({
      company: 'Freshworks',
      role: 'Engineer',
      status: 'APPLIED',
      jdUrl: null,
      jdText: null,
      location: null,
      salaryMinLpa: null,
      salaryMaxLpa: null,
      source: 'Referral',
      appliedOn: '2026-10-14',
      followUpOn: null,
    });
    const created = makeCard(9, 'APPLIED', 0, { company: 'Freshworks' });
    request.flush(created);
    await saving;

    expect(close).toHaveBeenCalledWith(created);
    http.verify();
  });

  it('does not send anything while the form is invalid', async () => {
    const { component } = create({ mode: 'create', status: 'APPLIED' });
    const http = TestBed.inject(HttpTestingController);

    await component['save']();

    http.expectNone(`${environment.apiBaseUrl}/applications`);
    expect(close).not.toHaveBeenCalled();
  });
});
