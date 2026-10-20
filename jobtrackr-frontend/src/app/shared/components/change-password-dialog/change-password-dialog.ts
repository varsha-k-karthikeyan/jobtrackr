import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { problemFieldErrors, problemMessage } from '../../../core/api/problem-message';
import { AuthService } from '../../../core/auth/auth-service';
import { matchesControl } from '../../validators/matches-control';
import { passwordStrengthValidator } from '../../validators/password-strength';
import { PasswordRules } from '../password-rules/password-rules';

/** Opens the dialog. Resolves (through afterClosed) to true when the password was changed. */
export function openChangePasswordDialog(dialog: MatDialog): MatDialogRef<ChangePasswordDialog, boolean> {
  return dialog.open<ChangePasswordDialog, void, boolean>(ChangePasswordDialog, {
    width: 'min(35rem, 100vw)',
    maxWidth: '100vw',
    autoFocus: 'first-tabbable',
    // Header tile, divider and grey footer shared with the add/edit dialog (styles/_form-dialog.scss)
    panelClass: 'app-form-dialog',
  });
}

// Change password for the signed-in user (PUT /me/password). On success the backend has already ended every
// other session, and AuthService has stored the fresh access token, so this browser simply stays signed in.
@Component({
  selector: 'app-change-password-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    PasswordRules,
  ],
  templateUrl: './change-password-dialog.html',
  styles: `
    // The info icon in the "other devices will be signed out" note, aligned with the first line of text
    .info-icon {
      flex: none;
      width: 18px;
      height: 18px;
      margin-top: 1px;
      font-size: 18px;
    }
  `,
})
export class ChangePasswordDialog {
  private readonly auth = inject(AuthService);
  private readonly dialogRef = inject<MatDialogRef<ChangePasswordDialog, boolean>>(MatDialogRef);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = inject(NonNullableFormBuilder).group({
    // No rules on the current password, like login: it only has to match
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, passwordStrengthValidator]],
    confirmPassword: ['', [Validators.required, matchesControl('newPassword')]],
  });

  protected readonly newPasswordValue = toSignal(this.form.controls.newPassword.valueChanges, { initialValue: '' });
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly hideCurrent = signal(true);
  protected readonly hideNew = signal(true);

  constructor() {
    // Editing the new password after the confirmation must re-check the confirmation too
    this.form.controls.newPassword.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.confirmPassword.updateValueAndValidity({ emitEvent: false }));
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.serverError.set(null);
    this.saving.set(true);
    this.dialogRef.disableClose = true; // no closing half-way through a save

    this.auth
      .changePassword({ currentPassword, newPassword })
      .pipe(
        finalize(() => {
          this.saving.set(false);
          this.dialogRef.disableClose = false;
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.dialogRef.close(true),
        error: (error: unknown) => this.showServerError(error),
      });
  }

  /** 400 field errors go under their fields (e.g. "Current password is incorrect"); anything else in the banner. */
  private showServerError(error: unknown): void {
    const fieldErrors = problemFieldErrors(error);
    let shownUnderAField = false;
    for (const [field, message] of Object.entries(fieldErrors)) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
        shownUnderAField = true;
      }
    }
    if (!shownUnderAField) {
      // e.g. the demo account's 403, whose ProblemDetail explains why
      this.serverError.set(problemMessage(error));
    }
  }
}
