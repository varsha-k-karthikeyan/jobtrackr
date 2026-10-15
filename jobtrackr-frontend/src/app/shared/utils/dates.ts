import { IsoDate } from '../../core/models/application.model';

/**
 * Today as "yyyy-MM-dd" in the USER'S time zone. The backend's LocalDate values (appliedOn, followUpOn)
 * are calendar dates with no zone, so they must be compared with the local calendar date, never with
 * `new Date().toISOString()`, which is UTC and is "tomorrow" or "yesterday" for part of every day in India.
 */
export function todayIsoDate(now: Date = new Date()): IsoDate {
  return dateToIsoDate(now);
}

export type FollowUpState = 'overdue' | 'today' | 'upcoming';

/** ISO dates compare correctly as strings ("2026-10-09" < "2026-10-14"), so no Date parsing is needed. */
export function followUpState(followUpOn: IsoDate, today: IsoDate = todayIsoDate()): FollowUpState {
  if (followUpOn < today) {
    return 'overdue';
  }
  return followUpOn === today ? 'today' : 'upcoming';
}

/** Local calendar date -> "yyyy-MM-dd" (what the backend's LocalDate expects). */
export function dateToIsoDate(date: Date): IsoDate {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/**
 * "yyyy-MM-dd" -> a Date at LOCAL midnight. `new Date("2026-10-14")` would be UTC midnight, which the
 * datepicker would show as the 13th in any zone west of UTC.
 */
export function isoDateToDate(isoDate: IsoDate | null): Date | null {
  if (!isoDate) {
    return null;
  }
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(year, month - 1, day);
}
