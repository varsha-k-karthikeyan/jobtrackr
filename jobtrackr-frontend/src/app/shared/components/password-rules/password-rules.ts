import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { PASSWORD_RULES } from '../../validators/password-strength';

/**
 * Live checklist of the password rules under a "new password" field, in a box with a strength bar: each rule
 * turns green as it is met, and the bar fills one segment per met rule.
 * Shared by registration, change password and reset password, so the rules always look the same.
 */
@Component({
  selector: 'app-password-rules',
  imports: [MatIconModule],
  template: `
    <div class="rules-box">
      <!-- One segment per rule; the list below already says which are met, so the bar is decorative -->
      <div class="strength-bar" aria-hidden="true">
        @for (rule of rules(); track rule.label) {
          <span class="segment" [class.filled]="$index < metCount()"></span>
        }
      </div>
      <ul class="m-0 grid list-none gap-x-4 gap-y-1.5 p-0 text-[13px] sm:grid-cols-2" aria-label="Password rules">
        @for (rule of rules(); track rule.label) {
          <li class="rule flex items-center gap-2" [class.met]="rule.met">
            <mat-icon class="rule-icon" aria-hidden="true">{{ rule.met ? 'check_circle' : 'radio_button_unchecked' }}</mat-icon>
            <span>{{ rule.label }}</span>
            <span class="sr-only">{{ rule.met ? '(met)' : '(not met)' }}</span>
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    .rules-box {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      padding: 0.875rem 1rem;
      border: 1px solid var(--color-divider);
      border-radius: var(--radius-field);
      background: var(--color-surface-subtle);
    }

    .strength-bar {
      display: grid;
      grid-auto-columns: 1fr;
      grid-auto-flow: column;
      gap: 0.375rem;

      .segment {
        height: 4px;
        border-radius: var(--radius-pill);
        background: var(--color-progress-track);
        transition: background-color 150ms ease;

        &.filled {
          background: var(--color-success);
        }
      }
    }

    // Unmet: grey empty circle and grey text. Met: a filled green circle with a white tick, green text.
    .rule {
      color: var(--color-muted);

      &.met {
        color: var(--color-success-ink);
        font-weight: 600;

        .rule-icon {
          color: var(--color-success);
          font-variation-settings: 'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 20;
        }
      }
    }

    .rule-icon {
      flex: none;
      width: 18px;
      height: 18px;
      font-size: 18px;
    }
  `,
})
export class PasswordRules {
  /** The password typed so far. */
  readonly password = input.required<string>();

  protected readonly rules = computed(() =>
    PASSWORD_RULES.map((rule) => ({ label: rule.label, met: rule.isMet(this.password()) })),
  );
  protected readonly metCount = computed(() => this.rules().filter((rule) => rule.met).length);
}
