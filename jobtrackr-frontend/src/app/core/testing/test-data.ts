// Builders shared by unit tests. Not imported by application code.
import { Application, ApplicationStatus, Board } from '../models/application.model';

export function makeCard(id: number, status: ApplicationStatus, position: number, overrides: Partial<Application> = {}): Application {
  return {
    id,
    company: `Company ${id}`,
    role: 'Angular Developer',
    status,
    position,
    jdUrl: null,
    jdText: null,
    location: 'Chennai',
    salaryMinLpa: null,
    salaryMaxLpa: null,
    source: null,
    appliedOn: '2026-10-01',
    followUpOn: null,
    interviewAt: null,
    version: 0,
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
    ...overrides,
  };
}

/** Board from cards; each card's column comes from its own status. */
export function makeBoard(...cards: Application[]): Board {
  const board: Board = { APPLIED: [], INTERVIEW: [], OFFER: [], REJECTED: [] };
  for (const card of cards) {
    board[card.status].push(card);
  }
  return board;
}

/** Unsigned JWT with the given expiry; enough for client-side code that only reads `exp`. */
export function makeJwt(expiresAtMs: number): string {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256' })}.${encode({ sub: '1', exp: Math.floor(expiresAtMs / 1000) })}.signature`;
}

/** Lets awaited promises inside the code under test continue (e.g. a reload after a failed move). */
export function flushMicrotasks(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve));
}
