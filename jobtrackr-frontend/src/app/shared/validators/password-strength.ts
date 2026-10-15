import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

// Mirrors the backend's PasswordPolicy (jobtrackr-backend: auth/dto/PasswordPolicy.java) so users get
// instant feedback. The backend remains the enforcement; keep the two in step when the rules change.

export type PasswordRuleKey = 'length' | 'uppercase' | 'lowercase' | 'special';

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 20;

export interface PasswordRule {
  key: PasswordRuleKey;
  label: string;
  isMet: (password: string) => boolean;
}

export const PASSWORD_RULES: readonly PasswordRule[] = [
  {
    key: 'length',
    label: `${PASSWORD_MIN_LENGTH} to ${PASSWORD_MAX_LENGTH} characters`,
    isMet: (password) => password.length >= PASSWORD_MIN_LENGTH && password.length <= PASSWORD_MAX_LENGTH,
  },
  { key: 'uppercase', label: 'One uppercase letter', isMet: (password) => /[A-Z]/.test(password) },
  { key: 'lowercase', label: 'One lowercase letter', isMet: (password) => /[a-z]/.test(password) },
  // Anything that is not a letter, digit or whitespace, e.g. @ # ! _ -
  { key: 'special', label: 'One special character (e.g. @ # ! _)', isMet: (password) => /[^A-Za-z0-9\s]/.test(password) },
];

/** Error shape: { passwordStrength: { missing: ['uppercase', 'special'] } }. Empty values are left to `required`. */
export const passwordStrengthValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const password = typeof control.value === 'string' ? control.value : '';
  if (password === '') {
    return null;
  }
  const missing = PASSWORD_RULES.filter((rule) => !rule.isMet(password)).map((rule) => rule.key);
  return missing.length > 0 ? { passwordStrength: { missing } } : null;
};
