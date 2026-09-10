import type { Training } from '@/types';

export const initialTrainings: Training[] = [
  { id: 'mock-training-1', skillKey: 'pick_place', umiDeviceId: 'mock-umi-1', inputMode: 'COLLECT_NEW', expectedEpisodeCount: 10, name: '빨간 블록 옮기기', robot: 'RB-07', progress: 68, stage: '동작 패턴 학습', status: '진행 중', started: '오늘 10:42' },
  { id: 'mock-training-2', skillKey: 'line_up', umiDeviceId: 'mock-umi-2', inputMode: 'EXISTING_EPISODES', expectedEpisodeCount: 10, name: '부품 상자 적재', robot: 'RB-12', progress: 100, stage: '모델 검증 완료', status: '완료', started: '어제 16:20' },
  { id: 'mock-training-3', skillKey: 'sort_two', umiDeviceId: 'mock-umi-3', inputMode: 'COLLECT_NEW', expectedEpisodeCount: 10, name: '컵 분류하기', robot: 'RB-21', progress: 34, stage: '연결 오류', status: '실패', started: '오늘 09:18' },
];
