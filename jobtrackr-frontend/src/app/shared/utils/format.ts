import { Application } from '../../core/models/application.model';

/** "₹18.5–24 LPA", "₹18+ LPA", "Up to ₹24 LPA", or null when no salary is known. */
export function formatSalary(application: Pick<Application, 'salaryMinLpa' | 'salaryMaxLpa'>): string | null {
  const { salaryMinLpa: min, salaryMaxLpa: max } = application;
  if (min !== null && max !== null) {
    return min === max ? `₹${formatLpa(min)} LPA` : `₹${formatLpa(min)}–${formatLpa(max)} LPA`;
  }
  if (min !== null) {
    return `₹${formatLpa(min)}+ LPA`;
  }
  return max !== null ? `Up to ₹${formatLpa(max)} LPA` : null;
}

/** One amount for a table cell: 18.5 -> "₹18.5 LPA", null -> "—". */
export function formatLpaAmount(value: number | null): string {
  return value === null ? '—' : `₹${formatLpa(value)} LPA`;
}

/** 18.5 -> "18.5", 24 -> "24", 18.25 -> "18.25": never "24.00". */
function formatLpa(value: number): string {
  return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(2)));
}
