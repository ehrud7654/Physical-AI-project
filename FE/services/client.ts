/**
 * Mock 전송 계층 헬퍼.
 * 실제 백엔드 연동 시 이 파일의 delay 기반 구현을 fetch 호출로 교체한다.
 */

/** 네트워크 지연을 흉내 낸다. URL에 ?mockError 가 있으면 통신 실패를 흉내 낸다. */
export async function delay(ms = 350): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms));
  if (typeof window !== 'undefined' && new URLSearchParams(window.location.search).has('mockError')) {
    throw new Error('서버와 통신하지 못했어요.');
  }
}

/** 원본 Mock 배열을 오염시키지 않도록 깊은 복사해 반환한다. */
export function clone<T>(value: T): T {
  return structuredClone(value);
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';
let accessToken: string | null = null;

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string) {
    super(message);
  }
}

export function setAccessToken(token: string): void {
  accessToken = token;
}

export function clearAccessToken(): void {
  accessToken = null;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  const body = await response.json().catch(() => null) as { data?: T; code?: string; message?: string } | null;

  if (!response.ok) {
    throw new ApiError(
      response.status,
      body?.code ?? 'API_ERROR',
      body?.message ?? '서버 요청을 처리하지 못했어요.',
    );
  }
  if (response.status === 204) return undefined as T;
  if (!body || body.data === undefined) throw new ApiError(response.status, 'INVALID_RESPONSE', '서버 응답 형식이 올바르지 않아요.');
  return body.data;
}
