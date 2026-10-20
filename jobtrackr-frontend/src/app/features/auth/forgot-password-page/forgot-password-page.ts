import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { problemMessage } from '../../../core/api/problem-message';
import { AuthService } from '../../../core/auth/auth-service';
import { AuthLayout } from '../../../shared/components/auth-layout/auth-layout';

// "Forgot password?": asks for the email address and requests a reset link. The confirmation is worded the
// same whether or not the address is registered, matching the backend, which never reveals that either.
@Component({
  selector: 'app-forgot-password-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    AuthLayout,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './forgot-password-page.html',
})
export class ForgotPasswordPage {
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
  });
  protected readonly submitting = signal(false);
  protected readonly serverError = signal<string | null>(null);
  /** The address the link was requested for; set once the request succeeded. */
  protected readonly sentTo = signal<string | null>(null);
  /** Development only: the local Mailpit inbox where the email can be read. */
  protected readonly mailInboxUrl = environment.mailInboxUrl;

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const email = this.form.controls.email.value.trim();
    this.serverError.set(null);
    this.submitting.set(true);
    this.auth
      .forgotPassword({ email })
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.sentTo.set(email),
        error: (error: unknown) => this.serverError.set(problemMessage(error)),
      });
  }

  /** "Use a different address": back to the form. */
  protected startOver(): void {
    this.sentTo.set(null);
  }
}
