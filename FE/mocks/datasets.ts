import type { Dataset } from '@/types';

export const initialDatasets: Dataset[] = [
  { id: 1, skillKey: 'pick_place', umiId: 'UMI-024', ready: true, retentionDays: 12, date: '2026. 08. 27  10:42', duration: '02:14', size: '486 MB', tone: 'orange' },
  { id: 2, skillKey: 'pick_place', umiId: 'UMI-018', ready: true, retentionDays: 12, date: '2026. 08. 27  10:18', duration: '01:48', size: '392 MB', tone: 'blue' },
  { id: 3, skillKey: 'sort_two', umiId: 'UMI-024', ready: true, retentionDays: 12, date: '2026. 08. 26  16:05', duration: '03:02', size: '651 MB', tone: 'green' },
  { id: 4, skillKey: 'align_fixture', umiId: 'UMI-018', ready: false, retentionDays: 3, date: '2026. 08. 26  15:44', duration: '01:35', size: '344 MB', tone: 'purple' },
];
