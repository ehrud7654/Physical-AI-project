import type { LoginResponse } from '@/types';
import { emailValidationError, normalizeEmail } from '../lib/authValidation';
import { apiRequest, clearAccessToken, setAccessToken } from './client';

const AUTH_SESSION_KEY = 'ssafy:auth-session';

export async function checkEmailAvailability(email: string, signal?: AbortSignal): Promise<{ email: string; available: boolean }> {
  const error = emailValidationError(email);
  if (error) throw new Error(error);
  return apiRequest(`/auth/email-availability?email=${encodeURIComponent(normalizeEmail(email))}`, { signal, cache: 'no-store' });
}

export async function signup(name: string, email: string, password: string): Promise<LoginResponse['user']> {
  const error = emailValidationError(email);
  if (error) throw new Error(error);
  return apiRequest('/auth/signup', {
    method: 'POST',
    body: JSON.stringify({ name: name.trim(), email: normalizeEmail(email), password }),
  });
}

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

export function clearAuthentication(): void {
  clearAccessToken();
  removeStoredSession();
}

export async function logout(): Promise<void> {
  await apiRequest<void>('/auth/logout', { method: 'POST' });
  clearAuthentication();
}

