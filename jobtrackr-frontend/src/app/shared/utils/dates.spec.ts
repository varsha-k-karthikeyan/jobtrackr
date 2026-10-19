import {
  combineDateAndTime,
  dateToIsoDate,
  followUpState,
  HALF_HOUR_TIMES,
  isoDateToDate,
  matchingTimes,
  parseTwelveHourTime,
  todayIsoDate,
  toTwelveHourTime,
} from './dates';

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

  it('reads a typed 12-hour time with a separate AM/PM', () => {
    expect(parseTwelveHourTime('7:30', 'PM')).toEqual({ hours: 19, minutes: 30 });
    expect(parseTwelveHourTime('7', 'AM')).toEqual({ hours: 7, minutes: 0 });
    expect(parseTwelveHourTime(' 07.05 ', 'AM')).toEqual({ hours: 7, minutes: 5 });
    expect(parseTwelveHourTime('12:00', 'AM')).toEqual({ hours: 0, minutes: 0 }); // midnight
    expect(parseTwelveHourTime('12:00', 'PM')).toEqual({ hours: 12, minutes: 0 }); // noon
    for (const invalid of ['13:00', '0:30', '7:60', '7:5', '', 'noon']) {
      expect(parseTwelveHourTime(invalid, 'AM')).toBeNull();
    }
  });

  it('suggests half-hour times that start with what was typed', () => {
    expect(HALF_HOUR_TIMES[0]).toBe('12:00');
    expect(HALF_HOUR_TIMES.at(-1)).toBe('11:30');
    expect(HALF_HOUR_TIMES).toHaveLength(24);
    expect(matchingTimes('')).toBe(HALF_HOUR_TIMES);
    expect(matchingTimes('1')).toEqual(['12:00', '12:30', '1:00', '1:30', '10:00', '10:30', '11:00', '11:30']);
    expect(matchingTimes('10:')).toEqual(['10:00', '10:30']);
    expect(matchingTimes('07.3')).toEqual(['7:30']);
    expect(matchingTimes('7:45')).toEqual([]);
  });

  it('shows a clock time back in the 12-hour form', () => {
    expect(toTwelveHourTime(new Date(2026, 9, 21, 19, 30))).toEqual({ time: '7:30', meridiem: 'PM' });
    expect(toTwelveHourTime(new Date(2026, 9, 21, 0, 5))).toEqual({ time: '12:05', meridiem: 'AM' });
    expect(toTwelveHourTime(new Date(2026, 9, 21, 12, 0))).toEqual({ time: '12:00', meridiem: 'PM' });
  });

  it('combines a local day and time into one exact instant', () => {
    expect(combineDateAndTime(new Date(2026, 9, 21), 19, 30)).toBe(new Date(2026, 9, 21, 19, 30).toISOString());
  });
});
