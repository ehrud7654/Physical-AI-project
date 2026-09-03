import type { SavedWork } from '@/types';
import { initialSavedWorks } from '@/mocks';
import { clone, delay } from './client';

let savedWorks: SavedWork[] = clone(initialSavedWorks);

export async function fetchSavedWorks(): Promise<SavedWork[]> {
  await delay();
  return clone(savedWorks);
}

/** 작업 저장. 같은 ID가 있으면 수정본으로 교체한다. */
export async function saveWork(work: SavedWork): Promise<SavedWork[]> {
  await delay();
  savedWorks = [clone(work), ...savedWorks.filter((item) => item.id !== work.id)];
  return clone(savedWorks);
}
