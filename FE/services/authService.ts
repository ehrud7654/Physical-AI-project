import type { LoginResponse } from '@/types';
import { apiRequest, clearAccessToken, setAccessToken } from './client';

export async function login(email: string, password: string): Promise<LoginResponse> {
  const result = await apiRequest<LoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
  setAccessToken(result.accessToken);
  return result;
}

export function logout(): void {
  clearAccessToken();
}

