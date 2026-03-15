import { getToken, setToken, removeToken, isLoggedIn } from '../../src/utils/auth';

beforeEach(() => {
  localStorage.clear();
});

describe('auth utils', () => {
  it('getToken returns null when no token', () => {
    expect(getToken()).toBeNull();
  });

  it('setToken stores token and getToken retrieves it', () => {
    setToken('abc');
    expect(getToken()).toBe('abc');
  });

  it('removeToken clears the stored token', () => {
    setToken('abc');
    removeToken();
    expect(getToken()).toBeNull();
  });

  it('isLoggedIn returns false when no token', () => {
    expect(isLoggedIn()).toBe(false);
  });

  it('isLoggedIn returns true when token exists', () => {
    setToken('abc');
    expect(isLoggedIn()).toBe(true);
  });
});
