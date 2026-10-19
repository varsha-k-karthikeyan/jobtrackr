import { IsoInstant } from './application.model';

/** Returned by both POST /auth/login (200) and POST /auth/register (201). */
export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Seconds until the token expires (900 = 15 minutes). */
  expiresIn: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string | null;
}

/** PUT /me/password. Answered with a fresh AuthResponse (and a new refresh cookie). */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** POST /auth/forgot-password. Always answered with 204, registered address or not. */
export interface ForgotPasswordRequest {
  email: string;
}

/** POST /auth/reset-password. `token` comes from the emailed link. Answered with 204; then log in. */
export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

/** GET /me (UserResponse). */
export interface User {
  id: number;
  email: string;
  fullName: string | null;
  createdAt: IsoInstant;
  /** The switch for reminder emails (8:00 digest, interview starting soon). */
  emailRemindersEnabled: boolean;
}

/** PUT /me/email-reminders. Answered with the updated User. */
export interface EmailRemindersRequest {
  enabled: boolean;
}
