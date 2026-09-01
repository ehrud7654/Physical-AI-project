import type { DeviceType, Robot, Umi } from '@/types';
import { initialRobots, initialUmis } from '@/mocks';
import { clone, delay } from './client';

/** Mock 저장소 — 백엔드 연동 시 아래 함수 본문만 fetch로 교체한다. */
let robots: Robot[] = clone(initialRobots);
let umis: Umi[] = clone(initialUmis);

export interface RegisterDeviceInput {
  type: DeviceType;
  id: string;
  name: string;
  location: string;
}

export async function fetchRobots(): Promise<Robot[]> {
  await delay();
  return clone(robots);
}

export async function fetchUmis(): Promise<Umi[]> {
  await delay();
  return clone(umis);
}

/** 장치 등록. 중복 ID면 오류를 던진다. */
export async function registerDevice(input: RegisterDeviceInput): Promise<{ robots: Robot[]; umis: Umi[] }> {
  await delay();
  const id = input.id.trim();
  const duplicated = robots.some((robot) => robot.id === id) || umis.some((umi) => umi.id === id);
  if (duplicated) throw new Error(`이미 등록된 ID예요: ${id}`);

  if (input.type === 'robot') {
    robots = [...robots, { id, name: input.name.trim(), location: input.location.trim(), status: '온라인' }];
  } else {
    umis = [...umis, { id, place: input.location.trim(), status: '온라인', collected: '방금 전', data: 0 }];
  }
  return { robots: clone(robots), umis: clone(umis) };
}
