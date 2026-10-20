import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/**
 * Shared frame of the signed-out pages (log in, create account, forgot and reset password): the page
 * background with a light-blue band across the top, the logo, and a white card holding the page's own content
 * (projected with <ng-content>). Typography and buttons inside the card are global classes in
 * styles/_auth.scss (.auth-title, .auth-submit, ...), because projected content keeps its page's style scope.
 */
@Component({
  selector: 'app-auth-layout',
  imports: [MatIconModule],
  template: `
    <div class="relative min-h-dvh bg-page">
      <div class="absolute inset-x-0 top-0 h-[340px] border-b border-band-line bg-band" aria-hidden="true"></div>
      <div class="relative mx-auto flex w-full max-w-[520px] flex-col items-center px-4 pb-10 pt-14">
        <div class="mb-7 flex items-center gap-3" role="img" aria-label="JobTrackr">
          <span class="logo-tile flex size-11 items-center justify-center rounded-field bg-accent text-on-accent" aria-hidden="true">
            <mat-icon>work</mat-icon>
          </span>
          <span class="text-[28px] font-extrabold tracking-tight text-body" aria-hidden="true">Job<span class="text-accent">Trackr</span></span>
        </div>
        <main class="auth-card w-full">
          <ng-content />
        </main>
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .logo-tile {
      box-shadow: var(--shadow-primary);

      mat-icon {
        width: 24px;
        height: 24px;
        font-size: 24px;
      }
    }

    .auth-card {
      padding: 2.25rem;
      border-radius: var(--radius-dialog);
      background: var(--color-surface);
      box-shadow: var(--shadow-auth-card);

      @media (max-width: 480px) {
        padding: 1.5rem 1.25rem;
      }
    }
  `,
})
export class AuthLayout {}
