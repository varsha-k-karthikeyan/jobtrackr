import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Group validator: salary min must not exceed max. Error `salaryRange` sits on the form group. */
export const salaryRangeValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const min: unknown = group.get('salaryMinLpa')?.value;
  const max: unknown = group.get('salaryMaxLpa')?.value;
  return typeof min === 'number' && typeof max === 'number' && min > max ? { salaryRange: true } : null;
};

/** At most `places` decimals, matching the backend's NUMERIC(5,2) / @Digits(fraction = 2). */
export function maxDecimalPlaces(places: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value: unknown = control.value;
    if (typeof value !== 'number') {
      return null;
    }
    const decimals = (String(value).split('.')[1] ?? '').length;
    return decimals > places ? { maxDecimalPlaces: { places } } : null;
  };
}
