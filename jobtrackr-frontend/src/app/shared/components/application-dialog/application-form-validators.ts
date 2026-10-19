import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { parseTwelveHourTime } from '../../utils/dates';

/** Group validator: salary min must not exceed max. Error `salaryRange` sits on the form group. */
export const salaryRangeValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const min: unknown = group.get('salaryMinLpa')?.value;
  const max: unknown = group.get('salaryMaxLpa')?.value;
  return typeof min === 'number' && typeof max === 'number' && min > max ? { salaryRange: true } : null;
};

/**
 * Group validator: an interview needs both a date and a time, or neither. A date alone would silently mean
 * midnight, and the "30 minutes before" reminder would arrive at 23:30 the night before.
 */
export const interviewDateTimeValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const hasDate = group.get('interviewDate')?.value != null;
  const time: unknown = group.get('interviewTime')?.value;
  const hasTime = typeof time === 'string' && time.trim() !== '';
  return hasDate === hasTime ? null : { interviewIncomplete: true };
};

/** A typed or picked 12-hour time ("7", "7:30", "7:45"); AM/PM is a separate field. Empty is left to the group rule. */
export const twelveHourTimeValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const text: unknown = control.value;
  if (typeof text !== 'string' || text.trim() === '') {
    return null;
  }
  return parseTwelveHourTime(text, 'AM') ? null : { twelveHourTime: true };
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
