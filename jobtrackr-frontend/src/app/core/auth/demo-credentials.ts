import { LoginRequest } from '../models/auth.model';

/**
 * The public demo account created by the backend migration V4__demo_user_data.sql.
 * NOT a secret: these credentials are published in the README on purpose, and the account only
 * exists in each developer's own local database.
 */
export const DEMO_CREDENTIALS: LoginRequest = {
  email: 'demo@jobtrackr.dev',
  password: 'Demo@1234',
};
