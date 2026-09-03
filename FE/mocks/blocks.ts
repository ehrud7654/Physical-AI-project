import type { BlockCatalogItem, LearnedSkill, SavedWork, WorkflowBlock } from '@/types';

export const blockCatalog: BlockCatalogItem[] = [
  { kind: 'umi', title: '빨간 블록 집기', description: 'UMI 체크포인트', mark: 'U' },
  { kind: 'vlm', title: '물체를 상자로 이동', description: 'VLM 사전 학습 작업', mark: 'V' },
  { kind: 'motor', title: '모터 움직이기', description: '모터 번호와 각도 설정', mark: 'M' },
  { kind: 'repeat', title: '반복하기', description: '안쪽 동작을 여러 번 실행', mark: '↻' },
];

export const motorRanges: Record<string, [number, number]> = {
  J1: [-180, 180], J2: [-90, 90], J3: [-120, 120], J4: [-180, 180], J5: [-110, 110], J6: [-180, 180],
};

export const initialWorkflowBlocks: WorkflowBlock[] = [
  { id: 1, kind: 'umi', title: '빨간 블록 집기' },
  { id: 2, kind: 'motor', title: '모터 움직이기', motor: 'J2', angle: 35 },
  { id: 3, kind: 'repeat', title: '반복하기', repeat: 2, children: [{ id: 4, kind: 'vlm', title: '물체를 상자로 이동' }] },
];

/** 저장된 작업 흐름 Mock — '작업 불러오기' 셀렉트에서 사용 */
export const initialSavedWorks: SavedWork[] = [
  {
    id: 'WF-001',
    name: '컵 정리 작업',
    robotId: 'RB-07',
    estimatedSeconds: 80,
    updatedAt: '2026-09-03T10:24:00+09:00',
    blocks: [
      { id: 11, kind: 'vlm', title: '물체를 상자로 이동' },
      { id: 12, kind: 'motor', title: '모터 움직이기', motor: 'J1', angle: -30 },
    ],
  },
  {
    id: 'WF-002',
    name: '부품 상자 이동',
    robotId: 'RB-12',
    estimatedSeconds: 145,
    updatedAt: '2026-09-02T16:08:00+09:00',
    blocks: [
      { id: 13, kind: 'umi', title: '빨간 블록 집기' },
      { id: 14, kind: 'repeat', title: '반복하기', repeat: 3, children: [{ id: 15, kind: 'vlm', title: '물체를 상자로 이동' }] },
    ],
  },
  {
    id: 'WF-003',
    name: '빨간 블록 분류',
    robotId: 'RB-07',
    estimatedSeconds: 110,
    updatedAt: '2026-08-31T14:42:00+09:00',
    blocks: [
      { id: 16, kind: 'umi', title: '빨간 블록 집기' },
      { id: 17, kind: 'vlm', title: '물체를 상자로 이동' },
      { id: 18, kind: 'motor', title: '모터 움직이기', motor: 'J3', angle: 20 },
    ],
  },
  {
    id: 'WF-004',
    name: '검수 위치로 이동',
    robotId: 'RB-18',
    estimatedSeconds: 45,
    updatedAt: '2026-08-28T09:17:00+09:00',
    blocks: [
      { id: 19, kind: 'motor', title: '모터 움직이기', motor: 'J1', angle: 60 },
    ],
  },
];

/** WORKFLOW에 처음 표시할 학습 완료 Skill */
export const initialLearnedSkills: LearnedSkill[] = [
  { id: 'SKILL-001', name: '빨간 블록 집기', kind: 'umi', description: 'UMI 데이터로 학습된 동작', mark: 'S' },
  { id: 'SKILL-002', name: '물체를 상자로 이동', kind: 'vlm', description: '사전 학습된 이동 동작', mark: 'S' },
];
