import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { problemFieldErrors, problemMessage } from '../../../core/api/problem-message';
import { AuthService } from '../../../core/auth/auth-service';
import { PasswordRules } from '../../../shared/components/password-rules/password-rules';
import { matchesControl } from '../../../shared/validators/matches-control';
import { passwordStrengthValidator } from '../../../shared/validators/password-strength';
import { AuthLayout } from '../../../shared/components/auth-layout/auth-layout';

// Target of the emailed link (/reset-password?token=...). The token is read once and then removed from the
// address bar, so it does not linger in the browser history or on screen. It is sent in the request body.
// No route guard: a signed-in user who clicks the link must still be able to use it.
@Component({
  selector: 'app-reset-password-page',
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
  templateUrl: './reset-password-page.html',
})
export class ResetPasswordPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Captured before the URL is cleaned; null when the page was opened without a token. */
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');

  protected readonly hasToken = this.token !== null && this.token !== '';
  protected readonly form = inject(NonNullableFormBuilder).group({
    newPassword: ['', [Validators.required, passwordStrengthValidator]],
    confirmPassword: ['', [Validators.required, matchesControl('newPassword')]],
  });
  protected readonly newPasswordValue = toSignal(this.form.controls.newPassword.valueChanges, { initialValue: '' });
  protected readonly submitting = signal(false);
  protected readonly serverError = signal<string | null>(null);
  /** The link is used, expired or made up: show the "request a new link" state. */
  protected readonly linkInvalid = signal(false);
  protected readonly hidePassword = signal(true);

  constructor() {
    if (this.hasToken) {
      // Same route, so the component stays; only the address bar changes (and history keeps no token)
      void this.router.navigate([], { queryParams: {}, replaceUrl: true });
    }
    this.form.controls.newPassword.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.confirmPassword.updateValueAndValidity({ emitEvent: false }));
  }

  protected submit(): void {
    if (this.form.invalid || !this.token) {
      this.form.markAllAsTouched();
      return;
    }
    this.serverError.set(null);
    this.submitting.set(true);
    this.auth
      .resetPassword({ token: this.token, newPassword: this.form.controls.newPassword.value })
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          // The backend ended every session of this account, so any session in this browser is gone too
          this.auth.clearSession();
          void this.router.navigate(['/login'], { queryParams: { reset: 'done' } });
        },
        error: (error: unknown) => this.showServerError(error),
      });
  }

  private showServerError(error: unknown): void {
    const newPasswordError = problemFieldErrors(error)['newPassword'];
    if (newPasswordError) {
      this.form.controls.newPassword.setErrors({ server: newPasswordError });
      this.form.controls.newPassword.markAsTouched();
    } else if (error instanceof HttpErrorResponse && error.status === 400) {
      this.linkInvalid.set(true);
    } else {
      this.serverError.set(problemMessage(error));
    }
  }
}
