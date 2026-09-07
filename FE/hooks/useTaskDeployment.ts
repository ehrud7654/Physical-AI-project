'use client';

import { useEffect, useState } from 'react';
import type { SavedWork } from '@/types';
import { delay } from '@/services/client';

interface Deployment { signature: string; robotId: string; version: number; }
const KEY = 'ssafy-task-deployments-v1';

/** Local mock deployment: a version fixes the exact saved block configuration. */
export function useTaskDeployment(work: SavedWork | undefined, robotId: string) {
  const signature = work ? JSON.stringify([work.id, work.name, work.blocks]) : '';
  const [record, setRecord] = useState<Deployment | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  /* eslint-disable react-hooks/set-state-in-effect -- hydrate the selected deployment */
  useEffect(() => {
    setError('');
    try {
      const records: Deployment[] = JSON.parse(localStorage.getItem(KEY) || '[]');
      setRecord(records.find(item => item.signature === signature && item.robotId === robotId) ?? null);
    } catch { setRecord(null); }
  }, [signature, robotId]);
  /* eslint-enable react-hooks/set-state-in-effect */
  const ready = !!record && record.signature === signature && record.robotId === robotId;
  const deploy = async () => {
    if (!work || pending) return;
    setPending(true);
    setError('');
    try {
      await delay(1000);
      const records: Deployment[] = JSON.parse(localStorage.getItem(KEY) || '[]');
      const previous = records.find(item => item.signature === signature);
      const next = { signature, robotId, version: previous?.version ?? Math.max(0, ...records.map(item => item.version)) + 1 };
      localStorage.setItem(KEY, JSON.stringify([...records.filter(item => !(item.signature === signature && item.robotId === robotId)), next]));
      setRecord(next);
    } catch { setError('배포하지 못했어요. 연결 상태를 확인하고 다시 시도해 주세요.'); }
    finally { setPending(false); }
  };
  return { ready, pending, error, version: ready ? record?.version : undefined, deploy };
}
