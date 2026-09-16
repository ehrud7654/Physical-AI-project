import type { LoginResponse } from '@/types';
import { apiRequest, clearAccessToken, setAccessToken } from './client';

const AUTH_SESSION_KEY = 'ssafy:auth-session';

interface StoredAuthSession {
  accessToken: string;
  expiresAt: number;
  user: LoginResponse['user'];
}

function removeStoredSession(): void {
  if (typeof window !== 'undefined') localStorage.removeItem(AUTH_SESSION_KEY);
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const result = await apiRequest<LoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
  setAccessToken(result.accessToken);
  const session: StoredAuthSession = {
    accessToken: result.accessToken,
    expiresAt: Date.now() + result.expiresIn * 1000,
    user: result.user,
  };
  localStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(session));
  return result;
}

export function restoreAuthentication(): LoginResponse['user'] | null {
  if (typeof window === 'undefined') return null;

  try {
    const rawSession = localStorage.getItem(AUTH_SESSION_KEY);
    if (!rawSession) return null;

    const session = JSON.parse(rawSession) as Partial<StoredAuthSession>;
    if (
      typeof session.accessToken !== 'string'
      || typeof session.expiresAt !== 'number'
      || !session.user
      || session.expiresAt <= Date.now()
    ) {
      removeStoredSession();
      return null;
    }

    setAccessToken(session.accessToken);
    return session.user;
  } catch {
    removeStoredSession();
    return null;
  }
}

export function logout(): void {
  clearAccessToken();
  removeStoredSession();
}

