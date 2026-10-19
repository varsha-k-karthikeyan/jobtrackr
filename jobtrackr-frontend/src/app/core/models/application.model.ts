// Mirrors the backend records in jobtrackr-backend/.../application/dto. Field names match the JSON exactly.
// Java nulls arrive as null (not undefined), hence `| null` rather than optional properties.

/** The four board columns, in left-to-right order (same order as the Java enum ApplicationStatus). */
export const APPLICATION_STATUSES = ['APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED'] as const;

export type ApplicationStatus = (typeof APPLICATION_STATUSES)[number];

export const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  APPLIED: 'Applied',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  REJECTED: 'Rejected',
};

/** Where the job was found. The backend stores free text (max 50); the UI offers this list. */
export const APPLICATION_SOURCES = ['LinkedIn', 'Naukri', 'Referral', 'Company site', 'Other'] as const;

/** ISO date without time, e.g. "2026-10-14" (Java LocalDate). */
export type IsoDate = string;
/** ISO instant in UTC, e.g. "2026-10-14T10:09:01.636671Z" (Java Instant). */
export type IsoInstant = string;

/** ApplicationResponse: one card. */
export interface Application {
  id: number;
  company: string;
  role: string;
  status: ApplicationStatus;
  /** Zero-based index inside its status column. */
  position: number;
  jdUrl: string | null;
  jdText: string | null;
  location: string | null;
  /** Lakhs per annum. Java BigDecimal, serialised as a JSON number. */
  salaryMinLpa: number | null;
  salaryMaxLpa: number | null;
  source: string | null;
  appliedOn: IsoDate | null;
  followUpOn: IsoDate | null;
  /** Scheduled interview start, an exact instant (ISO with Z). Drives the reminder emails. */
  interviewAt: IsoInstant | null;
  /** Optimistic-lock counter; must be sent back unchanged on PUT. */
  version: number;
  createdAt: IsoInstant;
  updatedAt: IsoInstant;
}

/** Fields shared by create and update requests. */
interface ApplicationFields {
  company: string;
  role: string;
  jdUrl: string | null;
  jdText: string | null;
  location: string | null;
  salaryMinLpa: number | null;
  salaryMaxLpa: number | null;
  source: string | null;
  appliedOn: IsoDate | null;
  followUpOn: IsoDate | null;
  interviewAt: IsoInstant | null;
}

/** POST /applications. No position: the backend appends the card to the bottom of its column. */
export interface CreateApplicationRequest extends ApplicationFields {
  status: ApplicationStatus;
}

/**
 * PUT /applications/{id}. Replaces every editable field. A different `status` moves the card to the
 * BOTTOM of that column (null keeps it); to place a card at an exact position, use move instead.
 */
export interface UpdateApplicationRequest extends ApplicationFields {
  status: ApplicationStatus | null;
  version: number;
}

/** PATCH /applications/{id}/move. position = zero-based index in the target column after the move. */
export interface MoveApplicationRequest {
  status: ApplicationStatus;
  position: number;
}

/** GET /applications/board: every key is always present, cards ordered by position. */
export type Board = Record<ApplicationStatus, Application[]>;
