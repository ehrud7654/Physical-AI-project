const MOCK_AUTH_KEY = 'ssafy-mock-authenticated';

export function hasMockSession(): boolean {
  if (typeof window === 'undefined') return false;
  return window.sessionStorage.getItem(MOCK_AUTH_KEY) === 'true';
}

export function startMockSession(): void {
  window.sessionStorage.setItem(MOCK_AUTH_KEY, 'true');
}

export function clearMockSession(): void {
  window.sessionStorage.removeItem(MOCK_AUTH_KEY);
}

