import type { Dataset } from '@/types';
import { initialDatasets } from '@/mocks';
import { clone, delay } from './client';

/** 장치별 데이터 목록을 조회한다. 실제 API 연결 시 이 함수의 구현만 교체한다. */
export async function fetchDatasets(): Promise<Dataset[]> {
  await delay();
  return clone(initialDatasets);
}

