import type { NavigateFunction } from 'react-router-dom';

const TOKEN_KEY = 'token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export function isLoggedIn(): boolean {
  return !!getToken();
}

export function requireAuth(navigate: NavigateFunction): boolean {
  if (!isLoggedIn()) {
    const target = encodeURIComponent(window.location.pathname + window.location.search);
    navigate(`/login?target=${target}`, { replace: true });
    return false;
  }
  return true;
}
