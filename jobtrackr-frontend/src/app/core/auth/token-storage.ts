// Persists the access token across reloads. Every localStorage access is wrapped in try/catch because
// storage can be unavailable (privacy modes, blocked site data) and must never break the app.
//
// Only the short-lived ACCESS token is stored here (15 minutes). The long-lived refresh token lives in an
// httpOnly cookie that JavaScript cannot read at all, so an XSS bug can steal at most a token that expires
// within minutes, never the session itself.

export const TOKEN_KEY = 'jobtrackr.accessToken';

/**
 * "A refresh cookie probably exists." JavaScript cannot see the httpOnly cookie, so this flag decides whether
 * a silent refresh is worth trying (after a reload with an expired access token, or on the login page).
 * Without it, every visit to the login page would fire a refresh request that can only fail.
 */
const SESSION_HINT_KEY = 'jobtrackr.hasSession';

export function readStoredToken(): string | null {
  try {
    const token = localStorage.getItem(TOKEN_KEY);
    // Drop a token that has already expired: the refresh cookie (if any) will provide a new one
    return token && !isExpired(token) ? token : null;
  } catch {
    return null;
  }
}

export function storeToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(SESSION_HINT_KEY, '1');
  } catch {
    // Not fatal: the session simply won't survive a reload
  }
}

export function clearStoredToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(SESSION_HINT_KEY);
  } catch {
    // Nothing to do
  }
}

export function hasSessionHint(): boolean {
  try {
    return localStorage.getItem(SESSION_HINT_KEY) === '1';
  } catch {
    return false;
  }
}

/**
 * Reads the `exp` claim (seconds since 1970). Only a convenience for the UI: the backend verifies the
 * signature and expiry on every request, so a tampered token gains nothing here.
 */
function isExpired(token: string): boolean {
  try {
    const payloadPart = token.split('.')[1];
    const json = atob(payloadPart.replace(/-/g, '+').replace(/_/g, '/'));
    const payload: unknown = JSON.parse(json);
    const exp = (payload as { exp?: unknown }).exp;
    return typeof exp === 'number' && exp * 1000 <= Date.now();
  } catch {
    return true; // malformed token: treat as unusable
  }
}
