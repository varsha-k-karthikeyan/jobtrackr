import { dateToIsoDate, followUpState, isoDateToDate, todayIsoDate } from './dates';

describe('dates', () => {
  it('formats the LOCAL calendar date, not the UTC one', () => {
    // 00:30 local on 14 Oct: in UTC+5:30 that is still 13 Oct in UTC, but the user's date is the 14th
    expect(todayIsoDate(new Date(2026, 9, 14, 0, 30))).toBe('2026-10-14');
  });

  it('round-trips between yyyy-MM-dd and a local Date', () => {
    const date = isoDateToDate('2026-02-05');
    expect(date).toEqual(new Date(2026, 1, 5));
    expect(dateToIsoDate(date as Date)).toBe('2026-02-05');
    expect(isoDateToDate(null)).toBeNull();
  });

  it('classifies follow-up dates against today', () => {
    expect(followUpState('2026-10-13', '2026-10-14')).toBe('overdue');
    expect(followUpState('2026-10-14', '2026-10-14')).toBe('today');
    expect(followUpState('2026-10-15', '2026-10-14')).toBe('upcoming');
  });
});
