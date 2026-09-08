import type { Training, TrainingRequirements, TrainingStatus } from '@/types';
import { initialTrainings } from '@/mocks';
import { clone, delay } from './client';

let trainings: Training[] = clone(initialTrainings);

const requirementBySkill: Record<string, Pick<TrainingRequirements, 'minimumEpisodeCount' | 'recommendedEpisodeCount' | 'maximumEpisodeCount'>> = {
  pick_place: { minimumEpisodeCount: 5, recommendedEpisodeCount: 10, maximumEpisodeCount: 100 },
  sort_two: { minimumEpisodeCount: 8, recommendedEpisodeCount: 15, maximumEpisodeCount: 100 },
  align_fixture: { minimumEpisodeCount: 8, recommendedEpisodeCount: 15, maximumEpisodeCount: 100 },
  present_inspect: { minimumEpisodeCount: 5, recommendedEpisodeCount: 10, maximumEpisodeCount: 100 },
  line_up: { minimumEpisodeCount: 10, recommendedEpisodeCount: 20, maximumEpisodeCount: 100 },
};

/** SKILL-09: 프리셋이 아닌 학습 입력 조건을 조회한다. */
export async function fetchTrainingRequirements(skillKey: string): Promise<TrainingRequirements> {
  await delay();
  const counts = requirementBySkill[skillKey] ?? requirementBySkill.pick_place;
  return { skillId: `skill_${skillKey}`, skillKey, ...counts, allowedSources: ['REAL'], requiresSameUmiDevice: false, requiredContractVersion: 'umi-policy-v1', supported: true, requirementsVersion: 1 };
}

export async function fetchTrainings(status?: TrainingStatus | 'all'): Promise<Training[]> {
  await delay();
  try {
    const stored = JSON.parse(localStorage.getItem('ssafy-trainings-v1') || 'null');
    if (Array.isArray(stored)) trainings = stored;
  } catch { /* Use initial mock data if storage is unavailable. */ }
  if (!status || status === 'all') return clone(trainings);
  return clone(trainings.filter((item) => item.status === status));
}

/** TRAIN-07: 실행 중인 학습은 취소 요청으로 표시하고 서버 확인 후에만 취소 완료로 바뀐다. */
export async function cancelTraining(name: string): Promise<Training[]> {
  await delay();
  trainings = trainings.map((item) =>
    item.name === name && item.status === '진행 중'
      ? { ...item, status: '취소 요청' as const, stage: '취소 확인 중' }
      : item,
  );
  window.setTimeout(() => {
    trainings = trainings.map((item) => item.name === name && item.status === '취소 요청' ? { ...item, status: '취소됨' as const, stage: '취소 완료' } : item);
  }, 1200);
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
  try { localStorage.setItem('ssafy-trainings-v1', JSON.stringify(trainings)); } catch { /* Keep the in-memory result. */ }
  return clone(trainings);
}
