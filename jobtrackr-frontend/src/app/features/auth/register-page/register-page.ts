import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { problemFieldErrors, problemMessage } from '../../../core/api/problem-message';
import { AuthService } from '../../../core/auth/auth-service';
import { PasswordRules } from '../../../shared/components/password-rules/password-rules';
import { passwordStrengthValidator } from '../../../shared/validators/password-strength';
import { AuthLayout } from '../../../shared/components/auth-layout/auth-layout';

@Component({
  selector: 'app-register-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    AuthLayout,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    PasswordRules,
  ],
  templateUrl: './register-page.html',
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  // Limits mirror the backend's RegisterRequest so most mistakes are caught before the round trip
  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', [Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    // The 8-20 length is one of the strength rules, so it shows in the checklist with the others
    password: ['', [Validators.required, passwordStrengthValidator]],
  });

  // Live checklist under the password field: re-evaluated on every keystroke
  protected readonly passwordValue = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });
  protected readonly submitting = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly emailTaken = signal(false);
  protected readonly hidePassword = signal(true);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { fullName, email, password } = this.form.getRawValue();
    this.serverError.set(null);
    this.emailTaken.set(false);
    this.submitting.set(true);

    this.auth
      .register({ email, password, fullName: fullName.trim() || null })
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => void this.router.navigateByUrl('/board'),
        error: (error: unknown) => this.showServerError(error),
      });
  }

  /** 400 field errors go under their own fields; anything else goes in the banner. */
  private showServerError(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      this.emailTaken.set(true);
      return;
    }
    const fieldErrors = problemFieldErrors(error);
    const unmatched: string[] = [];
    for (const [field, message] of Object.entries(fieldErrors)) {
      const control: AbstractControl | null = this.form.get(field);
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
      } else {
        unmatched.push(`${field}: ${message}`);
      }
    }
    if (Object.keys(fieldErrors).length === 0) {
      this.serverError.set(problemMessage(error));
    } else if (unmatched.length > 0) {
      this.serverError.set(unmatched.join('; '));
    }
  }
}
