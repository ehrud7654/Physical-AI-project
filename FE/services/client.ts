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
