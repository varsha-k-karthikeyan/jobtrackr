import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { problemMessage } from '../../../core/api/problem-message';
import { AuthService } from '../../../core/auth/auth-service';
import { DEMO_CREDENTIALS } from '../../../core/auth/demo-credentials';
import { safeReturnUrl } from '../../../core/guards/auth-guards';
import { LoginRequest } from '../../../core/models/auth.model';

/** Which button started the current request, so only that one shows a spinner. */
type SignInSource = 'form' | 'demo';

@Component({
  selector: 'app-login-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** ?returnUrl=... set by the auth guard; bound by withComponentInputBinding(). */
  readonly returnUrl = input<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });
  protected readonly submitting = signal<SignInSource | null>(null);
  protected readonly serverError = signal<string | null>(null);
  protected readonly hidePassword = signal(true);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.signIn(this.form.getRawValue(), 'form');
  }

  protected tryDemo(): void {
    this.signIn(DEMO_CREDENTIALS, 'demo');
  }

  private signIn(credentials: LoginRequest, source: SignInSource): void {
    this.serverError.set(null);
    this.submitting.set(source);
    this.auth
      .login(credentials)
      .pipe(
        finalize(() => this.submitting.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
        error: (error: unknown) =>
          this.serverError.set(
            error instanceof HttpErrorResponse && error.status === 401
              ? 'Invalid email or password.'
              : problemMessage(error),
          ),
      });
  }
}
