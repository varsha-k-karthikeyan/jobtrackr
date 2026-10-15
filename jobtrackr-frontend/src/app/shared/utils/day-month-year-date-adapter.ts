import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';

/**
 * Material's NativeDateAdapter displays dates in the locale's format (en-IN: 14/10/2026) but parses typed
 * text with Date.parse, which reads "04/10/2026" as April 10th. This adapter parses day/month/year, so
 * what users type matches what the datepicker shows. Picking from the calendar never goes through parse.
 */
@Injectable()
export class DayMonthYearDateAdapter extends NativeDateAdapter {
  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value === 'string') {
      const match = /^\s*(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\s*$/.exec(value);
      if (match) {
        const [, day, month, year] = match.map(Number);
        const date = new Date(year, month - 1, day);
        // Rejects impossible dates such as 31/02/2026, which Date would roll over into March
        return date.getMonth() === month - 1 ? date : this.invalid();
      }
    }
    return super.parse(value, parseFormat);
  }
}
