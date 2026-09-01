import type { WorkflowBlock } from '@/types';
import { initialSavedWorks } from '@/mocks';
import { clone, delay } from './client';

export interface SavedWork {
  name: string;
  robotId: string;
  blocks: WorkflowBlock[];
}

let savedWorks: SavedWork[] = clone(initialSavedWorks);

export async function fetchSavedWorks(): Promise<SavedWork[]> {
  await delay();
  return clone(savedWorks);
}

/** 작업 저장. 같은 이름이 있으면 덮어쓴다. */
export async function saveWork(work: SavedWork): Promise<SavedWork[]> {
  await delay();
  savedWorks = [clone(work), ...savedWorks.filter((item) => item.name !== work.name)];
  return clone(savedWorks);
}
