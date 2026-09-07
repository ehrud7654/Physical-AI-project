import type { Dataset } from '@/types';

export const initialDatasets: Dataset[] = [
  { id: 1, skillKey: 'pick_place', umiId: 'UMI-024', ready: true, status: 'READY', retentionDays: 12, date: '2026. 08. 27  10:42', duration: '02:14', size: '486 MB', tone: 'orange' },
  { id: 2, skillKey: 'pick_place', umiId: 'UMI-018', ready: true, status: 'READY', retentionDays: 12, date: '2026. 08. 27  10:18', duration: '01:48', size: '392 MB', tone: 'blue' },
  { id: 3, skillKey: 'sort_two', umiId: 'UMI-024', ready: false, status: 'VALIDATING', retentionDays: 12, date: '2026. 08. 26  16:05', duration: '03:02', size: '651 MB', tone: 'green' },
  { id: 4, skillKey: 'align_fixture', umiId: 'UMI-018', ready: false, status: 'INVALID', validationErrors: ['영상과 센서 데이터의 시작 시간이 일치하지 않아요.', '동작을 다시 보여주고 데이터를 재수집해 주세요.'], retentionDays: 3, date: '2026. 08. 26  15:44', duration: '01:35', size: '344 MB', tone: 'purple' },
  { id: 5, skillKey: 'present_inspect', umiId: 'UMI-024', ready: false, status: 'UPLOADING', retentionDays: 14, date: '2026. 08. 27  11:06', duration: '01:12', size: '271 MB', tone: 'blue' },
];
