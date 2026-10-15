import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { PASSWORD_RULES } from '../../validators/password-strength';

/**
 * Live checklist of the password rules under a "new password" field: each rule turns green as it is met.
 * Shared by registration, change password and reset password, so the rules always look the same.
 */
@Component({
  selector: 'app-password-rules',
  imports: [MatIconModule],
  template: `
    <ul class="password-rules m-0 grid list-none gap-1 p-0 text-xs sm:grid-cols-2" aria-label="Password rules">
      @for (rule of rules(); track rule.label) {
        <li class="flex items-center gap-1.5" [class.met]="rule.met">
          <mat-icon class="rule-icon" aria-hidden="true">{{ rule.met ? 'check_circle' : 'radio_button_unchecked' }}</mat-icon>
          <span>{{ rule.label }}</span>
          <span class="sr-only">{{ rule.met ? '(met)' : '(not met)' }}</span>
        </li>
      }
    </ul>
  `,
  styles: `
    // Grey until a rule is met, then the theme's primary colour
    .password-rules li {
      color: var(--mat-sys-on-surface-variant);

      &.met {
        color: var(--mat-sys-primary);
      }
    }

    .rule-icon {
      width: 16px;
      height: 16px;
      font-size: 16px;
    }
  `,
})
export class PasswordRules {
  /** The password typed so far. */
  readonly password = input.required<string>();

  protected readonly rules = computed(() =>
    PASSWORD_RULES.map((rule) => ({ label: rule.label, met: rule.isMet(this.password()) })),
  );
}
