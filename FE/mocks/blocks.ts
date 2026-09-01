import type { BlockCatalogItem, WorkflowBlock } from '@/types';

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
export const initialSavedWorks: { name: string; robotId: string; blocks: WorkflowBlock[] }[] = [
  {
    name: '컵 정리 작업',
    robotId: 'RB-07',
    blocks: [
      { id: 11, kind: 'vlm', title: '물체를 상자로 이동' },
      { id: 12, kind: 'motor', title: '모터 움직이기', motor: 'J1', angle: -30 },
    ],
  },
  {
    name: '부품 상자 이동',
    robotId: 'RB-12',
    blocks: [
      { id: 13, kind: 'umi', title: '빨간 블록 집기' },
      { id: 14, kind: 'repeat', title: '반복하기', repeat: 3, children: [{ id: 15, kind: 'vlm', title: '물체를 상자로 이동' }] },
    ],
  },
];
