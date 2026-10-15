/**
 * RFC 9457 error body (the successor of RFC 7807, same shape) produced by GlobalExceptionHandler.
 * Note: a missing or expired token gives a 401 with an EMPTY body (the security filter answers before
 * any controller runs), so never assume an error response carries one of these.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  /** Only on 400 validation errors: field name -> message, e.g. { company: "must not be blank" }. */
  errors?: Record<string, string>;
}

export function isProblemDetail(value: unknown): value is ProblemDetail {
  return typeof value === 'object' && value !== null && typeof (value as ProblemDetail).status === 'number';
}
