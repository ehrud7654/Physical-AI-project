import type { Training, TrainingStatus } from '@/types';
import { initialTrainings } from '@/mocks';
import { clone, delay } from './client';

let trainings: Training[] = clone(initialTrainings);

export async function fetchTrainings(status?: TrainingStatus | 'all'): Promise<Training[]> {
  await delay();
  if (!status || status === 'all') return clone(trainings);
  return clone(trainings.filter((item) => item.status === status));
}

/** 진행 중인 학습을 취소한다. 실패 상태로 전환된다. */
export async function cancelTraining(name: string): Promise<Training[]> {
  await delay();
  trainings = trainings.map((item) =>
    item.name === name && item.status === '진행 중'
      ? { ...item, status: '실패' as const, stage: '사용자 취소' }
      : item,
  );
  return clone(trainings);
}

/** 실패한 학습을 재시도한다. 진행 중 상태로 되돌아간다. */
export async function retryTraining(name: string): Promise<Training[]> {
  await delay();
  trainings = trainings.map((item) =>
    item.name === name && item.status === '실패'
      ? { ...item, status: '진행 중' as const, stage: '데이터 전처리', progress: 5, started: '방금 전' }
      : item,
  );
  return clone(trainings);
}

/** 새 학습을 등록한다(새 학습 완료 시 목록 반영용). */
export async function addTraining(input: { name: string; robot: string }): Promise<Training[]> {
  await delay();
  trainings = [
    { name: input.name, robot: input.robot, progress: 100, stage: '모델 검증 완료', status: '완료', started: '방금 전' },
    ...trainings.filter((item) => item.name !== input.name),
  ];
  return clone(trainings);
}
