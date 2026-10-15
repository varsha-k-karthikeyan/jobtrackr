import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { DateAdapter, MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { problemFieldErrors, problemMessage } from '../../../core/api/problem-message';
import {
  Application,
  APPLICATION_SOURCES,
  APPLICATION_STATUS_LABELS,
  APPLICATION_STATUSES,
  ApplicationStatus,
} from '../../../core/models/application.model';
import { ApplicationStore } from '../../../core/state/application-store';
import { dateToIsoDate, isoDateToDate } from '../../utils/dates';
import { DayMonthYearDateAdapter } from '../../utils/day-month-year-date-adapter';
import { maxDecimalPlaces, salaryRangeValidator } from './application-form-validators';

export type ApplicationDialogData =
  | { mode: 'create'; status: ApplicationStatus }
  | { mode: 'edit'; application: Application };

// One dialog for create and edit. Closes with the saved Application, or undefined when cancelled.
@Component({
  selector: 'app-application-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDatepickerModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './application-dialog.html',
  styleUrl: './application-dialog.scss',
  // Datepicker setup lives here, not in app.config: this dialog is the only datepicker user, so the
  // date code loads lazily with it instead of growing the initial bundle.
  // Native Date objects, shown and typed as dd/mm/yyyy (Indian convention).
  providers: [
    provideNativeDateAdapter(),
    { provide: DateAdapter, useClass: DayMonthYearDateAdapter },
    { provide: MAT_DATE_LOCALE, useValue: 'en-IN' },
  ],
})
export class ApplicationDialog {
  private readonly store = inject(ApplicationStore);
  private readonly dialogRef = inject<MatDialogRef<ApplicationDialog, Application>>(MatDialogRef);
  protected readonly data = inject<ApplicationDialogData>(MAT_DIALOG_DATA);

  protected readonly statuses = APPLICATION_STATUSES;
  protected readonly statusLabels = APPLICATION_STATUS_LABELS;
  protected readonly sources = APPLICATION_SOURCES;
  protected readonly isEdit = this.data.mode === 'edit';
  /** The column the card was in when the dialog opened; a different choice shows where it will land. */
  protected originalStatus: ApplicationStatus | null =
    this.data.mode === 'edit' ? this.data.application.status : null;

  protected readonly saving = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  /** Set after a 409: the form was refreshed with someone else's newer version. */
  protected readonly conflict = signal(false);

  /** The version the next PUT must carry; replaced when a 409 reloads the card. */
  private version = this.data.mode === 'edit' ? this.data.application.version : 0;

  // Typed form. Limits mirror the backend's request records, so most mistakes never reach the server.
  private readonly formBuilder = inject(FormBuilder);
  protected readonly form = this.formBuilder.group(
    {
      company: this.formBuilder.nonNullable.control('', [Validators.required, Validators.maxLength(150)]),
      role: this.formBuilder.nonNullable.control('', [Validators.required, Validators.maxLength(150)]),
      status: this.formBuilder.nonNullable.control<ApplicationStatus>('APPLIED'),
      jdUrl: this.formBuilder.nonNullable.control('', [Validators.pattern(/^https?:\/\/\S+$/)]),
      jdText: this.formBuilder.nonNullable.control(''),
      location: this.formBuilder.nonNullable.control('', [Validators.maxLength(100)]),
      salaryMinLpa: this.formBuilder.control<number | null>(null, [
        Validators.min(0),
        Validators.max(999.99),
        maxDecimalPlaces(2),
      ]),
      salaryMaxLpa: this.formBuilder.control<number | null>(null, [
        Validators.min(0),
        Validators.max(999.99),
        maxDecimalPlaces(2),
      ]),
      source: this.formBuilder.control<string | null>(null),
      appliedOn: this.formBuilder.control<Date | null>(null),
      followUpOn: this.formBuilder.control<Date | null>(null),
    },
    { validators: salaryRangeValidator },
  );

  constructor() {
    if (this.data.mode === 'edit') {
      this.fillForm(this.data.application);
    } else {
      this.form.controls.status.setValue(this.data.status);
      this.form.controls.appliedOn.setValue(new Date());
    }
  }

  protected async save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const fields = {
      company: value.company.trim(),
      role: value.role.trim(),
      jdUrl: blankToNull(value.jdUrl),
      jdText: blankToNull(value.jdText),
      location: blankToNull(value.location),
      salaryMinLpa: value.salaryMinLpa,
      salaryMaxLpa: value.salaryMaxLpa,
      source: value.source,
      appliedOn: value.appliedOn ? dateToIsoDate(value.appliedOn) : null,
      followUpOn: value.followUpOn ? dateToIsoDate(value.followUpOn) : null,
    };

    this.saving.set(true);
    this.errorMessage.set(null);
    try {
      // skipErrorToast: every error is shown inside this dialog
      const saved =
        this.data.mode === 'edit'
          ? await this.store.update(
              this.data.application.id,
              // A changed status moves the card to the bottom of that column (done by the backend)
              { ...fields, status: value.status, version: this.version },
              { skipErrorToast: true },
            )
          : await this.store.create({ ...fields, status: value.status }, { skipErrorToast: true });
      this.dialogRef.close(saved);
    } catch (error: unknown) {
      await this.showError(error);
    } finally {
      this.saving.set(false);
    }
  }

  private async showError(error: unknown): Promise<void> {
    if (this.data.mode === 'edit' && error instanceof HttpErrorResponse && error.status === 409) {
      // Someone saved this card after we opened it. Load their version so the user edits fresh data.
      const latest = await this.store.reloadCard(this.data.application.id);
      if (latest) {
        this.version = latest.version;
        this.originalStatus = latest.status;
        this.fillForm(latest);
        this.conflict.set(true);
      } else {
        this.errorMessage.set('This application was deleted elsewhere, so it can no longer be saved.');
      }
      return;
    }
    // 400: put each server message under its field; anything else goes in the banner
    const fieldErrors = problemFieldErrors(error);
    let unmatched = Object.keys(fieldErrors).length === 0;
    for (const [field, message] of Object.entries(fieldErrors)) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
      } else {
        unmatched = true;
      }
    }
    if (unmatched) {
      this.errorMessage.set(problemMessage(error));
    }
  }

  private fillForm(application: Application): void {
    this.form.patchValue({
      company: application.company,
      role: application.role,
      status: application.status,
      jdUrl: application.jdUrl ?? '',
      jdText: application.jdText ?? '',
      location: application.location ?? '',
      salaryMinLpa: application.salaryMinLpa,
      salaryMaxLpa: application.salaryMaxLpa,
      source: application.source,
      appliedOn: isoDateToDate(application.appliedOn),
      followUpOn: isoDateToDate(application.followUpOn),
    });
  }
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}
