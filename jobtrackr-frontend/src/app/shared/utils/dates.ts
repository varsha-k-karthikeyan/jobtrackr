import { IsoDate, IsoInstant } from '../../core/models/application.model';

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

export type Meridiem = 'AM' | 'PM';

/** The interview time suggestions: 12:00, 12:30, 1:00 … 11:30, in the order a clock face reads within AM or PM. */
export const HALF_HOUR_TIMES: readonly string[] = Array.from(
  { length: 24 },
  (_, step) => `${Math.floor(step / 2) === 0 ? 12 : Math.floor(step / 2)}:${step % 2 === 0 ? '00' : '30'}`,
);

/**
 * The suggestions that start with what the user typed, so the combo box narrows as they type: "2" offers
 * 2:00 and 2:30, "10:" offers 10:00 and 10:30. "07.30" is read like "7:30". Empty text offers every step.
 */
export function matchingTimes(typed: string): readonly string[] {
  const normalised = typed.trim().replace('.', ':').replace(/^0(?=\d)/, '');
  return normalised === '' ? HALF_HOUR_TIMES : HALF_HOUR_TIMES.filter((time) => time.startsWith(normalised));
}

/**
 * A 12-hour clock time ("7", "7:30", "07.30") plus a separately chosen AM/PM, as 24-hour hours and
 * minutes. 12 AM is midnight and 12 PM is noon. Null when the text is not a valid 12-hour time.
 */
export function parseTwelveHourTime(text: string, meridiem: Meridiem): { hours: number; minutes: number } | null {
  const match = /^\s*(\d{1,2})(?:[:.](\d{2}))?\s*$/.exec(text);
  if (!match) {
    return null;
  }
  const hourOnDial = Number(match[1]);
  const minutes = Number(match[2] ?? 0);
  if (hourOnDial < 1 || hourOnDial > 12 || minutes > 59) {
    return null;
  }
  return { hours: (hourOnDial % 12) + (meridiem === 'PM' ? 12 : 0), minutes };
}

/** The local clock time of `date` the way the form shows it: { time: "7:30", meridiem: "PM" }. */
export function toTwelveHourTime(date: Date): { time: string; meridiem: Meridiem } {
  const hours = date.getHours();
  const hourOnDial = hours % 12 === 0 ? 12 : hours % 12;
  return { time: `${hourOnDial}:${String(date.getMinutes()).padStart(2, '0')}`, meridiem: hours < 12 ? 'AM' : 'PM' };
}

/**
 * The calendar day of `date` at the given 24-hour time, both in the user's own time zone, as one exact
 * instant in ISO form ("2026-10-21T09:00:00.000Z"). The backend stores interviews as instants, so the
 * reminder fires at the moment the user meant, whatever zone the server runs in.
 */
export function combineDateAndTime(date: Date, hours: number, minutes: number): IsoInstant {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), hours, minutes).toISOString();
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
