// Persists the access token across reloads. Every localStorage access is wrapped in try/catch because
// storage can be unavailable (privacy modes, blocked site data) and must never break the app.
//
// Trade-off worth knowing: localStorage is readable by any script on the page, so an XSS bug could steal
// the token. An httpOnly cookie is immune to that but brings CSRF protection and cross-origin cookie setup
// into play. With a 15-minute token, Angular's built-in template sanitisation and no third-party scripts,
// localStorage is an acceptable choice for this app.

const TOKEN_KEY = 'jobtrackr.accessToken';

export function readStoredToken(): string | null {
  try {
    const token = localStorage.getItem(TOKEN_KEY);
    // Drop a token that has already expired, so a reload never shows a signed-in UI that will 401
    return token && !isExpired(token) ? token : null;
  } catch {
    return null;
  }
}

export function storeToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // Not fatal: the session simply won't survive a reload
  }
}

export function clearStoredToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nothing to do
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
