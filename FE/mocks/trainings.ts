import type { Training } from '@/types';

export const initialTrainings: Training[] = [
  { name: '빨간 블록 옮기기', robot: 'RB-07', progress: 68, stage: '동작 패턴 학습', status: '진행 중', started: '오늘 10:42' },
  { name: '부품 상자 적재', robot: 'RB-12', progress: 100, stage: '모델 검증 완료', status: '완료', started: '어제 16:20' },
  { name: '컵 분류하기', robot: 'RB-21', progress: 34, stage: '연결 오류', status: '실패', started: '오늘 09:18' },
];
