import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * "Confirm password": valid when the value equals the sibling control `otherControlName`.
 * Error shape: { mismatch: true }. Empty values are left to `required`.
 * The other control does not re-run this validator by itself: re-validate this control when it changes.
 */
export function matchesControl(otherControlName: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const other = control.parent?.get(otherControlName);
    if (!other || control.value === '' || control.value === other.value) {
      return null;
    }
    return { mismatch: true };
  };
}
