/**
 * Mock 전송 계층 헬퍼.
 * 실제 백엔드 연동 시 이 파일의 delay 기반 구현을 fetch 호출로 교체한다.
 */

/** 네트워크 지연을 흉내 낸다. */
export function delay(ms = 350): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 원본 Mock 배열을 오염시키지 않도록 깊은 복사해 반환한다. */
export function clone<T>(value: T): T {
  return structuredClone(value);
}
