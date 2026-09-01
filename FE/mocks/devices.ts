import type { Robot, Umi } from '@/types';

export const initialRobots: Robot[] = [
  { id: 'RB-07', name: '로봇팔 07', location: '실습실 A', status: '온라인' },
  { id: 'RB-12', name: '로봇팔 12', location: '실습실 B', status: '작업 중' },
  { id: 'RB-18', name: '로봇팔 18', location: '테스트실', status: '오프라인' },
  { id: 'RB-21', name: '로봇팔 21', location: '실습실 C', status: '오류' },
];

export const initialUmis: Umi[] = [
  { id: 'UMI-024', place: '실습실 A', status: '온라인', collected: '오늘 10:42', data: 18 },
  { id: 'UMI-018', place: '실습실 B', status: '온라인', collected: '어제 15:08', data: 11 },
  { id: 'UMI-031', place: '테스트실', status: '오프라인', collected: '3일 전', data: 7 },
];
