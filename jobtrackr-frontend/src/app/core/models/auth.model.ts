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

/** GET /me (UserResponse). */
export interface User {
  id: number;
  email: string;
  fullName: string | null;
  createdAt: IsoInstant;
}
