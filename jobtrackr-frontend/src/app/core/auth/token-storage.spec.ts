import { makeJwt } from '../testing/test-data';
import { clearStoredToken, hasSessionHint, readStoredToken, storeToken, TOKEN_KEY } from './token-storage';

describe('token storage', () => {
  beforeEach(() => localStorage.clear());

  it('stores the access token and sets the "a refresh cookie probably exists" hint', () => {
    const token = makeJwt(Date.now() + 15 * 60_000);

    storeToken(token);

    expect(readStoredToken()).toBe(token);
    expect(hasSessionHint()).toBe(true);
  });

  it('drops an expired token on read, so the app asks the refresh cookie instead', () => {
    localStorage.setItem(TOKEN_KEY, makeJwt(Date.now() - 1000));

    expect(readStoredToken()).toBeNull();
  });

  it('treats an unreadable token as absent instead of throwing', () => {
    localStorage.setItem(TOKEN_KEY, 'not-a-jwt');

    expect(readStoredToken()).toBeNull();
  });

  it('clearing removes both the token and the hint (logout)', () => {
    storeToken(makeJwt(Date.now() + 60_000));

    clearStoredToken();

    expect(readStoredToken()).toBeNull();
    expect(hasSessionHint()).toBe(false);
  });
});
